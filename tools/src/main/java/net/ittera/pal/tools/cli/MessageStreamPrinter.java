/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.tools.cli;

import static net.ittera.pal.common.util.Strings.stringAfter;
import static net.ittera.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Option;

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
import net.ittera.pal.common.cli.PalCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.MessageStreamer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.kafka.typed.KafkaLogMessageDeserializer;
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

@Command(
    name = "print",
    customSynopsis = "pal print [OPTIONS]%n",
    description = "Print messages from peers or logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n")
public class MessageStreamPrinter extends AbstractPalSubcommand {

  private static final Logger logger = LoggerFactory.getLogger(MessageStreamPrinter.class);

  enum OutputFormat {
    FULL,
    JSON,
    COMPACT
  }

  @ParentCommand PalCommand palCommand;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "name|uuid",
      description = "read from given log")
  private String logIdentifier;

  @Option(
      names = {"-pu", "--peer-uuid"},
      paramLabel = "uuid",
      description = "subscribe to peer with given UUID")
  private UUID peerUuid;

  @Option(
      names = {"-pa", "--peer-address"},
      paramLabel = "HOST:PORT",
      description = "subscribe to peer with given address")
  private String peerAddress;

  @Option(
      names = {"--formats"},
      arity = "0..*",
      split = ",",
      description = "comma-separated list of message formats to filter by (BINARY, JSON)")
  private List<String> msgFormats;

  // TODO consider using EnumSet for msgTypes
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

  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "uuid",
      description = "Filter by peer uuid")
  private String fromPeer;

  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "thread_name",
      description = "Filter by thread name")
  private String threadName;

  /**
   * TODO quite inefficient -> consuming all log until given offset. This option should use ThinPeer
   * (Consumer API), or be in a Search tool that uses it
   */
  @Option(
      names = {"-o", "--offset"},
      paramLabel = "offset",
      description = {
        "Print message with given offset (valid with --log/-l option)",
        "If given, filters are ignored. Combine with --follow/-f to wait for the message"
      })
  protected Long offset;

  @Option(
      names = {"-f", "--follow"},
      description = "Follow new messages (like 'tail -f')")
  private boolean follow;

  @Option(
      names = {"--id"},
      paramLabel = "id",
      description = "Filter by message ID")
  private String id;

  @Option(
      names = {"--output-format"},
      description = "Output format. Possible values: ${COMPLETION-CANDIDATES}",
      defaultValue = "COMPACT")
  private OutputFormat format;

  @Option(names = "-v", description = "Run verbosely")
  private boolean verbose;

  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Display this help message")
  private boolean helpRequested = false;

  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

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

  private AdminClient createAdminClient(String bootstrapServers) {
    Properties adminProps = new Properties();
    adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    return AdminClient.create(adminProps);
  }

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
              palDirectory.getAllLogs().stream()
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
      peerAddress = getPalDirectory().getPeerInfo(peerUuid).getPubAddress();
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

  @Override
  public void validateInput() {}

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
