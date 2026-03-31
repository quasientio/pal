/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.tools.stats.ContinuousPrinter;
import io.quasient.pal.tools.stats.Counters;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Collects and displays message statistics from a log (Kafka topic or Chronicle Queue).
 *
 * <p>This is the log-specific stats command for the {@code pal log stats} pattern. It accepts a log
 * name or {@code file:}-prefixed Chronicle Queue path as a positional argument and aggregates
 * statistics. For Kafka logs, all messages are consumed from the topic; for Chronicle logs,
 * messages are read directly from the queue. Messages are filtered by type, peer UUID, or thread
 * name, and the final counters are printed in plain text or JSON format.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal log stats my-log-topic -k localhost:9092
 *   pal log stats my-log-topic -k localhost:9092 -t EXEC_INSTANCE_METHOD -j
 *   pal log stats file:/tmp/my-wal
 *   pal log stats file:/tmp/my-wal -t EXEC_CONSTRUCTOR -j
 * </pre>
 *
 * @see Counters
 * @see ContinuousPrinter
 * @see LogResolver
 */
@Command(name = "stats", description = "Show log message statistics")
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2", "URF_UNREAD_FIELD", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"},
    justification =
        "CLI stats command - shared state for thread operations; format strings intentional")
public class LogStats extends AbstractStatsCommand {

  /** Logger for logging information and errors. */
  private final Logger logger = LoggerFactory.getLogger(LogStats.class);

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand palCommand;

  /** Positional log identifier: a Kafka topic name or {@code file:}-prefixed Chronicle path. */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "LOG",
      description = "log name or file: path")
  private String logName;

  /**
   * Kafka bootstrap server addresses.
   *
   * <p>Specifies the Kafka bootstrap servers to connect to. Required for Kafka logs; ignored for
   * Chronicle logs.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "BOOTSTRAP_SERVERS",
      description = "kafka bootstrap servers (required for Kafka logs)")
  private String bootstrapServers;

  /**
   * Types of messages to filter by.
   *
   * <p>Specifies one or more message types to include in the statistics.
   */
  @Option(
      names = {"-t", "--types"},
      arity = "0..*",
      description =
          "type(s) of messages to filter by ("
              + "STATIC_CONSTRUCTOR, RETURN_CLASS, CONSTRUCTOR, INSTANCE_METHOD,"
              + " CLASS_METHOD, GET_STATIC, GET_FIELD, PUT_STATIC, PUT_FIELD,"
              + " PUT_STATIC_DONE, PUT_FIELD_DONE, THROWABLE, RETURN_VALUE)")
  private List<String> msgTypes;

  /**
   * Peer UUID to filter messages by.
   *
   * <p>Includes only messages originating from the specified peer UUID.
   */
  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "PEER_UUID",
      description = "filter by peer uuid")
  private String fromPeer;

  /**
   * Thread name to filter messages by.
   *
   * <p>Includes only messages originating from the specified thread name.
   */
  @SuppressWarnings("unused")
  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "THREAD_NAME",
      description = "filter by thread name")
  private String threadName;

  /**
   * Flag to enable JSON output for statistics.
   *
   * <p>If set, the aggregated statistics will be printed in JSON format.
   */
  @Option(
      names = {"-j", "--json-output"},
      description = "print stats as JSON")
  private boolean jsonOutput;

  /** Flag to enable verbose output. */
  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  /**
   * Indicates whether help was requested.
   *
   * <p>If set, displays the help message and exits.
   */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /**
   * Constructs a LogStats instance for log-based stats, intended for use from another class rather
   * than the command line (e.g., from a Web/GUI application).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name or {@code file:}-prefixed Chronicle path
   */
  public LogStats(String bootstrapServers, String logName) {
    this(bootstrapServers, logName, null, null, null);
  }

  /**
   * Constructs a LogStats instance for log-based stats with additional filtering options. Intended
   * for use from another class rather than the command line.
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name or {@code file:}-prefixed Chronicle path
   * @param msgTypes message types to filter by
   * @param fromPeer peer UUID to filter by
   * @param threadName thread name to filter by
   */
  public LogStats(
      String bootstrapServers,
      String logName,
      List<String> msgTypes,
      String fromPeer,
      String threadName) {
    this.bootstrapServers = bootstrapServers;
    this.logName = logName;
    this.msgTypes = msgTypes;
    this.fromPeer = fromPeer;
    this.threadName = threadName;
  }

  /** Default constructor for running as a Picocli command (i.e., from the command line). */
  LogStats() {}

  /**
   * Validates the input provided to the command.
   *
   * @throws RuntimeException if the log name is not provided
   */
  @Override
  protected void validateInput() {
    if (logName == null || logName.isEmpty()) {
      throw new RuntimeException("Log name is required. Usage: pal log stats <LOG_NAME>");
    }
  }

  /**
   * Performs initialization steps required before running the command.
   *
   * <p>Initializes the directory connection provider if a PAL directory connection string is
   * available from the parent command.
   */
  @Override
  protected void initialize() {
    if (palCommand != null && palCommand.getPalDirectoryConnectionString() != null) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    }
  }

  /**
   * Routes to the appropriate stats method based on the resolved log type.
   *
   * <p>Uses {@link LogResolver} to resolve the log identifier to a {@link LogInfo}, then delegates
   * to either {@link #runKafkaStats()} or {@link #runChronicleStats(LogInfo)}.
   *
   * @return exit code indicating success (0) or failure (1)
   */
  @Override
  protected int runCommand() {
    String kafkaServersToUse = bootstrapServers != null ? bootstrapServers : getKafkaServers();
    LogResolver logResolver = new LogResolver(directoryConnectionProvider, kafkaServersToUse);
    LogInfo log = logResolver.resolveLogInfo(logName);

    if (log == null) {
      logger.error("No Log found for identifier: {}", logName);
      return 1;
    }

    if (log.getLogType() == LogType.CHRONICLE) {
      return runChronicleStats(log);
    } else {
      return runKafkaStats();
    }
  }

  /**
   * Reads all messages from a Kafka topic and aggregates statistics.
   *
   * <p>Creates a plain Kafka consumer that reads from the beginning of each partition up to the
   * current end offsets, applies filters, updates counters, and prints the final statistics.
   *
   * @return exit code indicating success (0) or failure (1)
   */
  private int runKafkaStats() {
    String kafkaServersToUse = bootstrapServers != null ? bootstrapServers : getKafkaServers();
    Objects.requireNonNull(kafkaServersToUse, "Kafka bootstrap servers required for Kafka logs");

    Properties props = new Properties();
    String groupId = "log-stats-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s group_id=%s%n",
          logName, kafkaServersToUse, groupId);
    }
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServersToUse);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
      List<PartitionInfo> partitionInfos = consumer.partitionsFor(logName);
      if (partitionInfos == null || partitionInfos.isEmpty()) {
        logger.error("No partitions found for topic: {}", logName);
        return 1;
      }

      List<TopicPartition> partitions =
          partitionInfos.stream()
              .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
              .toList();
      consumer.assign(partitions);

      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
      consumer.seekToBeginning(partitions);

      while (!reachedEnd(consumer, endOffsets)) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
        for (ConsumerRecord<String, byte[]> record : records) {
          Message message = new Message();
          message.unmarshal(record.value(), 0);

          // Apply type filter
          if (msgTypes != null) {
            ExecMessage execMessage = message.getExecMessage();
            MessageType messageType = MessageType.fromId(message.getMessageType());
            if (execMessage == null || !msgTypes.contains(messageType.name())) {
              continue;
            }
          }

          // Apply peer filter
          if (fromPeer != null && !fromPeer.equalsIgnoreCase(getPeerUuid(message))) {
            continue;
          }

          updateCounters(message);
        }
      }
    }

    printStats();
    return 0;
  }

  /**
   * Checks whether the consumer has reached the end offset for all assigned partitions.
   *
   * @param consumer the Kafka consumer to check position for
   * @param endOffsets the target end offsets per partition
   * @return {@code true} if the consumer position has reached the end offset for every partition
   */
  private static boolean reachedEnd(
      KafkaConsumer<?, ?> consumer, Map<TopicPartition, Long> endOffsets) {
    for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
      if (consumer.position(entry.getKey()) < entry.getValue()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Processes and aggregates statistics from a Chronicle Queue log.
   *
   * <p>Reads all messages from the Chronicle Queue, applies type and peer filters, and updates the
   * counters. After reading all messages, prints the final statistics once.
   *
   * @param log the resolved {@link LogInfo} for the Chronicle log
   * @return exit code indicating success (0) or failure (1)
   */
  private int runChronicleStats(LogInfo log) {
    Path queuePath = Path.of(log.getName());

    if (!ChronicleLogUtil.queueExists(queuePath)) {
      logger.error("Chronicle log does not exist at path: {}", queuePath);
      return 1;
    }

    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf("Chronicle queue: path=%s%n", queuePath);
    }

    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {

      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      while (true) {
        OutboundMsg outboundMsg = OutboundMsg.readNext(tailer);
        if (outboundMsg == null) {
          break;
        }

        Message message = new Message();
        message.unmarshal(outboundMsg.getBody(), 0);

        // Apply type filter
        if (msgTypes != null) {
          ExecMessage execMessage = message.getExecMessage();
          MessageType messageType = MessageType.fromId(message.getMessageType());
          if (execMessage == null || !msgTypes.contains(messageType.name())) {
            continue;
          }
        }

        // Apply peer filter
        if (fromPeer != null && !fromPeer.equalsIgnoreCase(getPeerUuid(message))) {
          continue;
        }

        updateCounters(message);
      }
    }

    // Print final statistics
    printStats();
    return 0;
  }

  /**
   * Prints the collected statistics to standard output.
   *
   * <p>Delegates to {@link ContinuousPrinter#printCounters(Counters, boolean,
   * com.google.gson.Gson)} for the shared formatting logic.
   */
  private void printStats() {
    ContinuousPrinter.printCounters(getCounters(), jsonOutput, null);
  }
}
