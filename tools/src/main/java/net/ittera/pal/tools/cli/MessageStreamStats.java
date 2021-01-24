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

import com.google.protobuf.InvalidProtocolBufferException;
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
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.MessageStreamer;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.KafkaExecMessageSerde;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import net.ittera.pal.tools.AbstractTool;
import net.ittera.pal.tools.stats.ContinuousPrinter;
import net.ittera.pal.tools.stats.Counters;
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

/** TODO This class and MessageStreamPrinter should inherit from base class w/ common logic */
@Command(name = "stats")
public class MessageStreamStats extends AbstractTool implements Callable<Integer> {

  private final Logger logger = LoggerFactory.getLogger(MessageStreamStats.class);

  private final Counters counters = new Counters();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);
  private ContinuousPrinter continuousPrinter;
  private boolean externalPrinting = false;

  @Option(
      names = {"-b", "--bootstrap-servers"},
      required = true,
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
      paramLabel = "PAL_DIR_ADDRESS",
      defaultValue = "localhost:2181",
      description = "address of PAL directory (default: ${DEFAULT-VALUE})")
  private String palDirAddress;

  @Option(
      names = {"-t", "--types"},
      arity = "0..*",
      description =
          "type(s) of messages to filter by ("
              +
              /*
              Msg types in wrappers.proto (peer-serdes) TODO: have option to list them from Enum, instead of hardcode them
              */
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

  @Option(
      names = {"-j", "--json-output"},
      description = "print stats as JSON")
  private boolean jsonOutput;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  protected boolean helpRequested = false;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  /**
   * Use this constructor to run this class from another class, not the cmd-line (i.e. from Seer
   * app)
   *
   * @param bootstrapServers
   * @param logName
   */
  public MessageStreamStats(String bootstrapServers, String logName) {
    this(bootstrapServers, logName, null, null, null);
  }

  /**
   * Use this constructor to run this class from another class, not the cmd-line (i.e. from Seer
   * app) For log-based streams
   *
   * @param bootstrapServers
   * @param logName
   * @param msgTypes
   * @param fromPeer
   * @param threadName
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
   * Use this constructor to run this class from another class, not the cmd-line (i.e. from Seer
   * app) For socket-based streams
   *
   * @param palDirAddress
   * @param peerUuid
   * @param peerAddress
   * @param msgTypes
   * @param fromPeer
   * @param threadName
   */
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

  /** For use when running as a Picocli command (ie. from the cmd-line) */
  MessageStreamStats() {}

  public static void main(String[] args) {
    int exitCode = new CommandLine(new MessageStreamStats()).execute(args);
    System.exit(exitCode);
  }

  private String getShortClassname(String classname) {
    String[] classnameParts = classname.split("\\.");
    return classnameParts.length > 0 ? classnameParts[classnameParts.length - 1] : classname;
  }

  public Counters getCounters() {
    return counters;
  }

  private void updateCounters(Message message) {
    // total msgs
    counters.getNumberOfMessages().getAndIncrement();

    // by msg type
    AtomicLong cntr = counters.getMessagesByType().get(getMessageType(message));
    if (cntr == null) {
      counters.getMessagesByType().put(getMessageType(message), new AtomicLong(1));
    } else {
      cntr.getAndIncrement();
    }

    // by peer
    cntr = counters.getMessagesFromPeer().get(getPeerUuid(message));
    if (cntr == null) {
      counters.getMessagesFromPeer().put(getPeerUuid(message), new AtomicLong(1));
    } else {
      cntr.getAndIncrement();
    }

    // by thread
    if (message.hasExecMessage()) {
      final ExecMessage execMessage = message.getExecMessage();
      cntr = counters.getMessagesByThread().get(execMessage.getThreadName());
      if (cntr == null) {
        counters.getMessagesByThread().put(execMessage.getThreadName(), new AtomicLong(1));
      } else {
        cntr.getAndIncrement();
      }

      // objects created by class
      if (execMessage.hasConstructorCall()) {
        String objClass = execMessage.getConstructorCall().getClass_().getName();
        cntr = counters.getObjectsCreated().get(objClass);
        if (cntr == null) {
          counters.getObjectsCreated().put(objClass, new AtomicLong(1));
        } else {
          cntr.getAndIncrement();
        }
      }

      // methods called by class+name
      if (execMessage.hasClassMethodCall() || execMessage.hasInstanceMethodCall()) {
        String className = null, methodName = null;
        switch (execMessage.getMsgType()) {
          case INSTANCE_METHOD:
            className = execMessage.getInstanceMethodCall().getClass_().getName();
            methodName = execMessage.getInstanceMethodCall().getName();
            break;
          case CLASS_METHOD:
            className = execMessage.getClassMethodCall().getClass_().getName();
            methodName = execMessage.getClassMethodCall().getName();
            break;
        }
        String classMethodKey = String.format("%s.%s()", getShortClassname(className), methodName);
        cntr = counters.getMethodsCalled().get(classMethodKey);
        if (cntr == null) {
          counters.getMethodsCalled().put(classMethodKey, new AtomicLong(1));
        } else {
          cntr.getAndIncrement();
        }
      }

      // field reads
      if (execMessage.hasInstanceFieldGet() || execMessage.hasStaticFieldGet()) {
        String className = null, fieldName = null;
        switch (execMessage.getMsgType()) {
          case GET_STATIC:
            className = execMessage.getStaticFieldGet().getClass_().getName();
            fieldName = execMessage.getStaticFieldGet().getField().getName();
            break;
          case GET_FIELD:
            className = execMessage.getInstanceFieldGet().getClass_().getName();
            fieldName = execMessage.getInstanceFieldGet().getField().getName();
            break;
        }
        String classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        cntr = counters.getFieldReads().get(classFieldKey);
        if (cntr == null) {
          counters.getFieldReads().put(classFieldKey, new AtomicLong(1));
        } else {
          cntr.getAndIncrement();
        }
      }

      // field writes
      if (execMessage.hasInstanceFieldPut() || execMessage.hasStaticFieldPut()) {
        String className = null, fieldName = null;
        switch (execMessage.getMsgType()) {
          case PUT_STATIC:
            className = execMessage.getStaticFieldPut().getClass_().getName();
            fieldName = execMessage.getStaticFieldPut().getField().getName();
            break;
          case PUT_FIELD:
            className = execMessage.getInstanceFieldPut().getClass_().getName();
            fieldName = execMessage.getInstanceFieldPut().getField().getName();
            break;
        }
        String classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        cntr = counters.getFieldWrites().get(classFieldKey);
        if (cntr == null) {
          counters.getFieldWrites().put(classFieldKey, new AtomicLong(1));
        } else {
          cntr.getAndIncrement();
        }
      }
    }
  }

  private Integer logMessageStreamStats() {

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
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaExecMessageSerde.class);

    /*
     2. DEFINE PROCESSING TOPOLOGY
    */
    final StreamsBuilder builder = new StreamsBuilder();

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

  private Integer socketMessageStreamStats() throws Exception {
    Objects.requireNonNull(palDirAddress, "palDirAddress required");

    ExecutorService executor = getExecutor(2);

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
      executor.submit(streamerThread);
      logger.info("Stream started");
      if (!externalPrinting) {
        continuousPrinter = new ContinuousPrinter(counters, jsonOutput, 1);
        executor.submit(continuousPrinter);
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
      return 1;
    }
    return 0;
  }

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

  public void stopStreams() {
    shutdownLatch.countDown();
  }

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
        (Thread thread, Throwable throwable) -> {
          throwable.printStackTrace();
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
      ex.printStackTrace();
    }
  }
}
