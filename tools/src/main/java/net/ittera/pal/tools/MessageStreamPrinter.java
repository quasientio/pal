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

package net.ittera.pal.tools;

import static net.ittera.pal.common.util.Strings.stringAfter;
import static net.ittera.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Option;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.ContextFillingTransformSupplier;
import net.ittera.pal.messages.MessageContext;
import net.ittera.pal.messages.MessageStreamer;
import net.ittera.pal.messages.protobuf.KafkaExecMessageSerde;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "printer")
public class MessageStreamPrinter extends AbstractTool implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(MessageStreamPrinter.class);
  private static final Printer protobufJsonPrinter = JsonFormat.printer();

  @Option(
      names = {"-b", "--bootstrap-servers"},
      paramLabel = "BOOTSTRAP_SERVERS",
      defaultValue = "localhost:9092",
      description = "kafka bootstrap servers (default: ${DEFAULT-VALUE})")
  private String bootstrapServers;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "LOGNAME",
      description = "read from given log")
  private String logName;

  @Option(
      names = {"-pu", "--peer-uuid"},
      paramLabel = "PEER_UUID",
      description = "subscribe to peer with given UUID")
  private UUID peerUuid;

  @Option(
      names = {"-pa", "--peer-address"},
      paramLabel = "PEER_ADDRESS",
      description = "subscribe to peer with given address")
  private String peerAddress;

  @Option(
      names = {"-d", "--dir-address"},
      paramLabel = "PAL_DIRECTORY",
      defaultValue = "localhost:2181",
      description = "address of PAL directory (default: ${DEFAULT-VALUE})")
  private String palDirAddress;

  @Option(
      names = {"-t", "--types"},
      arity = "0..*",
      description =
          "type(s) of messages to filter by ("
              +
              // list of available msg types is in wrappers.proto (serdes module) TODO: list
              // programmatically
              "STATIC_CONSTRUCTOR, RETURN_CLASS, CONSTRUCTOR, INSTANCE_METHOD,"
              + "CLASS_METHOD, GET_STATIC, GET_FIELD, PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE, THROWABLE,"
              + "RETURN_VALUE)")
  private List<String> msgTypes;

  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "PEER_UUID",
      description = "filter by peer uuid")
  private String fromPeer;

  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "THREAD_NAME",
      description = "filter by thread name")
  private String threadName;

  /**
   * TODO quite inefficient -> consuming all log until given offset. This option should use ThinPeer
   * (Consumer API), or be in a Search tool that uses it
   */
  @Option(
      names = {"-o", "--offset"},
      paramLabel = "MESSAGE_OFFSET",
      description = "print message with given offset (only valid with --log/-l option)")
  protected Long offset;

  @Option(
      names = {"-u", "--uuid"},
      paramLabel = "MESSAGE_UUID",
      description = "print message with given UUID")
  private String uuid;

  @Option(
      names = {"-f", "--full-output"},
      description = "full message output (PROTOBUF format)")
  private boolean fullOutput;

  @Option(
      names = {"-j", "--json-output"},
      description = "full message output (JSON format)")
  private boolean jsonOutput;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  private static String getProperty(String propertyName, @Nullable String defaultValue) {
    if (System.getProperty(propertyName) != null) {
      logger.debug("loading value of '{}' from system properties", propertyName);
      return System.getProperty(propertyName);
    } else if (System.getenv(propertyName.toUpperCase()) != null) {
      logger.debug("loading value of '{}' from ENV", propertyName.toUpperCase());
      return System.getenv(propertyName.toUpperCase());
    } else if (defaultValue != null) {
      logger.debug("loading value of '{}' from default", propertyName);
      return defaultValue;
    }
    return null;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new MessageStreamPrinter()).execute(args);
    System.exit(exitCode);
  }

  private Integer printLogMessageStream() {

    Objects.requireNonNull(bootstrapServers, "bootstrap servers required");

    /*
    1. CONFIGURE STREAMS API
    */
    Properties props = new Properties();
    String consumerId = "printer-" + UUID.randomUUID().toString();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s\n",
          logName, bootstrapServers, consumerId);
      if (msgTypes != null) {
        System.out.printf("Filtering by type(s): %s\n", String.join(",", msgTypes));
      }
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaExecMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();

    // stream: deserialize value
    KStream<String, Message> stream =
        builder.<String, byte[]>stream(logName)
            .map(
                (k, v) -> {
                  try {
                    return new KeyValue<>(k, Message.parseFrom(v));
                  } catch (InvalidProtocolBufferException e) {
                    logger.error("Error parsing message", e);
                    return new KeyValue<>(k, null);
                  }
                });

    // stream: apply filter: message types
    if (msgTypes != null) {
      stream =
          stream.filter(
              (k, m) ->
                  (m.hasExecMessage()
                      && msgTypes.contains(m.getExecMessage().getMsgType().toString())));
    }

    // stream: apply filter: from peer (uuid)
    if (fromPeer != null) {
      stream = stream.filter((k, m) -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
    }

    // stream: apply filter: from thread name
    if (threadName != null) {
      stream =
          stream.filter(
              (k, m) ->
                  (m.hasExecMessage()
                      && threadName.equalsIgnoreCase(m.getExecMessage().getThreadName())));
    }

    // stream: apply filter: msg UUID
    if (uuid != null) {
      stream = stream.filter((k, m) -> uuid.equalsIgnoreCase(getMessageUuid(m)));
    }

    // stream: transform: add context  (offset, partition, timestamp, headers, etc.)
    KStream<String, Map> streamWithCtxt = stream.transform(ContextFillingTransformSupplier::new);

    // stream: apply filter: offset
    if (offset != null) {
      streamWithCtxt =
          streamWithCtxt.filter(
              (k, m) -> ((MessageContext) m.get("context")).getOffset() == offset);
    }

    // stream: print
    streamWithCtxt.foreach(
        (k, m) -> {
          Message msg = (Message) m.get("message");
          MessageContext ctxt = (MessageContext) m.get("context");
          if (fullOutput) {
            System.out.printf(
                "CTXT: offset: %d, partition: %d, timestamp: %d, headers: %s, %nMESSAGE:%n%s%n",
                ctxt.getOffset(),
                ctxt.getPartition(),
                ctxt.getTimestamp(),
                ctxt.getHeadersToString(),
                msg.toString());
          } else if (jsonOutput) {
            try {
              System.out.printf(
                  "offset: %d,%nmessage: %s%n", ctxt.getOffset(), protobufJsonPrinter.print(msg));
            } catch (InvalidProtocolBufferException e) {
              logger.error("Error printing message as JSON", e);
              e.printStackTrace();
            }
          } else { // compact format (default)
            System.out.printf(
                "offset=%d uuid=%s key=%s type=%s%n",
                ctxt.getOffset(), getMessageUuid(msg), k, getMessageType(msg));
          }
        });

    final Topology topology = builder.build();

    if (verbose) {
      System.out.println(topology.describe());
    }

    /*
     3. START PROCESSING
    */
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
      e.printStackTrace();
      return 1;
    }
    return 0;
  }

  private ExecutorService getExecutor(int nThreads) {
    return Executors.newFixedThreadPool(
        nThreads,
        r -> {
          Thread thread = new Thread(r);
          thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
          return thread;
        });
  }

  private Integer printSocketMessageStream() throws Exception {
    Objects.requireNonNull(palDirAddress, "palDirAddress required");

    ExecutorService executor = getExecutor(1);

    if (peerAddress == null) { // peerUuid must be present then
      String palDirectoryURL =
          palDirAddress != null ? palDirAddress : getProperty("pal_directory", null);
      try (PALDirectory palDirectory = new PALDirectory(palDirectoryURL)) {
        peerAddress = palDirectory.getPeerInfo(peerUuid).getPubAddress();
      }
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
            stream = stream.filter(m -> (msgTypes.contains(getMessageType(m))));
          }
          // stream: apply filter: from peer (uuid)
          if (fromPeer != null) {
            stream = stream.filter(m -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
          }

          // stream: apply filter: from thread name
          if (threadName != null) {
            stream =
                stream.filter(
                    m ->
                        m.hasExecMessage()
                            && threadName.equalsIgnoreCase(m.getExecMessage().getThreadName()));
          }

          // stream: apply filter: msg UUID
          if (uuid != null) {
            stream = stream.filter(m -> uuid.equalsIgnoreCase(getMessageUuid(m)));
          }

          // stream: print
          stream.forEach(
              msg -> {
                if (fullOutput) {
                  System.out.printf("%s%n", msg.toString());
                } else if (jsonOutput) {
                  try {
                    System.out.printf("%s%n", protobufJsonPrinter.print(msg));
                  } catch (InvalidProtocolBufferException e) {
                    logger.error("Error printing message as JSON", e);
                  }
                } else { // compact format (default)
                  System.out.printf("uuid=%s type=%s%n", getMessageType(msg), getMessageType(msg));
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
      executor.submit(streamerThread);
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

  public Integer call() throws Exception {
    if (peerAddress != null || peerUuid != null) {
      return printSocketMessageStream();
    } else if (logName != null) {
      return printLogMessageStream();
    } else {
      throw new RuntimeException(
          "Either -log (for log streaming) or -pu/-pa (for socket streaming) is required");
    }
  }
}
