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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.ittera.pal.common.cli.PalCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.ContextFillingFixedKeyProcessor;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.MessageContext;
import net.ittera.pal.messages.MessageStreamer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.kafka.typed.KafkaLogMessageSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
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
      description = "filter by peer uuid (only valid for BINARY_RPC messages)")
  private String fromPeer;

  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "thread_name",
      description = "filter by thread name (only valid for BINARY_RPC messages)")
  private String threadName;

  /**
   * TODO quite inefficient -> consuming all log until given offset. This option should use ThinPeer
   * (Consumer API), or be in a Search tool that uses it
   */
  @Option(
      names = {"-o", "--offset"},
      paramLabel = "offset",
      description = "print message with given offset (only valid with --log/-l option)")
  protected Long offset;

  @Option(
      names = {"--id"},
      paramLabel = "id",
      description = "print messages with given ID")
  private String id;

  @Option(
      names = {"-f", "--full-output"},
      description = "full message output")
  private boolean fullOutput;

  @Option(
      names = {"-j", "--json-output"},
      description = "full message output (JSON format)")
  private boolean jsonOutput;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  private Integer printLogMessageStream() {
    logger.info("Started printer for log: {}", logIdentifier);
    PalDirectory palDirectory = getPalDirectory();

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

    if (log == null) {
      logger.error("No Log found for identifier: {}", logIdentifier);
      return 1;
    }

    // if not a valid UUID, or we have no PalDirectory, then treat log identifier as name
    final String logName = log.getName();
    final String bootstrapServers = log.getBootstrapServers();

    /*
    1. CONFIGURE STREAMS API
    */
    Properties props = new Properties();
    String consumerId = "printer-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s%n%n",
          logName, bootstrapServers, consumerId);
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaLogMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();

    // stream: deserialize value
    KStream<String, LogMessage<?>> stream = builder.stream(logName);

    // stream: apply filter: message formats
    if (msgFormats != null) {
      if (verbose) {
        System.out.printf("Filtering by format(s): %s%n", String.join(",", msgFormats));
      }
      stream =
          stream.filter(
              (k, message) -> {
                String messageFormat = getMessageFormat(message);
                if (messageFormat == null) {
                  throw new RuntimeException("Unknown message format of message: " + message);
                }
                return msgFormats.contains(messageFormat);
              });
    }

    // stream: apply filter: message types
    if (msgTypes != null) {
      if (verbose) {
        System.out.printf("Filtering by type(s): %s%n", String.join(",", msgTypes));
      }
      stream =
          stream.filter(
              (k, message) -> {
                String messageType = getMessageTypeName(message);
                if (messageType == null) {
                  throw new RuntimeException("Unknown message format of message: " + message);
                }
                // remove the "EXEC_" prefix
                messageType = messageType.substring(5);
                return msgTypes.contains(messageType);
              });
    }

    // stream: apply filter: from peer (uuid)
    if (fromPeer != null) {
      if (verbose) {
        System.out.printf("Filtering by peer: %s%n", fromPeer);
      }
      stream =
          stream.filter(
              (k, message) -> {
                if (isBinaryRpc(message)) {
                  return fromPeer.equalsIgnoreCase(getPeerUuid((Message) message.getContent()));
                } else { // since we don't have the peer for JSONRPC messages, we can't filter by it
                  return true;
                }
              });
    }

    // stream: apply filter: from thread name
    if (threadName != null) {
      if (verbose) {
        System.out.printf("Filtering by thread: %s%n", threadName);
      }
      stream =
          stream.filter(
              (k, message) -> {
                if (isBinaryRpc(message)
                    && ((Message) message.getContent()).getExecMessage() != null) {
                  ExecMessage execMessage = ((Message) message.getContent()).getExecMessage();
                  return execMessage != null
                      && threadName.equalsIgnoreCase(execMessage.getThreadName());
                } else { // since we don't have the thread name for non-ExecMessages, we can't
                  // filter by it
                  return true;
                }
              });
    }

    // stream: apply filter: msg ID
    if (id != null) {
      if (verbose) {
        System.out.printf("Filtering by message id: %s%n", id);
      }
      stream = stream.filter((k, message) -> id.equalsIgnoreCase(getId(message)));
    }

    // stream: transform: add context  (offset, partition, timestamp, headers, etc.)
    String logId = log.getUuid() != null ? log.getUuid().toString() : null;

    // Create the processor supplier
    FixedKeyProcessorSupplier<String, LogMessage<?>, Map<String, Object>> processorSupplier =
        () -> new ContextFillingFixedKeyProcessor(logId);

    KStream<String, Map<String, Object>> streamWithCtxt =
        stream.processValues(processorSupplier, Named.as("context-filling-processor"));

    // stream: apply filter: offset
    if (offset != null) {
      streamWithCtxt =
          streamWithCtxt.filter((k, m) -> ((MessageContext) m.get("context")).offset() == offset);
    }

    // stream: print
    streamWithCtxt.foreach(
        (k, m) -> {
          var msg = (LogMessage<?>) m.get("message");
          MessageContext ctxt = (MessageContext) m.get("context");
          if (fullOutput) {
            System.out.printf(
                "CONTEXT: offset: %d, topic: %s, partition: %d, key: %s, timestamp: %d %nHEADERS: %s%n%s%n",
                ctxt.offset(),
                ctxt.topic(),
                ctxt.partition(),
                k,
                ctxt.timestamp(),
                msg.getHeaders(),
                getMessageContentAsPrettyJson(msg));
          } else if (jsonOutput) { // JSON format pretty-print
            System.out.printf(
                "offset: %d,%n%s%n", ctxt.offset(), getMessageContentAsPrettyJson(msg));
          } else { // JSON format compact
            System.out.printf(
                "offset=%d id=%s message=%s%n", ctxt.offset(), getId(msg), getMessageOneLiner(msg));
          }
        });

    final Topology topology = builder.build();

    if (verbose) {
      System.out.println("TOPOLOGY:");
      System.out.println("=========");
      System.out.println(topology.describe());
    }

    /*
     3. START PROCESSING
    */
    @SuppressWarnings("resource")
    final KafkaStreams streams = new KafkaStreams(topology, props);
    final CountDownLatch latch = new CountDownLatch(1);

    // attach shutdown handler to catch control-c
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                streams.close();
                latch.countDown();
              }
            });

    // start consuming and printing
    try {
      if (verbose) {
        System.out.println("STREAM:");
        System.out.println("=======");
      }
      streams.start();
      latch.await();
    } catch (Throwable e) {
      logger.error("Uncaught error processing stream", e);
      return 1;
    }
    return 0;
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
                if (fullOutput) {
                  System.out.printf("%s%n", msg.toString());
                } else if (jsonOutput) {
                  System.out.printf("%s%n", ColferUtils.toJson(msg, true));
                } else { // compact format (default)
                  System.out.printf(
                      "uuid=%s type=%s%n", getMessageTypeName(msg), getMessageTypeName(msg));
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
      return printLogMessageStream();
    } else {
      throw new RuntimeException(
          "Either -log (for log streaming) or -pu/-pa (for socket streaming) is required");
    }
  }
}
