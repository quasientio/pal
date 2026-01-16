/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static io.quasient.pal.common.util.Strings.stringAfter;
import static io.quasient.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.ArgGroup;
import static picocli.CommandLine.Option;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.MessageStreamer;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
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
 * Prints messages from peers or logs based on the specified options.
 *
 * <p>This command allows users to stream and display messages either from a log identified by name
 * or UUID or by subscribing to a peer via its UUID or address. It supports filtering messages by
 * format, type, peer UUID, thread name, message ID, and offset. Additionally, it provides options
 * to follow new messages similar to the 'tail -f' command.
 *
 * <p>The output can be formatted in full, JSON, or compact forms, and verbosity can be toggled for
 * detailed logging information.
 */
@Command(
    name = "print",
    customSynopsis = "pal print [OPTIONS]%n",
    description = "Print messages from peers or logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n")
@SuppressFBWarnings(
    value = {"SIC_INNER_SHOULD_BE_STATIC_ANON", "URF_UNREAD_FIELD"},
    justification = "Anonymous Thread subclass for shutdown hook")
public class MessageStreamPrinter extends AbstractPalSubcommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessageStreamPrinter.class);

  /**
   * Enum representing the output formats available for printing messages.
   *
   * <p>Supported formats include:
   *
   * <ul>
   *   <li>FULL - Detailed output with context and headers.
   *   <li>JSON - Output in JSON format.
   *   <li>COMPACT - Concise output with key information.
   * </ul>
   */
  enum OutputFormat {
    /** Fully detailed output, including context and headers. */
    FULL,

    /** Machine-readable output serialized as JSON. */
    JSON,

    /** Minimalist output showing only the essentials in a compact form. */
    COMPACT
  }

  /** Parent command that provides access to the main PalCommand context. */
  @ParentCommand PalCommand palCommand;

  /**
   * Identifier for the log to read from. Can be specified by log name or UUID.
   *
   * <p>When provided, the command will stream messages from the specified log.
   */
  @Option(
      names = {"-l", "--log"},
      paramLabel = "name|uuid",
      description = "read from given log")
  private String logIdentifier;

  /**
   * Kafka bootstrap servers for direct access to Kafka logs without PAL_DIRECTORY.
   *
   * <p>When provided, allows reading Kafka logs directly without connecting to the PAL directory.
   * Takes precedence over the KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "host:port[,host:port...]",
      description = "Kafka bootstrap servers (for direct Kafka access without -d)")
  private String kafkaServers;

  /**
   * UUID of the peer to subscribe to.
   *
   * <p>When provided, the command will subscribe to the peer with the specified UUID to stream
   * messages.
   */
  @Option(
      names = {"-pu", "--peer-uuid"},
      paramLabel = "uuid",
      description = "subscribe to peer with given UUID")
  private UUID peerUuid;

  /**
   * Address of the peer to subscribe to, in the format HOST:PORT.
   *
   * <p>When provided, the command will subscribe to the peer at the specified address to stream
   * messages.
   */
  @Option(
      names = {"-pa", "--peer-address"},
      paramLabel = "HOST:PORT",
      description = "subscribe to peer with given address")
  private String peerAddress;

  /**
   * Comma-separated list of message formats to filter by.
   *
   * <p>Supported formats are BINARY and JSON. If specified, only messages matching the provided
   * formats will be displayed.
   */
  @Option(
      names = {"--formats"},
      arity = "0..*",
      split = ",",
      description = "comma-separated list of message formats to filter by (BINARY, JSON)")
  private List<String> msgFormats;

  /**
   * Comma-separated list of message types to filter by.
   *
   * <p>Supported types include: CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD, GET_STATIC, GET_FIELD,
   * PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE, RETURN_VALUE, THROWABLE.
   */
  @Option(
      names = {"--types"},
      arity = "0..*",
      split = ",",
      description =
          "comma-separated list of message types to filter by ("
              + "CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD,"
              + " GET_STATIC, GET_FIELD,"
              + " PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE,"
              + " RETURN_VALUE, THROWABLE)")
  private List<String> msgTypes;

  /**
   * Peer UUID to filter messages by.
   *
   * <p>If specified, only messages from the peer with this UUID will be displayed.
   */
  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "uuid",
      description = "Filter by peer uuid")
  private String fromPeer;

  /**
   * Thread name to filter messages by.
   *
   * <p>If specified, only messages originating from the specified thread name will be displayed.
   */
  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "thread_name",
      description = "Filter by thread name")
  private String threadName;

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
        "Print message with given offset (valid with --log/-l option)",
        "If given, filters are ignored. Combine with --follow/-f to wait for the message"
      })
  protected Long offset;

  /** If set, the command will follow new messages as they arrive, similar to 'tail -f'. */
  @Option(
      names = {"-f", "--follow"},
      description = "Follow new messages (like 'tail -f')")
  private boolean follow;

  /**
   * Message ID to filter messages by.
   *
   * <p>If specified, only messages with the given ID will be displayed.
   */
  @Option(
      names = {"--id"},
      paramLabel = "id",
      description = "Filter by message ID")
  private String id;

  /**
   * Output format options group.
   *
   * <p>These options are mutually exclusive. If none is specified, COMPACT is the default.
   */
  static class FormatOptions {
    /** Flag indicating compact output format should be used. */
    @Option(
        names = {"--compact"},
        description = "Compact output format (default)")
    boolean compact;

    /** Flag indicating JSON output format should be used. */
    @Option(
        names = {"--json"},
        description = "JSON output format")
    boolean json;

    /** Flag indicating full output format should be used. */
    @Option(
        names = {"--full"},
        description = "Full output format with all details")
    boolean full;
  }

  /**
   * Specifies the output format for the printed messages.
   *
   * <p>The format options are mutually exclusive. If none is specified, COMPACT is the default.
   */
  @ArgGroup private FormatOptions formatOptions;

  /**
   * Gets the selected output format.
   *
   * @return the selected OutputFormat, defaulting to COMPACT if none specified
   */
  private OutputFormat getFormat() {
    if (formatOptions == null) {
      return OutputFormat.COMPACT;
    }
    if (formatOptions.full) {
      return OutputFormat.FULL;
    }
    if (formatOptions.json) {
      return OutputFormat.JSON;
    }
    // Default to COMPACT (this covers both explicit -c and no option selected)
    return OutputFormat.COMPACT;
  }

  /** If set, the command will run in verbose mode, providing additional logging information. */
  @Option(names = "-v", description = "Run verbosely")
  private boolean verbose;

  /** Displays the help message and exits. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display this help message")
  private boolean helpRequested = false;

  /**
   * {@inheritDoc}
   *
   * <p>Initializes the directory connection provider using the connection string from the parent
   * command.
   */
  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Initiates the printing of log messages by routing to the appropriate method based on log type.
   *
   * @return 0 if the operation is successful, 1 if no log is found for the given identifier.
   * @throws Exception if an error occurs while consuming messages.
   */
  private int printLogMessageConsumer() throws Exception {

    logger.info("Started printer for log: {}", logIdentifier);

    // Strip "file:" prefix if present
    String chronicleFilePrefix = "file:";
    boolean isChronicleLog = logIdentifier.startsWith(chronicleFilePrefix);
    String logNameOrPath =
        isChronicleLog ? logIdentifier.substring(chronicleFilePrefix.length()) : logIdentifier;

    // Try to get PalDirectory if available
    PalDirectory palDirectory = null;
    try {
      if (directoryConnectionProvider != null) {
        Optional<PalDirectory> palDirOpt = directoryConnectionProvider.get();
        palDirectory = palDirOpt.orElse(null);
      }
    } catch (RuntimeException e) {
      logger.debug("PalDirectory not available: {}", e.getMessage());
    }

    LogInfo log;

    // If PalDirectory is available, use it to resolve the log
    if (palDirectory != null) {
      log = resolveLogInfo(palDirectory, logNameOrPath);
      if (log == null) {
        logger.error("No Log found for identifier: {}", logIdentifier);
        return 1;
      }
    } else {
      // PalDirectory not available, try to work with environment variables
      logger.info("PalDirectory not available, attempting to use environment variables");

      if (isChronicleLog) {
        // Create a minimal LogInfo for Chronicle log
        log = new LogInfo(logNameOrPath);
        log.setLogType(LogType.CHRONICLE);
        logger.info("Using Chronicle log without PAL_DIRECTORY: {}", logNameOrPath);
      } else {
        // Kafka log: verify KAFKA_SERVERS is available (from option or environment)
        String kafkaServersToUse = kafkaServers != null ? kafkaServers : getKafkaServers();
        if (kafkaServersToUse == null) {
          logger.error(
              "Cannot print Kafka log without PAL_DIRECTORY: "
                  + "use --kafka-servers/-k option or set KAFKA_SERVERS environment variable");
          return 1;
        }
        // Create a minimal LogInfo for Kafka log
        log = new LogInfo(logNameOrPath, kafkaServersToUse);
        log.setLogType(LogType.KAFKA);
        logger.info(
            "Using Kafka log without PAL_DIRECTORY: topic={}, servers={}",
            logNameOrPath,
            kafkaServersToUse);
      }
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
          "offset id");
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
      while (!done.get()) {

        ConsumerRecords<String, LogMessage<?>> records = consumer.poll(Duration.ofMillis(200));
        if (records.isEmpty()) {
          // a) If we are in follow mode => keep waiting
          // b) If offset != null => maybe we already read that offset? We handle that below
          // c) If not follow => check if we are "caught up"
          if (!follow && offset == null) {
            // We can compare current position to endOffset
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
                  done.set(true);
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

    return 0;
  }

  /**
   * Prints the verbose header line and common filter details used in both Kafka and Chronicle
   * paths.
   *
   * @param headerLine the first line describing the source/context (already formatted)
   * @param offsetDescriptor the label to use for the offset line (e.g., "offset id",
   *     "offset/index")
   */
  private void printVerboseFilters(String headerLine, String offsetDescriptor) {
    System.out.println(headerLine);
    if (msgFormats != null) {
      System.out.printf("Filtering by format(s): %s%n", String.join(",", msgFormats));
    }
    if (msgTypes != null) {
      System.out.printf("Filtering by type(s): %s%n", String.join(",", msgTypes));
    }
    if (fromPeer != null) {
      System.out.printf("Filtering by peer: %s%n", fromPeer);
    }
    if (threadName != null) {
      System.out.printf("Filtering by thread: %s%n", threadName);
    }
    if (id != null) {
      System.out.printf("Filtering by message id: %s%n", id);
    }
    if (offset != null) {
      System.out.printf("Will print message with %s: %s and then exit%n", offsetDescriptor, offset);
    }
  }

  /**
   * Prints messages from a Chronicle log.
   *
   * @param log the LogInfo for the Chronicle log
   * @return 0 if the operation is successful
   * @throws Exception if an error occurs while reading messages
   */
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
      printVerboseFilters(String.format("Chronicle queue: path=%s", queuePath), "offset/index");
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

      while (true) {
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
            if (!follow) {
              // Not in follow mode, we're done after printing the requested offset
              break;
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
   * Determines whether a message should be printed based on the current filters and offset.
   *
   * @param recOffset the offset of the current record
   * @param key the key associated with the message
   * @param msg the log message to evaluate
   * @return {@code true} if the message meets the criteria and should be printed; {@code false}
   *     otherwise
   */
  private boolean shouldPrint(Long recOffset, String key, LogMessage<?> msg) {

    // First thing is to check offset
    if (offset != null && offset.equals(recOffset)) {
      return true;
    }

    // 1) Filter by msgFormats?
    if (msgFormats != null) {
      String format = getMessageFormat(msg);
      if (format == null || !msgFormats.contains(format)) {
        return false;
      }
    }
    // 2) Filter by msgTypes?
    if (msgTypes != null) {
      String type = getMessageTypeName(msg);
      if (type != null) {
        type = type.substring(5); // remove "EXEC_"
        if (!msgTypes.contains(type)) {
          return false;
        }
      } else {
        return false;
      }
    }
    // 3) fromPeer
    if (fromPeer != null) {
      // the message Key is the peer's UUID
      if (!fromPeer.equalsIgnoreCase(key)) {
        return false;
      }
    }
    // 4) fromThread
    if (threadName != null) {
      if (isColferMessage(msg)) {
        Message m = (Message) msg.getContent();
        if (m != null && m.getExecMessage() != null) {
          String t = m.getExecMessage().getThreadName();
          if (!threadName.equalsIgnoreCase(t)) {
            return false;
          }
        }
      }
    }
    // 5) messageId
    if (id != null) {
      String msgId = getId(msg);
      return id.equalsIgnoreCase(msgId);
    }
    return true;
  }

  /**
   * Prints a single record to the standard output in the specified format.
   *
   * @param key the key associated with the message
   * @param msg the log message to print
   * @param offset the offset of the message
   */
  private void printRecord(String key, LogMessage<?> msg, long offset) {
    switch (getFormat()) {
      case FULL ->
          System.out.printf(
              "CONTEXT: offset: %d key: %s %nHEADERS: %s%n%s%n",
              offset, key, msg.getHeaders(), getMessageContentAsPrettyJson(msg));
      case JSON ->
          System.out.printf("offset: %d,%n%s%n", offset, getMessageContentAsPrettyJson(msg));
      case COMPACT ->
          System.out.printf(
              "offset=%d id=%s message=%s%n", offset, getId(msg), getMessageOneLiner(msg));
      default -> throw new IllegalStateException("Unexpected value: " + getFormat());
    }
  }

  /**
   * Prints a single record to the standard output without a key (for Chronicle messages).
   *
   * @param msg the log message to print
   * @param offset the offset/index of the message
   */
  private void printRecord(LogMessage<?> msg, long offset) {
    printRecord(null, msg, offset);
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

  /**
   * Resolves the LogInfo object based on the provided log identifier.
   *
   * @param palDirectory the PalDirectory instance for accessing log information
   * @param logIdentifier the name or UUID of the log to resolve
   * @return the resolved LogInfo object, or {@code null} if no matching log is found
   */
  private LogInfo resolveLogInfo(PalDirectory palDirectory, String logIdentifier) {
    LogInfo log = null;
    // try to get log by name
    try {
      log = palDirectory.getLogInfo(logIdentifier);
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      logger.debug("Error trying to find log by name in directory: {}", logIdentifier);
    }

    if (log == null) {

      // ok, then try as UUID
      final UUID logUuid;
      UUID parsedUuid = null;
      try {
        parsedUuid = UUID.fromString(logIdentifier);
      } catch (IllegalArgumentException iae) {
        // never mind
      } finally {
        logUuid = parsedUuid;
      }
      // log identifier is a valid UUID so let's treat it as such
      if (logUuid != null) {
        try {
          Optional<LogInfo> logInfoByUuid =
              palDirectory.listAllLogs().stream()
                  .filter(l -> logUuid.equals(l.getUuid()))
                  .findFirst();
          if (logInfoByUuid.isPresent()) {
            log = logInfoByUuid.get();
            logger.info("Got matching Log: {} for logIdentifier: {}", log, logIdentifier);
          }
        } catch (RuntimeException | ExecutionException | InterruptedException e) {
          logger.error("Error fetching logs from directory.");
        }
      }
    }
    return log;
  }

  /**
   * Initiates the printing of messages by subscribing to a peer's message stream via a socket
   * connection.
   *
   * @return 0 if the operation is successful, 1 if an uncaught error occurs
   * @throws Exception if an error occurs while consuming messages
   */
  private Integer printSocketMessageStream() throws Exception {
    ExecutorService executor =
        Executors.newFixedThreadPool(
            1,
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
              return thread;
            });

    if (peerAddress == null) { // peerUuid must be present then
      peerAddress = getPalDirectory().getPeer(peerUuid).getPubAddress();
    }

    // parse address
    String hostAndPort =
        peerAddress.contains("://") ? stringAfter(peerAddress, "://") : peerAddress;
    String host = stringBefore(hostAndPort, ":");
    int port = Integer.parseInt(stringAfter(hostAndPort, ":"));

    final MessageStreamer streamer = new MessageStreamer(host, port).connect();
    logger.info("Connected printer to {}:{}", host, port);

    Runnable streamerThread =
        () -> {
          Stream<Message> stream = streamer.getStream();

          // stream: apply filter: message types
          if (msgTypes != null) {
            stream =
                stream.filter(
                    m -> {
                      String messageTypeName = getMessageTypeName(m);
                      // remove the "EXEC_" prefix
                      messageTypeName = messageTypeName.substring(5);
                      return msgTypes.contains(messageTypeName);
                    });
          }
          // stream: apply filter: from peer (uuid)
          if (fromPeer != null) {
            stream = stream.filter(m -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
          }

          // stream: apply filter: from thread name
          if (threadName != null) {
            stream =
                stream.filter(
                    m -> {
                      ExecMessage execMessage = m.getExecMessage();
                      return execMessage != null
                          && threadName.equalsIgnoreCase(execMessage.getThreadName());
                    });
          }

          // stream: apply filter: msg ID
          if (id != null) {
            stream = stream.filter(m -> id.equalsIgnoreCase(getMessageId(m)));
          }

          // stream: print
          stream.forEach(
              msg -> {
                switch (getFormat()) {
                  case FULL -> System.out.printf("%s%n", msg.toString());
                  case JSON -> System.out.printf("%s%n", ColferUtils.toJson(msg, true));
                  case COMPACT ->
                      System.out.printf(
                          "uuid=%s type=%s%n", getMessageTypeName(msg), getMessageTypeName(msg));
                  default -> throw new IllegalStateException("Unexpected value: " + getFormat());
                }
              });
        };

    // latch to wait for termination
    final CountDownLatch latch = new CountDownLatch(1);

    // attach shutdown handler to catch control-c
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                latch.countDown();
              }
            });

    // start consuming and printing
    try {
      executor.execute(streamerThread);
      logger.info("Stream started");
      latch.await();
      logger.info("Shutting down");
      executor.shutdownNow();
      streamer.close();
    } catch (Throwable e) {
      logger.error("Uncaught error", e);
      return 1;
    }
    return 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation does not perform additional input validation.
   */
  @Override
  public void validateInput() {}

  /**
   * {@inheritDoc}
   *
   * <p>Executes the message streaming command based on the specified options. Depending on the
   * provided options, it either streams messages from a log or subscribes to a peer's message
   * stream via socket.
   *
   * @return the exit code of the command execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (peerAddress != null || peerUuid != null) {
      return printSocketMessageStream();
    } else if (logIdentifier != null) {
      return printLogMessageConsumer();
    } else {
      throw new RuntimeException(
          "Either -log (for log streaming) or -pu/-pa (for socket streaming) is required");
    }
  }
}
