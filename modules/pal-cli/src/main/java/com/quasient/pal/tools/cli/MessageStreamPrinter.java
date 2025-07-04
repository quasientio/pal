/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import static com.quasient.pal.common.util.Strings.stringAfter;
import static com.quasient.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Option;

import com.quasient.pal.common.cli.PalCommand;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.MessageStreamer;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
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
  // TODO consider using EnumSet for better type safety.
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
   * Specifies the output format for the printed messages.
   *
   * <p>Possible values are FULL, JSON, and COMPACT. The default format is COMPACT.
   */
  @Option(
      names = {"--output-format"},
      description = "Output format. Possible values: ${COMPLETION-CANDIDATES}",
      defaultValue = "COMPACT")
  private OutputFormat format;

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
   * Initiates the printing of log messages by setting up and configuring a Kafka consumer to read
   * messages from the specified log.
   *
   * @return 0 if the operation is successful, 1 if no log is found for the given identifier.
   * @throws Exception if an error occurs while consuming messages.
   */
  private int printLogMessageConsumer() throws Exception {

    logger.info("Started printer for log: {}", logIdentifier);
    PalDirectory palDirectory = getPalDirectory();

    // 1) Resolve the log info
    LogInfo log = resolveLogInfo(palDirectory, logIdentifier);
    if (log == null) {
      logger.error("No Log found for identifier: {}", logIdentifier);
      return 1;
    }

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
      System.out.printf("Kafka config: topic=%s, bootstrap=%s%n", topic, bootstrapServers);
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
        System.out.printf("Will print message with offset id: %s and then exit%n", offset);
      }
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
      String peer = key;
      if (!fromPeer.equalsIgnoreCase(peer)) {
        return false;
      }
    }
    // 4) fromThread
    if (threadName != null) {
      if (isBinaryRpc(msg)) {
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
      if (!id.equalsIgnoreCase(msgId)) {
        return false;
      }
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
    switch (format) {
      case FULL ->
          System.out.printf(
              "CONTEXT: offset: %d key: %s %nHEADERS: %s%n%s%n",
              offset, key, msg.getHeaders(), getMessageContentAsPrettyJson(msg));
      case JSON ->
          System.out.printf("offset: %d,%n%s%n", offset, getMessageContentAsPrettyJson(msg));
      case COMPACT ->
          System.out.printf(
              "offset=%d id=%s message=%s%n", offset, getId(msg), getMessageOneLiner(msg));
      default -> throw new IllegalStateException("Unexpected value: " + format);
    }
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
    } catch (Exception e) {
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
        } catch (Exception e) {
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
                switch (format) {
                  case FULL -> System.out.printf("%s%n", msg.toString());
                  case JSON -> System.out.printf("%s%n", ColferUtils.toJson(msg, true));
                  case COMPACT ->
                      System.out.printf(
                          "uuid=%s type=%s%n", getMessageTypeName(msg), getMessageTypeName(msg));
                  default -> throw new IllegalStateException("Unexpected value: " + format);
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
