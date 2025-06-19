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

package com.quasient.pal.tools.cli;

import static com.quasient.pal.common.util.Strings.stringAfter;
import static com.quasient.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Option;

import com.google.common.base.Splitter;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.messages.MessageStreamer;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.kafka.KafkaMessageSerde;
import com.quasient.pal.tools.AbstractTool;
import com.quasient.pal.tools.stats.ContinuousPrinter;
import com.quasient.pal.tools.stats.Counters;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.annotation.Nullable;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Collects and displays statistics about message streams.
 *
 * <p>This tool processes messages from Kafka logs or socket streams, filters them based on
 * specified criteria, and aggregates various metrics such as message types, peers, threads, and
 * field accesses. The aggregated statistics can be printed continuously in either plain text or
 * JSON format.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   # To collect stats from a Kafka log
 *   stats --bootstrap-servers localhost:9092 --log my-log-topic
 *
 *   # To collect stats from a socket stream
 *   stats --pal-dir-address localhost:2379 --peer-uuid 123e4567-e89b-12d3-a456-426614174000 --peer-address 127.0.0.1:8080
 * </pre>
 *
 * <p>Options allow filtering by message types, peer UUID or address, and thread name.
 */
// TODO This class and MessageStreamPrinter should inherit from base class w/ common logic
@Command(name = "stats")
public class MessageStreamStats extends AbstractTool implements Callable<Integer> {

  /** Logger for logging information and errors. */
  private final Logger logger = LoggerFactory.getLogger(MessageStreamStats.class);

  /** Aggregates various statistics counters for processed messages. */
  private final Counters counters = new Counters();

  /** Latch used to coordinate shutdown signal handling. */
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /** Handles continuous printing of aggregated statistics. */
  private ContinuousPrinter continuousPrinter;

  /** Indicates whether statistics are being printed externally. */
  private boolean externalPrinting = false;

  /**
   * Kafka bootstrap server addresses.
   *
   * <p>Specifies the Kafka bootstrap servers to connect to. Defaults to "localhost:9092" if not
   * provided.
   */
  @Option(
      names = {"-b", "--bootstrap-servers"},
      required = true,
      paramLabel = "BOOTSTRAP_SERVERS",
      defaultValue = "localhost:9092",
      description = "kafka bootstrap servers (default: ${DEFAULT-VALUE})")
  private String bootstrapServers;

  /**
   * Kafka topic name to read logs from.
   *
   * <p>Specifies the name of the Kafka topic containing logs to be processed.
   */
  @Option(
      names = {"-l", "--log"},
      paramLabel = "LOG_NAME",
      description = "read from given log")
  private String logName;

  /**
   * UUID of the peer to subscribe to.
   *
   * <p>Filters messages to include only those from the specified peer UUID.
   */
  @Option(
      names = {"-pu", "--peer-uuid"},
      paramLabel = "PEER_UUID",
      description = "subscribe to peer with given UUID")
  private UUID peerUuid;

  /**
   * Address of the peer to subscribe to.
   *
   * <p>Filters messages to include only those from the specified peer address.
   */
  @Option(
      names = {"-pa", "--peer-address"},
      paramLabel = "PEER_ADDRESS",
      description = "subscribe to peer with given address")
  private String peerAddress;

  /**
   * Address of the PAL directory.
   *
   * <p>Specifies the address of the PAL directory service. Defaults to "localhost:2379" if not
   * provided.
   */
  @Option(
      names = {"-d", "--dir-address"},
      paramLabel = "PAL_DIR_ADDRESS",
      defaultValue = "localhost:2379",
      description = "address of PAL directory (default: ${DEFAULT-VALUE})")
  private String palDirAddress;

  /**
   * Types of messages to filter by.
   *
   * <p>Specifies one or more message types to include in the statistics. Supported types include
   * STATIC_CONSTRUCTOR, RETURN_CLASS, CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD, GET_STATIC,
   * GET_FIELD, PUT_STATIC, PUT_FIELD, PUT_STATIC_DONE, PUT_FIELD_DONE, THROWABLE, RETURN_VALUE.
   */
  // TODO consider using EnumSet for better type safety.
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
   * <p>If set, the aggregated statistics will be printed in JSON format. Otherwise, they will be
   * printed in plain text.
   */
  @Option(
      names = {"-j", "--json-output"},
      description = "print stats as JSON")
  private boolean jsonOutput;

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
  protected boolean helpRequested = false;

  /**
   * Flag to enable verbose output.
   *
   * <p>If set, the tool will output additional debugging and status information.
   */
  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  /**
   * Constructs a MessageStreamStats instance for log-based streams, intended for use from another
   * class rather than the command line (e.g., from a Web/GUI application).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
   */
  public MessageStreamStats(String bootstrapServers, String logName) {
    this(bootstrapServers, logName, null, null, null);
  }

  /**
   * Constructs a MessageStreamStats instance for log-based streams with additional filtering
   * options. Intended for use from another class rather than the command line (e.g., from a Web/GUI
   * application).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
   * @param msgTypes message types to filter by
   * @param fromPeer peer UUID to filter by
   * @param threadName thread name to filter by
   */
  public MessageStreamStats(
      String bootstrapServers,
      String logName,
      @Nullable List<String> msgTypes,
      @Nullable String fromPeer,
      @Nullable String threadName) {
    this.bootstrapServers = bootstrapServers;
    this.logName = logName;
    this.msgTypes = msgTypes;
    this.fromPeer = fromPeer;
    this.threadName = threadName;
    this.externalPrinting = true;
  }

  /**
   * Constructs a MessageStreamStats instance for socket-based streams with additional filtering
   * options. Intended for use from another class rather than the command line.
   *
   * @param palDirAddress address of the PAL directory
   * @param peerUuid UUID of the peer to subscribe to
   * @param peerAddress address of the peer to subscribe to
   * @param msgTypes message types to filter by
   * @param fromPeer peer UUID to filter by
   * @param threadName thread name to filter by
   */
  // Use this constructor to run this class from another class, not the cmd-line.
  // For socket-based streams
  public MessageStreamStats(
      String palDirAddress,
      UUID peerUuid,
      String peerAddress,
      @Nullable List<String> msgTypes,
      @Nullable String fromPeer,
      @Nullable String threadName) {
    this.palDirAddress = palDirAddress;
    this.peerUuid = peerUuid;
    this.peerAddress = peerAddress;
    this.msgTypes = msgTypes;
    this.fromPeer = fromPeer;
    this.threadName = threadName;
    this.externalPrinting = true;
  }

  /** Default constructor for running as a Picocli command (i.e., from the command line). */
  MessageStreamStats() {}

  /**
   * Entry point for the MessageStreamStats application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new MessageStreamStats()).execute(args);
    System.exit(exitCode);
  }

  /**
   * Extracts the short class name from a fully qualified class name.
   *
   * @param classname the fully qualified class name
   * @return the short class name without package prefixes
   */
  private String getShortClassname(String classname) {
    String[] classnameParts = Splitter.on('.').splitToList(classname).toArray(new String[0]);
    return classnameParts.length > 0 ? classnameParts[classnameParts.length - 1] : classname;
  }

  /**
   * Retrieves the counters containing aggregated statistics.
   *
   * @return the {@link Counters} instance with current statistics
   */
  @SuppressWarnings("unused")
  public Counters getCounters() {
    return counters;
  }

  /**
   * Updates the counters based on the provided message.
   *
   * <p>Increments total message count and updates counts by message type, peer UUID, thread name,
   * and field access types according to the message content.
   *
   * @param message the {@link Message} to process and update counters for
   * @throws IllegalStateException if an unexpected message type is encountered
   */
  private void updateCounters(Message message) {
    // total messages
    counters.getNumberOfMessages().getAndIncrement();

    // by msg type
    AtomicLong messageCounter = counters.getMessagesByType().get(getMessageTypeName(message));
    if (messageCounter == null) {
      counters.getMessagesByType().put(getMessageTypeName(message), new AtomicLong(1));
    } else {
      messageCounter.getAndIncrement();
    }

    // by peer
    messageCounter = counters.getMessagesFromPeer().get(getPeerUuid(message));
    if (messageCounter == null) {
      counters.getMessagesFromPeer().put(getPeerUuid(message), new AtomicLong(1));
    } else {
      messageCounter.getAndIncrement();
    }

    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage == null) {
      return;
    }

    // by thread
    messageCounter = counters.getMessagesByThread().get(execMessage.getThreadName());
    if (messageCounter == null) {
      counters.getMessagesByThread().put(execMessage.getThreadName(), new AtomicLong(1));
    } else {
      messageCounter.getAndIncrement();
    }

    String className;
    String methodName;
    String fieldName;
    String classFieldKey;
    MessageType messageType = MessageType.fromId(message.getMessageType());
    switch (messageType) {
      case EXEC_CONSTRUCTOR -> {
        String objClassKey = execMessage.getConstructorCall().getClazz().getName();
        incrementObjectsCreated(objClassKey);
      }
      case EXEC_INSTANCE_METHOD -> {
        className = execMessage.getInstanceMethodCall().getClazz().getName();
        methodName = execMessage.getInstanceMethodCall().getName();
        String classMethodKey = String.format("%s.%s()", getShortClassname(className), methodName);
        incrementMethodCalls(classMethodKey);
      }
      case EXEC_CLASS_METHOD -> {
        className = execMessage.getClassMethodCall().getClazz().getName();
        methodName = execMessage.getClassMethodCall().getName();
        String classMethodKey = String.format("%s.%s()", getShortClassname(className), methodName);
        incrementMethodCalls(classMethodKey);
      }
      case EXEC_GET_STATIC -> {
        className = execMessage.getStaticFieldGet().getClazz().getName();
        fieldName = execMessage.getStaticFieldGet().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldReads(classFieldKey);
      }
      case EXEC_GET_FIELD -> {
        className = execMessage.getInstanceFieldGet().getClazz().getName();
        fieldName = execMessage.getInstanceFieldGet().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldReads(classFieldKey);
      }
      case EXEC_PUT_STATIC -> {
        className = execMessage.getStaticFieldPut().getClazz().getName();
        fieldName = execMessage.getStaticFieldPut().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldWrites(classFieldKey);
      }
      case EXEC_PUT_FIELD -> {
        className = execMessage.getInstanceFieldPut().getClazz().getName();
        fieldName = execMessage.getInstanceFieldPut().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldWrites(classFieldKey);
      }
      default -> throw new IllegalStateException("Unexpected type: " + messageType);
    }
  }

  /**
   * Increments the count of objects created for a specific class.
   *
   * @param key the class name key representing the object type
   */
  private void incrementObjectsCreated(String key) {
    AtomicLong counter = counters.getObjectsCreated().get(key);
    if (counter == null) {
      counters.getObjectsCreated().put(key, new AtomicLong(1));
    } else {
      counter.getAndIncrement();
    }
  }

  /**
   * Increments the count of method calls for a specific method.
   *
   * @param key the key representing the method (e.g., "ClassName.methodName()")
   */
  private void incrementMethodCalls(String key) {
    AtomicLong counter = counters.getMethodsCalled().get(key);
    if (counter == null) {
      counters.getMethodsCalled().put(key, new AtomicLong(1));
    } else {
      counter.getAndIncrement();
    }
  }

  /**
   * Increments the count of field read accesses for a specific field.
   *
   * @param key the key representing the field (e.g., "ClassName.fieldName")
   */
  private void incrementFieldReads(String key) {
    AtomicLong counter = counters.getFieldReads().get(key);
    if (counter == null) {
      counters.getFieldReads().put(key, new AtomicLong(1));
    } else {
      counter.getAndIncrement();
    }
  }

  /**
   * Increments the count of field write accesses for a specific field.
   *
   * @param key the key representing the field (e.g., "ClassName.fieldName")
   */
  private void incrementFieldWrites(String key) {
    AtomicLong counter = counters.getFieldWrites().get(key);
    if (counter == null) {
      counters.getFieldWrites().put(key, new AtomicLong(1));
    } else {
      counter.getAndIncrement();
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
  private Integer logMessageStreamStats() {

    Objects.requireNonNull(bootstrapServers, "bootstrap servers required");

    /*
    1. CONFIGURE STREAMS API
    */
    Properties props = new Properties();
    String consumerId = "printer-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s\n",
          logName, bootstrapServers, consumerId);
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();

    KStream<String, Message> stream =
        builder.<String, byte[]>stream(logName)
            .map(
                (k, v) -> {
                  Message message = new Message();
                  message.unmarshal(v, 0);
                  return new KeyValue<>(k, message);
                });

    // stream: apply filter: message types
    if (msgTypes != null) {
      stream =
          stream.filter(
              (k, m) -> {
                ExecMessage execMessage = m.getExecMessage();
                MessageType messageType = MessageType.fromId(m.getMessageType());
                return execMessage != null && msgTypes.contains(messageType.name());
              });
    }

    // stream: apply filter: from peer (uuid)
    if (fromPeer != null) {
      stream = stream.filter((k, m) -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
    }

    // process each record updating counters
    stream.foreach((key, message) -> updateCounters(message));

    /*
     3. PREPARE AND START PROCESSING
    */

    final Topology topology = builder.build();
    if (verbose) {
      System.out.println(topology.describe());
    }

    final KafkaStreams streams = new KafkaStreams(topology, props);
    // attach shutdown handler to catch control-c
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                streams.close();
                if (continuousPrinter != null) {
                  continuousPrinter.setDone(true);
                }
                shutdownLatch.countDown();
              }
            });

    startStreams(streams, counters);
    try {
      shutdownLatch.await();
    } catch (Throwable e) {
      logger.error("Uncaught error processing stream", e);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      return 1;
    }
    return 0;
  }

  /**
   * Processes and aggregates statistics from socket-based message streams.
   *
   * <p>Connects to a message streamer via socket, applies necessary filters, and updates the
   * counters accordingly. Initializes and manages the streaming and printing threads.
   *
   * @return exit code indicating success (0) or failure (1)
   * @throws Exception if an error occurs during stream processing
   */
  private Integer socketMessageStreamStats() throws Exception {
    Objects.requireNonNull(palDirAddress, "palDirAddress required");

    ExecutorService executor =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler(
                  (t, e) -> {
                    logger.error("Uncaught exception", e);
                    if (!externalPrinting) {
                      //noinspection CallToPrintStackTrace
                      e.printStackTrace();
                    }
                  });
              return thread;
            });

    if (peerAddress == null) { // peerUuid must be present then
      String palDirectoryUrl =
          palDirAddress != null ? palDirAddress : getProperty("pal_directory", null);
      try (PalDirectory palDirectory = new PalDirectory(palDirectoryUrl)) {
        peerAddress = palDirectory.getPeerInfo(peerUuid).getPubAddress();
      }
    }

    // parse address
    String hostAndPort =
        peerAddress.contains("://") ? stringAfter(peerAddress, "://") : peerAddress;
    String host = stringBefore(hostAndPort, ":");
    int port = Integer.parseInt(stringAfter(hostAndPort, ":"));

    // stream processing
    final MessageStreamer streamer = new MessageStreamer(host, port).connect();
    Runnable streamerThread =
        () -> {
          Stream<Message> stream = streamer.getStream();
          // process each record updating counters
          stream.forEach(this::updateCounters);
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

    // start consuming
    try {
      executor.execute(streamerThread);
      logger.info("Stream started");
      if (!externalPrinting) {
        continuousPrinter = new ContinuousPrinter(counters, jsonOutput, 1);
        executor.execute(continuousPrinter);
        logger.info("Printer started");
      }
      latch.await();
      logger.info("Shutting down");
      if (continuousPrinter != null) {
        continuousPrinter.setDone(true);
      }
      executor.shutdownNow();
      streamer.close();
    } catch (Throwable e) {
      logger.error("Uncaught error", e);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      return 1;
    }
    return 0;
  }

  /**
   * Executes the message statistics collection.
   *
   * <p>Determines the streaming collection method based on provided options and initiates the
   * appropriate processing logic.
   *
   * @return exit code indicating success (0) or failure (1)
   * @throws Exception if an error occurs during execution
   */
  @Override
  public Integer call() throws Exception {
    if (peerAddress != null || peerUuid != null) {
      return socketMessageStreamStats();
    } else if (logName != null) {
      return logMessageStreamStats();
    } else {
      throw new RuntimeException(
          "Either -log (for log streaming) or -pu/-pa (for socket streaming) is required");
    }
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
    // set state listener
    streams.setStateListener(
        (newState, oldState) -> {
          if (stateFuture.isDone()) {
            return;
          }

          if (newState == KafkaStreams.State.RUNNING || newState == KafkaStreams.State.ERROR) {
            stateFuture.complete(newState);
          }
        });

    // catch unhandled exceptions
    streams.setUncaughtExceptionHandler(
        throwable -> {
          logger.error("Uncaught exception in stream. Will shutdown client.", throwable);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

    // start consuming the stream
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
      logger.error("Error processing stream", ex);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        ex.printStackTrace();
      }
    }
  }
}
