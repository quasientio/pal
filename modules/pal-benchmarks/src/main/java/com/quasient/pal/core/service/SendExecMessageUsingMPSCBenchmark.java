/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

package com.quasient.pal.core.service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.core.intercept.InterceptMatcher;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.internal.concurrent.MpscKind;
import com.quasient.pal.core.runtime.session.SessionService;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.core.transport.gateway.OutboundMessageGatewayStats;
import com.quasient.pal.core.transport.kafka.LogWriter;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherStats;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jctools.queues.MessagePassingQueue;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micro-benchmark for the *hot* sendExecMessage path:
 * <pre>
 *  producers  → walQueue → LogWriter  (Kafka)
 *             ↘ pubQueue → MessagePublisher (ZMQ)
 * </pre>
 *
 * The benchmark:
 *   • builds a Guice Injector with PeerWiring<br>
 *   • starts LogWriter + MessagePublisher via ServiceManager<br>
 *   • repeatedly calls gateway.sendExecMessage(...)
 * <p>
 * Measure throughput (ops/ms) with different producer counts.
 * <p>
 * Run with t=4 threads:
 * <pre>
 * >> start_kafka_docker.sh  # or start_kafka
 *
 * # only full hot path
 * >> java -Dpeer.logging=$PAL_HOME/config/peer-logging.xml \
 *   -jar target/pal-benchmarks-1.0.0-SNAPSHOT.jar \
 *   SendExecMessageUsingMPSCBenchmark -p variant=WAL_PUB_INTERCEPTS -t 4
 *
 *
 * # compare INTERCEPTS vs FULL
 * >> java -Dpeer.logging=$PAL_HOME/config/peer-logging.xml \
 *   -jar target/pal-benchmarks-1.0.0-SNAPSHOT.jar \
 *   SendExecMessageUsingMPSCBenchmark -p variant=INTERCEPTS,WAL_PUB_INTERCEPTS
 * </pre>
 */

@Fork(value = 3, jvmArgsAppend = { "-Xms2g", "-Xmx2g" })
@Warmup(iterations = 6,   time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 1, timeUnit = TimeUnit.MINUTES)
@State(Scope.Benchmark)
public class SendExecMessageUsingMPSCBenchmark {

  /** Path to the default logging configuration file in the classpath. */
  private static final String LOGGING_CONFIG = "/peer-logging-fallback.xml";

  /** Zmq socket endpoint to synchronize service readiness .*/
  private static final String SYNC_READY_ENDPOINT = "inproc://sync_ready";

  /** Kafka bootstrap servers. */
  private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:29092";

  /** WAL name - (i.e. kafka WAL topic). */
  private static final String WAL_LOG_NAME = "benchmark_log001";

  /** Endpoint for MessagePublisher's Zmq PUB. */
//  private static final String PUB_ENDPOINT = "inproc://pub";
  private static final String PUB_ENDPOINT = "tcp://localhost:8788";

  /** Whether we should register a dummy SUB socket. */
  private static final boolean WITH_DUMMY_SUB = false;

  /** Default value for Pub queue initial size - bounded types. */
  private static final int PUB_QUEUE_INITIAL_SIZE = 1 << 14;  // 16384

  /** Default value for Pub queue max capacity/size - bounded types. */
  private static final int PUB_QUEUE_MAX_SIZE = 1 << 20;  // 1M

  /** Default value for Pub queue chunk size - unbounded type. */
  private static final int PUB_QUEUE_CHUNK_SIZE = 1 << 13;  // 8192


  // ----------------------- Tunables from the command line -----------------
  /** Unused (otherwise, rename to parallelThreads). Parallel producer threads JMH should use.  Set with -t <n> on CLI. */
  @Param({"1"})
  public int unused;                 // (value unused; @Threads controls parallelism)

  /** Variant of call to sendExecMessage, parameterizes variations of RunOptions. */
  @Param({"NOOP", "INTERCEPTS", "PUB", "WAL", "PUB_WAL", "INTERCEPTS_PUB", "INTERCEPTS_WAL", "INTERCEPTS_PUB_WAL"})
  public ExecMessageCallVariant variant;

  /** Pub Queue type: FIXED, CHUNKED, GROWABLE, UNBOUNDED */
  @Param({"FIXED", "CHUNKED", "GROWABLE", "UNBOUNDED"})
  public MpscKind pubQueueType;

  // ----------------------- Dependency-injected runtime --------------------

  /** Shared Zmq context. */
  private ZContext               zmqCtx;

  /** Inproc socket used for synchronizing managed services startup. */
  private Socket                 syncSocket;

  /** Manager for all required services. */
  private ServiceManager         serviceManager;

  /** The class under test. */
  private OutboundMessageGateway gateway;

  /** Handle to the injected MessagePublisher. */
  private MessagePublisher messagePublisher;

  /** MPSC queue for WAL .*/
  private MessagePassingQueue<OutboundMsg> walQueue;

  /** MPSC queue for PUB .*/
  private MessagePassingQueue<OutboundMsg> pubQueue;

  /** Custom classloader. */
  private CustomClassloader customClassloader;

  /** Handle to the MessagePublisher's config. */
  private MessagePublisherConfig messagePublisherConfig;

  // ----------------------- Other required vars --------------------

  /** Handle to the Guice's Injector. */
  private Injector injector;

  /** Handle to the MessagePublisher PUB socket. */
  private Socket pubSocket;

  /** Pool of pre-built messages to use during the bench. */
  private List<Message>      msgPool;

  /** Counts how many times the benchmark method is called. */
  private final AtomicLong callsMade = new AtomicLong();

  /** Counter to index the {@code msgPool}. */
  private int                    next;

  /** Dummy subscriber socket to attach when using PUB. */
  private Socket dummySubSocket;

  /** Number of messages received by dummy subscriber .*/
  private long dummyRcvs;

  /** Dummy subscriber's thread for continuous recv(). */
  private Thread                 dummySubThread;

  /** Flag to let the dummy thread know it's done. */
  private volatile boolean stopDummy;


  /**
   * Set up the benchmark run.
   */
  @Setup(Level.Trial)
  public void setUp() throws IllegalAccessException, InterruptedException {

    initLogging();

    /* 1 - prepare properties for PeerWiring */
    Properties props = new Properties();

    // peer ID
    props.setProperty("id",  UUID.randomUUID().toString());

    // ZMQ socket endpoints
    props.setProperty("sync.ready", SYNC_READY_ENDPOINT);  // used by all services
    props.setProperty("offset.pub",   "inproc://offsets");  // used by LogWriter
    props.setProperty("out.pub",      PUB_ENDPOINT);  // used by MessagePublisher
    // props.setProperty("out.pub",      "inproc://pub");  -- OR this to emulate no --pub
    props.setProperty("session.svc", "inproc://session");  // used by SessionService
    props.setProperty("intercepts.reg", "inproc://intercept_reg");  // used by InterceptMatcher

    // LogWriter params
    props.setProperty("key.serializer",   "com.quasient.pal.serdes.kafka.KafkaKeySerializer");
    props.setProperty("value.serializer", "com.quasient.pal.serdes.kafka.KafkaMessageSerializer");
    LogInfo writeAheadLog = new LogInfo(WAL_LOG_NAME, KAFKA_BOOTSTRAP_SERVERS);

    // WAL queue params
    props.setProperty("wal.queue.initial", "1024");
    props.setProperty("wal.queue.max",     "1048576");
    props.setProperty("wal.queue.chunk", "1024");
    props.setProperty("wal.queue.unbounded", "false");  // set to 'true' for unbounded queue

    // PUB queue params
    props.setProperty("pub.queue.type", pubQueueType.name());
    switch (pubQueueType) {
      case FIXED, CHUNKED, GROWABLE -> {
        props.setProperty("pub.queue.initial", String.valueOf(PUB_QUEUE_INITIAL_SIZE));  // 1L << 13
        props.setProperty("pub.queue.max", String.valueOf(PUB_QUEUE_MAX_SIZE));  // 1L << 24
      }
      case UNBOUNDED ->
              props.setProperty("pub.queue.chunk", String.valueOf(PUB_QUEUE_CHUNK_SIZE));  // set for type=unbounded
    }

    // MessagePublisher params
    props.setProperty("publisher.spsc_size", "524288");   // 1 << 19 (half the pub queue size)
    props.setProperty("publisher.batch_size", "4096");
    props.setProperty("publisher.flush_on_close", "true");
    props.setProperty("publisher.zmq.linger", "0");
    props.setProperty("publisher.zmq.send_timeout", "0");
    props.setProperty("publisher.zmq.send_hwm", "10000");
    props.setProperty("publisher.drop.policy", "DROP_OLD");
    props.setProperty("publisher.drop.hwm_pct", "97");
    props.setProperty("publisher.drop.keep_pct", "92");

    // Required by other classes (execution.java/*)
    props.setProperty("messages.with_src_context", "false");
    props.setProperty("rpc.allow_nonpublic", "false");
    props.setProperty("paldir_url", PalDirectory.NO_URL);

    // -------------------------------------------------------------------

    // set up runOpts based on the selected ExecMessageCallVariant
    EnumSet<RunOptions> runOpts = EnumSet.noneOf(RunOptions.class);
    switch (variant) {
      case INTERCEPTS ->
              runOpts.add(RunOptions.WITH_INTERCEPTS);
      case PUB ->
              runOpts.add(RunOptions.WITH_TCP_PUB);
      case WAL ->
              runOpts.add(RunOptions.WITH_WAL);
      case PUB_WAL -> {
              runOpts.add(RunOptions.WITH_TCP_PUB);
              runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
      }
      case INTERCEPTS_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
        runOpts.add(RunOptions.WITH_WAL);
      }
    }

    // initialize ZMQ context   - shared across services
    initZmqContextAndGetReady();

    // if PUB, attach a dummy subscriber
    if (WITH_DUMMY_SUB && runOpts.contains(RunOptions.WITH_TCP_PUB)) {

      dummySubSocket = zmqCtx.createSocket(SocketType.SUB);
      dummySubSocket.setRcvHWM(500_000);
      dummySubSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
      dummySubSocket.connect(PUB_ENDPOINT);

      // one–slot poller,  0 = dummySub
      ZMQ.Poller poller = zmqCtx.createPoller(1);
      poller.register(dummySubSocket, ZMQ.Poller.POLLIN);

      dummySubThread = new Thread(() -> {
        while (!stopDummy && !Thread.currentThread().isInterrupted()) {

          // wait max 10 ms for the first frame of a message
          if (poller.poll(10) == 0) {
            Thread.onSpinWait();          // nothing yet
            continue;
          }

          if (poller.pollin(0)) {           // data ready
            try {
              OutboundMsg unused = OutboundMsg.receive(dummySubSocket, true);
              dummyRcvs++;
            } catch (Exception ignore) {
              // ignore malformed / partially received messages – rare on inproc
            }
          }
        }
      }, "dummy-sub");
      dummySubThread.setDaemon(true);     // JVM can exit even if we forget to stop it
      dummySubThread.start();
      Thread.sleep(200);  // give time for the handshake once publisher starts
    }

    // create CustomClassLoader (with no URL's in path)
    customClassloader = new CustomClassloader(new URL[]{}, Thread.currentThread().getContextClassLoader());

    // create DI injector
    injector = Guice.createInjector(new PeerWiring(props, runOpts, zmqCtx, customClassloader));

    // 2 - start consumer services (WAL, PUB, Sessions)

    // collect handles of required services
    messagePublisher = injector.getInstance(MessagePublisher.class);

    final Set<Service> services = new HashSet<>();
    services.add(injector.getInstance(InterceptMatcher.class));
    services.add(injector.getInstance(SessionService.class));
    services.add(messagePublisher);
    LogWriter walWriter = injector.getInstance(LogWriter.class);
    services.add(walWriter);
    serviceManager = new ServiceManager(services);

    pubSocket = (ZMQ.Socket) FieldUtils.readField(messagePublisher, "pubSocket", true);

    // tell LogWriter which log to write
    walWriter.writeToLog(writeAheadLog, true);

    // start all services
    serviceManager.startAsync();

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

    // wait for all services up
    serviceManager.awaitHealthy();

    // 3 - collect injected handles required in benchmark
    gateway     = injector.getInstance(OutboundMessageGateway.class);
    walQueue = injector.getInstance(Key.get(new TypeLiteral<>() {}, Names.named("wal_queue")));
    pubQueue = injector.getInstance(Key.get(new TypeLiteral<>() {}, Names.named("pub_queue")));
    messagePublisherConfig = injector.getInstance(MessagePublisherConfig.class);

    // 4 - prepare a pool of ExecMessages we’ll reuse
    createMixedSizedMessagePool();
  }

  /**
   * Creates a pool of serialized ExecMessages of different sizes and content.
   * Size doesn't affect queueing in the PUB or WAL queues but affects serialization
   * inside MessagePublisher (through PUB socket) and LogWriter (through Kafka).
   * <p>
   * We create 1024 msg's with a realistic workload distribution of:
   * <ul>
   * <li>40% micro (409) - 208 bytes/msg</li>
   * <li>35% small (358) - 690 bytes/msg</li>
   * <li>20% medium (205) - 1232 bytes/msg</li>
   * <li>5% large (52) - 2292 bytes/msg</li>
   *</ul>
   */
  private void createMixedSizedMessagePool() {
    MessageBuilder builder = injector.getInstance(MessageBuilder.class);
    UUID peerId = injector.getInstance(UUID.class);

    /*  ------- create a batch of micro messages --------  */
    int microMsgsN = 409;
    List<Message> microMsgs = new ArrayList<>(microMsgsN);
    for (int i = 0; i < microMsgsN; i++) {
      ExecMessage e = builder.buildEmptyConstructor(peerId, "java.lang.String");
      microMsgs.add(builder.wrap(e));
    }
    System.out.println();
    System.out.printf("%d micro messages of size: %d bytes%n", microMsgsN, ColferUtils.toBytes(microMsgs.get(0)).length);

    /*  ------- create a batch of small messages (small Lorem) --------  */
    int smallMsgsN = 358;
    List<Message> smallMsgs = new ArrayList<>(smallMsgsN);
    for (int i = 0; i < smallMsgsN; i++) {
      ExecMessage e = builder.buildConstructor(
        peerId,
        "java.lang.String",
              new String[]{"java.lang.String"},
              new Object[]{shortLorem},
              this,
              null);
      smallMsgs.add(builder.wrap(e));
    }
    System.out.printf("%d small messages of size: %d bytes%n", smallMsgsN, ColferUtils.toBytes(smallMsgs.get(0)).length);

    /*  ------- create a batch of large messages (list of 100 doubles) --------  */

    int mediumMsgsN = 205;
    List<Message> mediumMsgs = new ArrayList<>(mediumMsgsN);

    // list instance with 50 doubles
    List<Double> doubleList = new ArrayList<>();
    Random random = new Random();
    for (int i = 0; i < 50; i++) {
      double randomValue = random.nextDouble(); // Generates a value between 0.0 and 1.0
      doubleList.add(randomValue);
    }

    // create the messages
    for (int i = 0; i < mediumMsgsN; i++) {
      ExecMessage e = builder.buildClassMethod(
              peerId,
              "some.math.like.UtilityClass",
              "AFancyMethod",
      new String[]{doubleList.getClass().getName()},
              this,
              null,
              new Object[]{doubleList});

      mediumMsgs.add(builder.wrap(e));
    }
    System.out.printf("%d medium messages of size: %d bytes%n", mediumMsgsN, ColferUtils.toBytes(mediumMsgs.get(0)).length);

    /*  ------- create a batch of large messages (long Lorem) --------  */
    int largeMsgsM = 52;
    List<Message> largeMsgs = new ArrayList<>(largeMsgsM);
    for (int i = 0; i < largeMsgsM; i++) {
      ExecMessage e = builder.buildConstructor(
              peerId,
              "java.lang.String",
              new String[]{"java.lang.String"},
              new Object[]{longLorem},
              this,
              null);
      largeMsgs.add(builder.wrap(e));
    }
    System.out.printf("%d large messages of size: %d bytes%n", largeMsgsM, ColferUtils.toBytes(largeMsgs.get(0)).length);

    msgPool = new ArrayList<>(microMsgs.size() + smallMsgs.size() + mediumMsgs.size() + largeMsgs.size());
    msgPool.addAll(microMsgs);
    msgPool.addAll(smallMsgs);
    msgPool.addAll(mediumMsgs);
    msgPool.addAll(largeMsgs);
  }

  /**
   * Tear down the benchmark run.
   */
  @TearDown(Level.Trial)
  public void tearDown() throws InterruptedException {

    /* -------------------------------------------------
     * Let the publisher flush everything it still has
     * ------------------------------------------------- */


    // wait until the app’s own queue is empty
    while (!pubQueue.isEmpty()) {
      Thread.onSpinWait();
    }

    // wait until the PUB socket itself reports “ready for output”
    //    (= everything already handed to ØMQ)
    while (!pubFlushed(pubSocket)) {
      Thread.onSpinWait();
    }

    // stop the dummy subscriber *first* so it cannot race with ctx.close()
    if (WITH_DUMMY_SUB && dummySubThread != null) {
      stopDummy = true;              // let the loop exit
      dummySubThread.interrupt();    // break a blocking recv()
      dummySubSocket.close();
      dummySubThread.join();
    }

    // now it is safe to shut everything else down
    if (serviceManager != null) {
      serviceManager.stopAsync().awaitStopped();
    }

    zmqCtx.close();

    /* -------------------------------------------------
     * Print the parameters / config, to keep track
     * ------------------------------------------------- */
    System.out.println();
    System.out.println("----- CONFIGURATION / PARAMETERS -----");
    System.out.printf("PUB queue initial size (bounded types): %d%n", PUB_QUEUE_INITIAL_SIZE);
    System.out.printf("PUB queue max size (bounded types): %d%n", PUB_QUEUE_MAX_SIZE);
    System.out.printf("PUB queue chunk size (unbounded types): %d%n", PUB_QUEUE_CHUNK_SIZE);
    System.out.printf("No dummy sub (0 subscribers)%n");
    System.out.println(messagePublisherConfig.toString());

    /* -------------------------------------------------
     * Print the run statistics
     * ------------------------------------------------- */
    System.out.println();
    MessagePublisherStats publisherStats = messagePublisher.getEndStats();

    System.out.println("----- Publisher counters -----");
    System.out.printf("received  : %,d%n", publisherStats.messagesReceived());
    System.out.printf("published : %,d%n", publisherStats.messagesPublished());
    System.out.printf("dropped new (SPSC)      : %,d%n", publisherStats.messagesDroppedUnforwarded());
    System.out.printf("dropped old (SPSC)      : %,d%n", publisherStats.messagesDroppedEvicted());
    System.out.printf("dropped(send fail) : %,d%n", publisherStats.messagesDroppedPubFail());
    System.out.printf("dropped(socket err): %,d%n", publisherStats.messagesDroppedSocketErr());
    System.out.printf("left in SPSC       : %,d%n", publisherStats.messagesInSpsc());
    System.out.println("------------------------------");

    System.out.println();
    System.out.println("------ Benchmark Stats -------");
    System.out.printf("hotPath() called %d times%n", callsMade.get());
    if (WITH_DUMMY_SUB) {
      System.out.printf("Dummy subscriber received %d messages%n", dummyRcvs);
    }
    if (pubQueue instanceof HwmMessageQueue){
      System.out.printf("Peak PUB queue depth (i.e. HWM): %,d%n", ((HwmMessageQueue<?>) pubQueue).highWaterMark());
    }
    System.out.println("------------------------------");

    System.out.println();
    System.out.println("--- MessageGateway stats ---");
    OutboundMessageGatewayStats gatewayStats  = OutboundMessageGateway.getStats();
    System.out.printf("Dropped %d messages due to PUB queue congestion%n", gatewayStats.messagesDroppedPub());
    System.out.println("----------------------------");

    /* -------------------------------------------------
     * House-keeping
     * ------------------------------------------------- */
    walQueue.clear();
    pubQueue.clear();
    customClassloader.shutdown();
  }

  /**
   *  helper: returns true once the PUB socket is completely flushed
   * @param pubSocket the socket to wait on until flushed
   * @return true once the PUB socket is completely flushed
   */
  private static boolean pubFlushed(ZMQ.Socket pubSocket) {
    try {
      return pubSocket == null || (pubSocket.getEvents() & ZMQ.Poller.POLLOUT) != 0;
    } catch (Exception e) {
      return true;           // socket/context already closed ⇒ treat as flushed
    }
  }

  // ----------------------- Actual benchmark method -----------------------

  /**
   * Runs the actual benchmark logic.
   * @param bh the Blackhole instance that consumes values to prevent JIT optimizations.
   */
  @SuppressWarnings("unused")
  @Benchmark
  @Threads(Threads.MAX)   // let JMH spawn threads per @Param / CLI -t
  public void hotPath(Blackhole bh) {
    // pick a pre-created ExecMessage (avoids allocation noise)
    Message m = msgPool.get(next & 1023);   // cheap modulo
    next++;

    ExecMessage ret = gateway.sendExecMessage(m, ExecPhase.BEFORE);
    bh.consume(ret);
    callsMade.incrementAndGet();
  }

  // ----------------------- Initialization helpers -----------------------

  /**
   * Initializes and configures the logging system using Logback.
   *
   * <p>If a system property "peer.logging" is set and points to an existing file, that
   * configuration is used. Otherwise, the default logging configuration resource is loaded.
   */
  private void initLogging() {
    // configure logging
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    context.reset();

    // look for a property named peer.logging in the System properties
    final String palLogging = System.getProperty("peer.logging");
    if (palLogging != null && !palLogging.trim().isEmpty()) {
      boolean givenFileExists = false;
      try {
        if (Files.exists(Paths.get(palLogging))) {
          givenFileExists = true;
        }
      } catch (Exception ex) {
        ex.printStackTrace(System.err);
      }
      if (givenFileExists) {
        try {
          configurator.doConfigure(palLogging);
        } catch (Exception ex) {
          System.err.printf("Error loading logging configuration from %s%n", palLogging);
          // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
          //noinspection CallToPrintStackTrace
          ex.printStackTrace();
        }
        return;
      }
    }

    // fall back to our default logging configuration
    try (final InputStream stream = Main.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (Exception ex) {
      System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
      // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
      //noinspection CallToPrintStackTrace
      ex.printStackTrace();
    }
  }

  /**
   * Waits for a specified number of "go!" signals on the synchronization socket.
   *
   * <p>Only messages equal to "go!" (case-insensitive) are counted; any unexpected messages are
   * logged and ignored.
   *
   * @param numberOfSignals the number of "go!" signals to wait for
   */
  private void collectGoSignals(int numberOfSignals) {
    CountDownLatch latch = new CountDownLatch(numberOfSignals);
    while (latch.getCount() > 0) {
      String received = syncSocket.recvStr();
      if (received.equalsIgnoreCase("go!")) {
        latch.countDown();
      } else {
        System.err.println("ignoring unexpected msg: " + received);
      }
    }
    syncSocket.close();
  }

  /**
   * Initialize ZMQ Context and bind Sync-Ready socket.
   */
  private void initZmqContextAndGetReady() {
    zmqCtx = new ZContext();
    zmqCtx.setLinger(1000);
    zmqCtx.setRcvHWM(10000);
    zmqCtx.setSndHWM(10000);

    // start ready socket
    syncSocket = zmqCtx.createSocket(SocketType.PULL);
    syncSocket.bind(SYNC_READY_ENDPOINT);
  }

  /** Short string argument for messages. */
  private final String shortLorem = """
    Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
    Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.
    Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.
    Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
    """;

  /** Long string argument for messages. */
  private final String longLorem = """
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non risus. Suspendisse lectus tortor, dignissim sit amet,
    adipiscing nec, ultricies sed, dolor. Cras elementum ultrices diam. Maecenas ligula massa, varius a, semper congue,
    euismod non, mi. Proin porttitor, orci nec nonummy molestie, enim est eleifend mi, non fermentum diam nisl sit amet erat.
    Duis semper. Duis arcu massa, scelerisque vitae, consequat in, pretium a, enim. Pellentesque congue. Ut in risus volutpat
    libero pharetra tempor. Cras vestibulum bibendum augue. Praesent egestas leo in pede. Praesent blandit odio eu enim.
    Pellentesque sed dui ut augue blandit sodales. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere
    cubilia Curae; Aliquam nibh. Mauris ac mauris sed pede pellentesque fermentum. Maecenas adipiscing ante non diam sodales hendrerit.

    Ut velit mauris, egestas sed, gravida nec, ornare ut, mi. Aenean ut orci vel massa suscipit pulvinar. Nulla sollicitudin.
    Fusce varius, ligula non tempus aliquam, nunc turpis ullamcorper nibh, in tempus sapien eros vitae ligula. Pellentesque
    rhoncus nunc et augue. Integer id felis. Curabitur aliquet pellentesque diam. Integer quis metus vitae elit lobortis egestas.
    Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Morbi vel erat non mauris convallis vehicula. Nulla et sapien.
    Integer tortor tellus, aliquam faucibus, convallis id, congue eu, quam. Mauris ullamcorper felis vitae erat. Proin feugiat,
    augue non elementum posuere, metus purus iaculis lectus, et tristique ligula justo vitae magna.

    Aliquam convallis sollicitudin purus. Praesent aliquam, enim at fermentum mollis, ligula massa adipiscing nisl, ac euismod
    nibh nisl eu lectus. Fusce vulputate sem at sapien. Vivamus leo. Aliquam euismod libero eu enim. Nulla nec felis sed leo
    placerat imperdiet. Aenean suscipit nulla in justo. Suspendisse cursus rutrum augue. Nulla tincidunt tincidunt mi.
    Curabitur iaculis, lorem vel rhoncus faucibus, felis magna fermentum augue, et ultricies lacus lorem varius purus.
    """;
}

