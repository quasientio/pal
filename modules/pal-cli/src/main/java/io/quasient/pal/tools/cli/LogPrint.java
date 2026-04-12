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
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Streams and prints messages from a log (Kafka topic or Chronicle Queue).
 *
 * <p>This is the log-specific print command for the {@code pal log print} pattern. It accepts a log
 * name or path as a positional argument and prints messages in various output formats with optional
 * filtering, following, and offset control.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal log print my-log
 *   pal log print my-log --full -o 42
 *   pal log print file:/tmp/wal --tree -f
 *   pal log print my-log -k localhost:29092 --types CONSTRUCTOR,INSTANCE_METHOD
 * </pre>
 *
 * @see AbstractPrintCommand
 * @see LogResolver
 */
@Command(name = "print", description = "Print messages from a log")
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD"},
    justification = "CLI command - picocli fields are read via reflection")
class LogPrint extends AbstractPrintCommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(LogPrint.class);

  /** Parent command that provides access to the main PalCommand context. */
  @ParentCommand PalCommand palCommand;

  /**
   * Positional log identifier argument.
   *
   * <p>Accepts a log name (Kafka topic), UUID, or {@code file:}-prefixed Chronicle Queue path.
   */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "LOG",
      description = "log name, UUID, or file: path")
  String logIdentifier;

  /**
   * Kafka bootstrap servers for direct access to Kafka logs without PAL_DIRECTORY.
   *
   * <p>When provided, allows reading Kafka logs directly without connecting to the PAL directory.
   * Takes precedence over the PAL_KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "host:port[,host:port...]",
      description = "Kafka bootstrap servers (for direct Kafka access without -d)")
  private String kafkaServers;

  /**
   * Offset from which to start printing messages.
   *
   * <p>If specified, prints the message at the given offset and then exits. When used, all other
   * filters are ignored. Combine with the --follow option to wait for the message if it is not yet
   * available.
   */
  @Option(
      names = {"-o", "--offset"},
      paramLabel = "offset",
      description = {
        "Print message with given offset",
        "If given, filters are ignored. Combine with --follow/-f to wait for the message"
      })
  Long offset;

  /** If set, the command will follow new messages as they arrive, similar to 'tail -f'. */
  @Option(
      names = {"-f", "--follow"},
      description = "Follow new messages (like 'tail -f')")
  boolean follow;

  /** Set by the shutdown hook when the JVM receives SIGTERM/SIGINT during follow mode. */
  private volatile boolean interrupted;

  /**
   * When used with --offset, also shows the corresponding return value or exception for the
   * operation at the specified offset.
   *
   * <p>Scans subsequent messages using nesting-depth tracking to find the matching return value.
   * Each operation increments depth; each return/done message decrements it. When depth reaches
   * zero, the matching return has been found.
   */
  @Option(
      names = {"--with-return"},
      description =
          "Show the return value for the operation at the given offset (use with --offset)")
  boolean withReturn;

  /** Constructs a new {@code LogPrint} instance. */
  LogPrint() {}

  /**
   * {@inheritDoc}
   *
   * <p>Validates that a log identifier has been provided.
   *
   * @throws RuntimeException if the log identifier is not provided
   */
  @Override
  protected void validateInput() {
    if (logIdentifier == null || logIdentifier.isEmpty()) {
      throw new RuntimeException(
          "Log identifier is required. Usage: pal log print <LOG_NAME_OR_PATH>");
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initializes the directory connection provider using the connection string from the parent
   * command.
   */
  @Override
  protected void initialize() {
    if (palCommand != null && palCommand.getPalDirectoryConnectionString() != null) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Prints messages from the specified log by routing to the appropriate method based on log
   * type.
   *
   * @return 0 if the operation is successful, 1 if no log is found or an error occurs
   * @throws Exception if an error occurs while consuming messages
   */
  @Override
  protected int runCommand() throws Exception {
    return printLogMessageConsumer();
  }

  /**
   * Initiates the printing of log messages by routing to the appropriate method based on log type.
   *
   * <p>Uses {@link LogResolver} to resolve the log identifier to a {@link LogInfo}, then delegates
   * to either {@link #printKafkaLogMessages(LogInfo)} or {@link
   * #printChronicleLogMessages(LogInfo)}.
   *
   * @return 0 if the operation is successful, 1 if no log is found for the given identifier.
   * @throws Exception if an error occurs while consuming messages.
   */
  private int printLogMessageConsumer() throws Exception {

    logger.info("Started printer for log: {}", logIdentifier);

    // Register shutdown hook so follow-mode loops can detect SIGTERM/SIGINT
    if (follow) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread("log-print-shutdown-hook") {
                @Override
                public void run() {
                  interrupted = true;
                  // halt() here so the exit code is set before the JVM's own shutdown
                  // sequence completes with an uncontrolled 143 (128 + SIGTERM).
                  Runtime.getRuntime().halt(EXIT_INTERRUPTED);
                }
              });
    }

    String kafkaServersToUse = kafkaServers != null ? kafkaServers : getKafkaServers();
    LogResolver logResolver =
        new LogResolver(directoryConnectionProvider, kafkaServersToUse, getChronicleBaseDir());
    LogInfo log = logResolver.resolveLogInfo(logIdentifier);

    if (log == null) {
      logger.error("No Log found for identifier: {}", logIdentifier);
      return 1;
    }

    // Route based on log type
    if (log.getLogType() == LogType.CHRONICLE) {
      return printChronicleLogMessages(log);
    } else {
      return printKafkaLogMessages(log);
    }
  }

  /**
   * Prints messages from a Kafka log.
   *
   * @param log the LogInfo for the Kafka log
   * @return 0 if the operation is successful
   * @throws Exception if an error occurs while consuming messages
   */
  private int printKafkaLogMessages(LogInfo log) throws Exception {

    final String topic = log.getName();
    final String bootstrapServers = log.getBootstrapServers();

    // 2) Configure the Consumer
    Properties consumerProps = new Properties();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaLogMessageDeserializer.class);

    // print config and filters
    if (verbose) {
      printVerboseFilters(
          String.format("Kafka config: topic=%s, bootstrap=%s", topic, bootstrapServers),
          "offset id",
          offset,
          withReturn);
    }

    // 3) Create the Consumer
    try (Consumer<String, LogMessage<?>> consumer = new KafkaConsumer<>(consumerProps)) {

      // 4) Assign single partition: (topic, 0)
      final TopicPartition partition0 = new TopicPartition(topic, 0);
      consumer.assign(Collections.singleton(partition0));

      // 5) fetch the "end offset" to know our stopping point
      final long endOffset;
      try (AdminClient admin = createAdminClient(bootstrapServers)) {
        Map<TopicPartition, OffsetSpec> request = Map.of(partition0, OffsetSpec.latest());
        var offsetsResult = admin.listOffsets(request).all().get();
        endOffset = offsetsResult.get(partition0).offset();
      }
      if (verbose) {
        System.out.printf("End offset at startup is %d%n", endOffset);
      }

      // 6) If the user wants to read from a specific offset
      if (offset != null) {
        if (offset <= endOffset) {
          // seek to the given offset if already exists
          if (verbose) {
            System.out.printf("Seeking to offset %d%n", offset);
          }
          consumer.seek(partition0, offset);
        } else {
          // offset is yet to come, so seek to endOffset
          if (verbose) {
            System.out.printf("Seeking to latest offset: %d%n", endOffset);
          }
          consumer.seek(partition0, endOffset);
        }
      } else {
        // Although we have 'auto.offset.reset=earliest', for explicit control:
        long earliest =
            consumer.beginningOffsets(Collections.singleton(partition0)).get(partition0);
        consumer.seek(partition0, earliest);
        if (verbose) {
          System.out.printf("Seeking to earliest offset: %d%n", earliest);
        }
      }

      // 7) Start polling
      final AtomicBoolean done = new AtomicBoolean(false);
      // Tracks nesting depth when scanning for the matching return with --with-return
      final AtomicInteger withReturnDepth = new AtomicInteger(0);
      while (!done.get() && !interrupted) {

        ConsumerRecords<String, LogMessage<?>> records = consumer.poll(Duration.ofMillis(200));
        if (records.isEmpty()) {
          // a) If we are in follow mode => keep waiting
          // b) If not follow => check if we are "caught up"
          if (!follow) {
            long positionNow = consumer.position(partition0);
            if (positionNow >= endOffset) {
              if (verbose) {
                System.out.printf("Reached end offset (%d). Exiting.%n", endOffset);
              }
              break;
            }
          }
          continue; // poll again
        }

        // 8) Process each record
        records.forEach(
            rec -> {
              long currentOffset = rec.offset();

              // If user gave a specific offset, print it and done after seeing it
              if (offset != null) {
                if (currentOffset == offset) {
                  printRecord(rec.key(), rec.value(), currentOffset);
                  if (!withReturn) {
                    done.set(true);
                  } else {
                    // Start nesting-depth scan for matching return value
                    withReturnDepth.set(1);
                  }
                } else if (withReturn && withReturnDepth.get() > 0) {
                  // Scan for matching return using nesting depth tracking
                  if (isReturnType(rec.value())) {
                    int newDepth = withReturnDepth.decrementAndGet();
                    if (newDepth == 0) {
                      printRecord(rec.key(), rec.value(), currentOffset);
                      done.set(true);
                    }
                  } else {
                    withReturnDepth.incrementAndGet();
                  }
                }
              } else if (shouldPrint(currentOffset, rec.key(), rec.value())) {
                // if no offset given, apply filters and print
                printRecord(rec.key(), rec.value(), currentOffset);
              }
            });
      }

      if (verbose) {
        System.out.println("Done reading records.");
      }
    }

    if (interrupted) {
      Runtime.getRuntime().halt(EXIT_INTERRUPTED);
    }
    return 0;
  }

  /**
   * Prints messages from a Chronicle log.
   *
   * @param log the LogInfo for the Chronicle log
   * @return 0 if the operation is successful
   * @throws Exception if an error occurs while reading messages
   */
  @SuppressWarnings("PMD.CognitiveComplexity")
  private int printChronicleLogMessages(LogInfo log) throws Exception {
    Path queuePath = Path.of(log.getName());

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Chronicle log w/name: {} has full path: {}", log.getName(), queuePath.toAbsolutePath());
    }

    // Verify the queue exists
    if (!ChronicleLogUtil.queueExists(queuePath)) {
      logger.error("Chronicle log does not exist at path: {}", queuePath);
      return 1;
    }

    // Print config and filters
    if (verbose) {
      printVerboseFilters(
          String.format("Chronicle queue: path=%s", queuePath), "offset/index", offset, withReturn);
    }

    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {

      ExcerptTailer tailer = queue.createTailer();

      // Get queue index info
      ChronicleLogUtil.QueueIndexInfo indexInfo = ChronicleLogUtil.getQueueIndexInfo(queuePath);
      if (indexInfo == null) {
        logger.error("Failed to get index info for Chronicle queue at: {}", queuePath);
        return 1;
      }

      long firstIndex = indexInfo.getFirstIndex();
      long lastIndex = indexInfo.getLastIndex();

      if (verbose) {
        System.out.printf(
            "Queue index range: %d to %d (count: %d)%n",
            firstIndex, lastIndex, indexInfo.getMessageCount());
      }

      // Always start from the beginning to use logical offsets (0-based sequential)
      tailer.toStart();

      if (verbose) {
        if (offset != null) {
          System.out.printf("Starting from first index to reach logical offset %d%n", offset);
        } else {
          System.out.printf("Starting from first index: %d%n", firstIndex);
        }
      }

      // Read and print messages, using logical offsets
      boolean messageFound = false;
      long logicalOffset = 0; // Track logical 0-based sequential offset
      // Tracks nesting depth when scanning for the matching return with --with-return
      int withReturnDepth = 0;

      while (!interrupted) {
        OutboundMsg outboundMsg = OutboundMsg.readNext(tailer);

        if (outboundMsg == null) {
          // No more messages
          if (follow) {
            // In follow mode, wait a bit and try again
            if (verbose && !messageFound) {
              System.out.println("Waiting for messages...");
            }
            Thread.sleep(100);
            continue;
          } else {
            // Not in follow mode, we're done
            break;
          }
        }

        messageFound = true;

        // Reconstruct LogMessage from OutboundMsg for Chronicle messages
        // Unmarshal the body bytes into a Message instance
        Message message = new Message();
        message.unmarshal(outboundMsg.getBody(), 0);

        // Create headers map from OutboundMsg metadata
        LogMessage<Message> logMessage = getLogMessage(outboundMsg, logicalOffset, message);

        // If user requested a specific logical offset, check if we've reached it
        if (offset != null) {
          if (logicalOffset == offset) {
            // Found the requested offset, print it
            printRecord(logMessage, logicalOffset);
            if (withReturn) {
              // Start nesting-depth scan for matching return value
              withReturnDepth = 1;
            } else if (!follow) {
              // Not in follow mode, we're done after printing the requested offset
              break;
            }
          } else if (withReturn && withReturnDepth > 0) {
            // Scan for matching return using nesting depth tracking
            if (isReturnType(logMessage)) {
              withReturnDepth--;
              if (withReturnDepth == 0) {
                printRecord(logMessage, logicalOffset);
                if (!follow) {
                  break;
                }
              }
            } else {
              withReturnDepth++;
            }
          } else if (logicalOffset > offset && !follow) {
            // We've passed the offset without finding it (shouldn't happen), exit
            break;
          }
          // In follow mode, continue after printing the offset
        } else {
          // No specific offset requested, apply filters and print if it passes
          if (shouldPrint(logicalOffset, null, logMessage)) {
            printRecord(logMessage, logicalOffset);
          }
        }

        // Increment logical offset for next message
        logicalOffset++;
      }

      if (verbose) {
        System.out.println("Done reading Chronicle queue messages.");
      }
    }

    if (interrupted) {
      Runtime.getRuntime().halt(EXIT_INTERRUPTED);
    }
    return 0;
  }

  /**
   * Creates a LogMessage from an OutboundMsg for Chronicle messages.
   *
   * @param outboundMsg the OutboundMsg to convert
   * @param currentIndex the current index of the message
   * @param message the Message instance to include in the LogMessage
   * @return a LogMessage containing the message and its metadata
   */
  private static LogMessage<Message> getLogMessage(
      OutboundMsg outboundMsg, long currentIndex, Message message) {
    Map<String, String> headers = new HashMap<>();
    headers.put("message-type", outboundMsg.getMessageType().name());
    headers.put("message-format", "BINARY"); // Chronicle currently only writes binary messages
    if (outboundMsg.getMessageId() != null) {
      headers.put("message-id", outboundMsg.getMessageId());
    }
    if (outboundMsg.getResponseToId() != null) {
      headers.put("response-to-id", outboundMsg.getResponseToId());
    }

    // return new LogMessage using Chronicle constructor (no topic)
    return new LogMessage<>(currentIndex, headers, message);
  }

  /**
   * Creates an AdminClient instance for interacting with Kafka.
   *
   * @param bootstrapServers the Kafka bootstrap servers to connect to
   * @return a configured AdminClient instance
   */
  private AdminClient createAdminClient(String bootstrapServers) {
    Properties adminProps = new Properties();
    adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return AdminClient.create(adminProps);
  }
}
