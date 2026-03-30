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
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Collects and displays message stream statistics from a log (Kafka topic or Chronicle Queue).
 *
 * <p>This is the log-specific stats command for the {@code pal log stats} pattern. It accepts a log
 * name or {@code file:}-prefixed Chronicle Queue path as a positional argument and aggregates
 * statistics. For Kafka logs, aggregation uses Kafka Streams; for Chronicle logs, messages are read
 * directly from the queue. Messages are filtered by type, peer UUID, or thread name, and counters
 * are continuously printed in plain text or JSON format.
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
    value = {
      "EI_EXPOSE_REP2",
      "SIC_INNER_SHOULD_BE_STATIC_ANON",
      "URF_UNREAD_FIELD",
      "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"
    },
    justification =
        "CLI stats command - shared state for thread operations; format strings intentional")
public class LogStats extends AbstractStatsCommand {

  /** Logger for logging information and errors. */
  private final Logger logger = LoggerFactory.getLogger(LogStats.class);

  /**
   * Latch used to coordinate shutdown signal handling for Kafka streams.
   *
   * <p>Package-private for test access.
   */
  final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /**
   * Kafka streams instance for log-based message processing.
   *
   * <p>Package-private for test access.
   */
  KafkaStreams kafkaStreams;

  /** Handles continuous printing of aggregated statistics. */
  ContinuousPrinter continuousPrinter;

  /** Indicates whether statistics are being printed externally. */
  private boolean externalPrinting = false;

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
   * Constructs a LogStats instance for log-based streams, intended for use from another class
   * rather than the command line (e.g., from a Web/GUI application).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
   */
  public LogStats(String bootstrapServers, String logName) {
    this(bootstrapServers, logName, null, null, null);
  }

  /**
   * Constructs a LogStats instance for log-based streams with additional filtering options.
   * Intended for use from another class rather than the command line.
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
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
    this.externalPrinting = true;
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
   * Processes and aggregates statistics from Kafka message streams.
   *
   * <p>Sets up Kafka Streams with the specified configurations, applies necessary filters based on
   * message types and peer UUID, and updates the counters accordingly. This method initializes the
   * stream processing topology and starts the Kafka Streams application.
   *
   * @return exit code indicating success (0) or failure (1)
   */
  private int runKafkaStats() {
    String kafkaServersToUse = bootstrapServers != null ? bootstrapServers : getKafkaServers();
    Objects.requireNonNull(kafkaServersToUse, "Kafka bootstrap servers required for Kafka logs");

    Properties props = new Properties();
    String consumerId = "printer-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s%n",
          logName, kafkaServersToUse, consumerId);
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServersToUse);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    final StreamsBuilder builder = new StreamsBuilder();

    KStream<String, Message> stream =
        builder.<String, byte[]>stream(logName)
            .map(
                (k, v) -> {
                  Message message = new Message();
                  message.unmarshal(v, 0);
                  return new KeyValue<>(k, message);
                });

    if (msgTypes != null) {
      stream =
          stream.filter(
              (k, m) -> {
                ExecMessage execMessage = m.getExecMessage();
                MessageType messageType = MessageType.fromId(m.getMessageType());
                return execMessage != null && msgTypes.contains(messageType.name());
              });
    }

    if (fromPeer != null) {
      stream = stream.filter((k, m) -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
    }

    stream.foreach((key, message) -> updateCounters(message));

    final Topology topology = builder.build();
    if (verbose) {
      System.out.println(topology.describe());
    }

    kafkaStreams = new KafkaStreams(topology, props);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                performKafkaShutdown();
              }
            });

    startStreams(kafkaStreams, getCounters());
    try {
      shutdownLatch.await();
    } catch (Throwable e) {
      logger.error("Uncaught error processing stream", e);
      return 1;
    }
    return 0;
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

  /**
   * Performs shutdown for Kafka-based message streaming.
   *
   * <p>This method closes the Kafka streams, signals the continuous printer to stop, and counts
   * down the shutdown latch.
   *
   * <p>Package-private for test access.
   */
  void performKafkaShutdown() {
    kafkaStreams.close();
    if (continuousPrinter != null) {
      continuousPrinter.setDone(true);
    }
    shutdownLatch.countDown();
  }

  /**
   * Stops the ongoing stream processing by triggering a shutdown.
   *
   * <p>Signals the stream processing threads to terminate gracefully.
   */
  @SuppressWarnings("unused")
  public void stopStreams() {
    shutdownLatch.countDown();
  }

  /**
   * Initializes and starts the Kafka Streams processing.
   *
   * <p>Sets up state listeners and exception handlers, starts the Kafka Streams instance, and
   * optionally starts the continuous statistics printer if external printing is not enabled.
   *
   * @param streams the {@link KafkaStreams} instance to start
   * @param counters the {@link Counters} instance to update with stream data
   */
  private void startStreams(KafkaStreams streams, Counters counters) {
    CompletableFuture<KafkaStreams.State> stateFuture = new CompletableFuture<>();
    streams.setStateListener(
        (newState, oldState) -> {
          if (stateFuture.isDone()) {
            return;
          }

          if (newState == KafkaStreams.State.RUNNING || newState == KafkaStreams.State.ERROR) {
            stateFuture.complete(newState);
          }
        });

    streams.setUncaughtExceptionHandler(
        throwable -> {
          logger.error("Uncaught exception in stream. Will shutdown client.", throwable);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

    streams.start();
    try {
      KafkaStreams.State finalState = stateFuture.get();
      if (finalState == KafkaStreams.State.RUNNING) {
        if (!externalPrinting) {
          this.continuousPrinter = new ContinuousPrinter(counters, jsonOutput);
          new Thread(continuousPrinter).start();
        }
      }
    } catch (InterruptedException | ExecutionException ex) {
      logger.error("Error waiting for Kafka Streams to reach running state", ex);
    }
  }
}
