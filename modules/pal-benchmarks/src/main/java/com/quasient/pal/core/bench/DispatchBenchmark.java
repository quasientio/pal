/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

package com.quasient.pal.core.bench;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.lang.reflect.MethodSignature;
import com.quasient.pal.common.lang.reflect.Params;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.DispatchForwarder;
import com.quasient.pal.core.bench.io.InputMode;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.core.intercept.InterceptMatcher;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.internal.concurrent.MpscKind;
import com.quasient.pal.core.runtime.session.SessionService;
import com.quasient.pal.core.service.Main;
import com.quasient.pal.core.service.PeerWiring;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.core.transport.gateway.MessageQueueStats;
import com.quasient.pal.core.transport.gateway.ThreadWaitSnapshot;
import com.quasient.pal.core.transport.kafka.KafkaWalWriter;
import com.quasient.pal.core.transport.WalWriterStats;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherStats;
import com.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.OutboundMsg;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.DoubleStream;

/**
 *
 * Benchmark for the *hot* dispatch path.
 *
 * <p>
 * Driven by two main params/axis: variant and ioProfile
 * Each axis answers a different question:
 * <ul>
 *   <li>variant → “Which Pal features?”</li>
 *   <li>ioProfile →  “How realistic is the transport?”</li>
 * </ul>
 *
 * <hr>
 * You can bisect a regression quickly:
 * <ul>
 *   <li>Slow only when ioProfile==REAL ⇒ broker tuning;</li>
 *   <li>slow already at MOCK ⇒ serialisation/callbacks;</li>
 *   <li>slow even at CPU_ONLY ⇒ quantiser or matcher.</li>
 * </ul>
 *
 * Variants may include networking features, like WAL and PUB
 * <pre>
 *  producers  → walQueue → KafkaWalWriter  (Kafka)
 *             ↘ pubQueue → MessagePublisher (ZMQ)
 * </pre>
 *
 * If the chosen variant includes WAL, and 'ioProfile == REAL',
 * then a Kafka instance must be running. You may use the shipped
 * script that runs Kafka on docker, or the script that launches
 * a locally installed Kafka.
 * <pre>
 * >> start_kafka_docker.sh  # or start_kafka
 *</pre>
 *
 * Use the <b>run.sh</b> script to configure and launch this benchmark.
 */

@Fork(value = 5, jvmArgsAppend = { "-Xms2g", "-Xmx2g" })
@Warmup(iterations = 4,   time = 15, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)
@State(Scope.Benchmark)
public class DispatchBenchmark {

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger("benchmark");

  /** Path to the default peer logging configuration file in the classpath. */
  private static final String LOGGING_CONFIG = "/peer-logging-fallback.xml";

  // ---- Queue Defaults -------------------------------------------------------


  /** Default initial size of the WAL queue. */
  private static final int    DEF_WAL_QUEUE_INITIAL= 16_384;    //  1 << 14

  /** Default max size of the Wal queue. */
  private static final int    DEF_WAL_QUEUE_MAX    = 1_048_576; //  1 << 20

  /** Default chunk size of the Wal queue, for growable types. */
  private static final int    DEF_WAL_QUEUE_CHUNK  = 4_096;     //  1 << 12

  /** Default initial size of the Pub queue. */
  private static final int    DEF_PUB_QUEUE_INITIAL= 16_384;    //  1 << 14

  /** Default max size of the Pub queue. */
  private static final int    DEF_PUB_QUEUE_MAX    = 1_048_576; // 1 << 20

  /** Default chunk size of the Pub queue, for growable types. */
  private static final int    DEF_PUB_QUEUE_CHUNK  = 8_192;     //  1 << 13

  // ---- MessagePublisher Defaults --------------------------------------------

  /** Default endpoint for MessagePublisher's Zmq PUB. */
  private static final String DEF_PUB_ENDPOINT = "tcp://127.0.0.1:8788";

  /** Default size for the MessagePublisher's SPSC queue. */
  private static final int    DEF_SPSC_SIZE        = 524_288;   //  1 << 19

  /** Default size for MessagePublisher's send() batch. */
  private static final int    DEF_BATCH_SIZE       = 4_096;     //  1 << 12

  /** Default flush-on-close policy (whether messages left in the SPSC queue should be flushed on close). */
  private static final String DEF_FLUSH_ON_CLOSE       = "false";

  /** Default ZMQ linger value for PUB socket. */
  private static final int DEF_ZMQ_LINGER           = 0;

  /** Default ZMQ send timeout value for PUB socket. */
  private static final int DEF_ZMQ_SEND_TIMEOUT     = 0;

  /** Default ZMQ send HWM value for PUB socket. */
  private static final int DEF_ZMQ_SEND_HWM         = 10_000;

  /** Default drop policy for messages enqueued for publishing. */
  private static final PublishingDropPolicy DEF_DROP_POLICY  = PublishingDropPolicy.DROP_OLD;

  /** Default HWM value indicating when to start trimming the queue - applies to DROP_OLD. */
  private static final int DEF_DROP_HWM_PCT         = 97;

  /** Default % of queue to keep when trimming old messages - applies to DROP_OLD. */
  private static final int DEF_DROP_KEEP_PCT        = 92;

  // ---- KafkaWalWriter Defaults --------------------------------------------

  /** Kafka bootstrap servers. */
  private static final String DEF_KAFKA_BOOTSTRAP_SERVERS = "localhost:29092";

  /** WAL name - (i.e. kafka WAL topic). */
  private static final String DEF_WAL_LOG_NAME = "benchmark_log001";

  // ---- Dispatcher Defaults --------------------------------------------

  /**
   * Default value for the "messages.with_src_context", which indicates whether to include source context details
   * in serializing messages sourced from quantization.
   */
  private static final boolean DEF_WITH_SRC_CONTEXT = false;

  /** Whether we should register a dummy SUB socket. */
  private static final boolean WITH_DUMMY_SUB = false;


  // ----------------------- Tunables from the command line -----------------
  /** Unused (otherwise, rename to parallelThreads). Parallel producer threads JMH should use.  Set with -t <n> on CLI. */
  @Param({"1"})
  public int unused;                 // (value unused; @Threads controls parallelism)

  /** Type of {@link IoProfile} to run in relation to IO (networking). */
  @Param({"CPU_ONLY", "MOCK", "REAL"})
  public IoProfile ioProfile;

  /** Variant of call to sendExecMessage, parameterizes variations of RunOptions. */
  @Param({"NOOP", "INTERCEPTS", "PUB", "WAL", "PUB_WAL", "INTERCEPTS_PUB", "INTERCEPTS_WAL", "INTERCEPTS_PUB_WAL"})
  public FeatureSetVariant variant;

  /** Four comma‑separated percentages that must add up to 100, e.g. "40,35,20,5". */
  @Param({"40:35:20:5"})
  public String sizeDistPct;

  /** Selects which {@link com.quasient.pal.core.bench.io.DispatchArgsSource} to use. */
  @Param({"ASYNC", "PRELOADED"})
  public InputMode inputMode;

  /** Pub Queue type: FIXED, CHUNKED, GROWABLE, UNBOUNDED */
  @Param({"FIXED", "CHUNKED", "GROWABLE", "UNBOUNDED"})
  public MpscKind pubQueueType;

  /** WAL Queue type: FIXED, CHUNKED, GROWABLE, UNBOUNDED */
  @Param({"FIXED", "CHUNKED", "GROWABLE", "UNBOUNDED"})
  public MpscKind walQueueType;

  // ----------------------- Dependency-injected runtime --------------------

  /** Properties for {@link PeerWiring}. */
  private final Properties props = new Properties();

  /** RunOptions set. */
  private EnumSet<RunOptions> runOpts;

  /** Shared Zmq context. */
  private ZContext               zmqCtx;

  /** Inproc socket used for synchronizing managed services startup. */
  private Socket                 syncSocket;

  /** Manager for all required services. */
  private ServiceManager         serviceManager;

  /** Handle to the injected MessagePublisher. */
  private MessagePublisher messagePublisher;

  /** Handle to the injected OutboundMessageGateway. */
  private OutboundMessageGateway messageGateway;

  /** MPSC queue for WAL .*/
  private HwmMessageQueue<OutboundMsg> walQueue;

  /** MPSC queue for PUB .*/
  private HwmMessageQueue<OutboundMsg> pubQueue;

  /** Custom classloader. */
  private CustomClassloader customClassloader;

  /** Handle to the MessagePublisher's config. */
  private MessagePublisherConfig messagePublisherConfig;

  // ----------------------- Other required vars --------------------

  /** number of worker threads in this fork */
  int jmhThreads;

  /** parsed values from {@link #sizeDistPct} for [micro, small, medium, large] */
  private int[] sizeDist;

  /** Handle to the Guice's Injector. */
  private Injector injector;

  /** Handle to the MessagePublisher PUB socket. */
  private Socket pubSocket;

  /** Counts how many times the benchmark method is called. */
  private AtomicLong callsMade;

  /** Dummy subscriber socket to attach when using PUB. */
  private Socket dummySubSocket;

  /** Number of messages received by dummy subscriber .*/
  private long dummyRcvs;

  /** Dummy subscriber's thread for continuous recv(). */
  private Thread    dummySubThread;

  /** Flag to let the dummy thread know it's done. */
  private volatile boolean stopDummy;

  /** Sequence of chars to create random chunks of text. */
  private static final char[] ALPHA = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ ".toCharArray();

  /**
   * Set up the benchmark run.
   */
  @Setup(Level.Trial)
  public void setUp(BenchmarkParams bmParams) throws IllegalAccessException, InterruptedException {

    // initialize logging
    initLogging();
    logger.debug("In setUp");

    // parse size distribution param
    parseSizeDist();

    this.jmhThreads = bmParams.getThreads();

    // reset counter to 0
    callsMade = new AtomicLong();

    // load and set the running properties
    setWiringProperties();

    // get the RunOptions based on the selected Variant
    runOpts = variant.toRunOptions();

    // assert variant <--> ioProfile compatibility
    if (ioProfile == IoProfile.CPU_ONLY &&
            (runOpts.contains(RunOptions.WITH_WAL) || runOpts.contains(RunOptions.WITH_TCP_PUB)))  {
      throw new IllegalArgumentException("CPU_ONLY profile cannot be combined with WAL/PUB variants");
    }

    if (ioProfile == IoProfile.CPU_ONLY) {
      // CPU_ONLY profile only compatible with NOOP and INTERCEPTS variants
      runOpts.remove(RunOptions.WITH_WAL);
      runOpts.remove(RunOptions.WITH_TCP_PUB);
    }

   // initialize ZMQ context   - shared across services
    initZmqContextAndGetReady();

    // if PUB, attach a dummy subscriber
    if (WITH_DUMMY_SUB && runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      initDummySubscriber();
    }

    // create CustomClassLoader (with no URL's in path)
    customClassloader = new CustomClassloader(new URL[]{}, Thread.currentThread().getContextClassLoader());

    // create DI injector
    Module brokersModule = null;
    switch (ioProfile) {
      case MOCK -> brokersModule = new MockBrokersModule();   // MockProducer + DummyPublisher
      case REAL -> brokersModule = new RealBrokersModule();   // KafkaProducer provider, etc.
      // CPU_ONLY: nothing extra
    }

    if (brokersModule == null) {
      injector = Guice.createInjector(new PeerWiring(props, runOpts, zmqCtx, customClassloader));
    } else {
      injector = Guice.createInjector(
              Modules.override(new PeerWiring(props, runOpts, zmqCtx, customClassloader))
                      .with(brokersModule));
    }

    // ----- start consumer services (WAL, PUB, Sessions), collecting required handles
    final Set<Service> services = new HashSet<>();
    services.add(injector.getInstance(InterceptMatcher.class));
    services.add(injector.getInstance(SessionService.class));

    if (runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      messagePublisher = injector.getInstance(MessagePublisher.class);
      services.add(messagePublisher);
      pubSocket = (ZMQ.Socket) FieldUtils.readField(messagePublisher, "pubSocket", true);
    }
    if (runOpts.contains(RunOptions.WITH_WAL)) {
      KafkaWalWriter walWriter = injector.getInstance(KafkaWalWriter.class);
      services.add(walWriter);
      // tell KafkaWalWriter which log to write
      LogInfo writeAheadLog = new LogInfo(DEF_WAL_LOG_NAME, props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
      walWriter.writeToLog(writeAheadLog, false);
    }
    serviceManager = new ServiceManager(services);

    // get handle to message gateway singleton
    messageGateway = injector.getInstance(OutboundMessageGateway.class);

    // start all services
    serviceManager.startAsync();

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

    // wait for all services up
    serviceManager.awaitHealthy();

    // collect injected handles required in benchmark
    walQueue = injector.getInstance(Key.get(new TypeLiteral<>() {}, Names.named("wal_queue")));
    pubQueue = injector.getInstance(Key.get(new TypeLiteral<>() {}, Names.named("pub_queue")));
    messagePublisherConfig = injector.getInstance(MessagePublisherConfig.class);

    logger.debug("setUp completed");
  }

  /**
   * Parse {@link #sizeDistPct} into {@link #sizeDist}
   *
   * @throws IllegalArgumentException if missing values or sum does not equal 100
   */
  private void parseSizeDist() {
    String[] parts = sizeDistPct.split("\\s*:\\s*");
    if (parts.length != 4) {
      throw new IllegalArgumentException("sizeDistPct must have 4 numbers");
    }
    sizeDist = new int[4];
    int sum = 0;
    for (int i = 0; i < 4; i++) {
      sizeDist[i] = Integer.parseInt(parts[i]);
      sum += sizeDist[i];
    }
    if (sum != 100) {
      throw new IllegalArgumentException("sizeDistPct values must add up to 100 (was " + sum + ')');
    }
  }

  /**
   * Sets the running properties, reading them from -D... args if available, falling back to defaults.
   */
  private void setWiringProperties() {

    // peer ID
    props.setProperty("id",  UUID.randomUUID().toString());

    // ZMQ socket endpoints
    props.setProperty("sync.ready", "inproc://sync_ready");  // used by all services
    props.setProperty("offset.pub",   "inproc://offsets");  // used by KafkaWalWriter
    props.setProperty("out.pub", System.getProperty("pub.endpoint", DEF_PUB_ENDPOINT));  // used by MessagePublisher
    props.setProperty("session.svc", "inproc://session");  // used by SessionService
    props.setProperty("intercepts.reg", "inproc://intercept_reg");  // used by InterceptMatcher

    // WAL Writer
    props.setProperty("wal.type", "kafka");

    // KafkaWalWriter params
    props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            System.getProperty("wal.kafka.bootstrap_servers", DEF_KAFKA_BOOTSTRAP_SERVERS));

    props.setProperty("wal.kafka.linger_ms", System.getProperty("wal.kafka.linger_ms", null));
    props.setProperty("wal.kafka.batch_size", System.getProperty("wal.kafka.batch_size", null));
    props.setProperty("wal.kafka.compression_type", System.getProperty("wal.kafka.compression_type", null));
    props.setProperty("wal.kafka.buffer_memory", System.getProperty("wal.kafka.buffer_memory", null));

    // WAL queue params
    props.setProperty("wal.queue.type", walQueueType.name());
    switch (walQueueType) {
      case FIXED, CHUNKED, GROWABLE -> {
        props.setProperty("wal.queue.initial", System.getProperty("wal.queue.initial", String.valueOf(DEF_WAL_QUEUE_INITIAL)));
        props.setProperty("wal.queue.max", System.getProperty("wal.queue.max", String.valueOf(DEF_WAL_QUEUE_MAX)));
      }
      case UNBOUNDED -> props.setProperty("wal.queue.chunk", System.getProperty("wal.queue.chunk", String.valueOf(DEF_WAL_QUEUE_CHUNK)));

      default -> throw new IllegalArgumentException("Unsupported wal.queue.type=" + walQueueType);
    }

    // PUB queue params
    props.setProperty("pub.queue.type", pubQueueType.name());
    switch (pubQueueType) {
      case FIXED, CHUNKED, GROWABLE -> {
        props.setProperty("pub.queue.initial", System.getProperty("pub.queue.initial", String.valueOf(DEF_PUB_QUEUE_INITIAL)));
        props.setProperty("pub.queue.max", System.getProperty("pub.queue.max", String.valueOf(DEF_PUB_QUEUE_MAX)));
      }
      case UNBOUNDED -> props.setProperty("pub.queue.chunk", System.getProperty("pub.queue.chunk", String.valueOf(DEF_PUB_QUEUE_CHUNK)));

      default -> throw new IllegalArgumentException("Unsupported pub.queue.type=" + pubQueueType);
    }

    // MessagePublisher params
    props.setProperty("pub.spsc_size",
            System.getProperty("pub.spsc_size",
                    String.valueOf(DEF_SPSC_SIZE)));

    props.setProperty("pub.batch_size",
            System.getProperty("pub.batch_size",
                    String.valueOf(DEF_BATCH_SIZE)));

    props.setProperty("pub.flush_on_close",
            System.getProperty("pub.flush_on_close",
                    DEF_FLUSH_ON_CLOSE));

    props.setProperty("pub.zmq.linger",
            System.getProperty("pub.zmq.linger",
                    String.valueOf(DEF_ZMQ_LINGER)));

    props.setProperty("pub.zmq.send_timeout",
            System.getProperty("pub.zmq.send_timeout",
                    String.valueOf(DEF_ZMQ_SEND_TIMEOUT)));

    props.setProperty("pub.zmq.send_hwm",
            System.getProperty("pub.zmq.send_hwm",
                    String.valueOf(DEF_ZMQ_SEND_HWM)));

    props.setProperty("pub.drop.policy",
            System.getProperty("pub.drop.policy",
                    DEF_DROP_POLICY.name()));

    props.setProperty("pub.drop.hwm_pct",
            System.getProperty("pub.drop.hwm_pct",
                    String.valueOf(DEF_DROP_HWM_PCT)));

    props.setProperty("pub.drop.keep_pct",
            System.getProperty("pub.drop.keep_pct",
                    String.valueOf(DEF_DROP_KEEP_PCT)));

    // Params required by other classes (execution.java/*)
    props.setProperty("messages.with_src_context",
            System.getProperty("messages.with_src_context", String.valueOf(DEF_WITH_SRC_CONTEXT)));
    props.setProperty("rpc.allow_nonpublic", "false");
    props.setProperty("paldir_url", PalDirectory.NO_URL);
  }

  /** Initializes a dummy subscriber to listen from the MessagePublisher's PUB socket. */
  private void initDummySubscriber() throws InterruptedException {
    dummySubSocket = zmqCtx.createSocket(SocketType.SUB);
    dummySubSocket.setRcvHWM(500_000);
    dummySubSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
    dummySubSocket.connect(DEF_PUB_ENDPOINT);

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


  /**
   *  Creates a random chunk of text of length between {@code minBytes} and {@code maxBytes}.
   *
   * @param minBytes minimum size in bytes
   * @param maxBytes maximum size in bytes
   * @param rnd random generator instance
   * @param prefix some text to prepend to the generated chunk
   * @return a random chunk of text with len > {@code minBytes} and < {@code maxBytes}
   */
  private static String randomText(int minBytes, int maxBytes, Random rnd, String prefix) {
    int len = rnd.nextInt(maxBytes - minBytes + 1) + minBytes + prefix.length();
    StringBuilder sb = new StringBuilder(len);
    sb.append(prefix);
    for (int i = 0; i < len; i++) {
      sb.append(ALPHA[rnd.nextInt(ALPHA.length)]);
    }
    return sb.toString();
  }

  /**
   * Using {@link #randomText}, creates a dispatchable that invokes {@link String#toUpperCase}
   * on a new String with size > {@code minBytes} and < {@code maxBytes}
   *
   * @param minBytes lower bound for String object's length in chars
   * @param maxBytes upper bound for String object's length in chars
   * @param rnd a random generator
   * @param prefix a String that will be prefixed to the generated payload, so we can identify
   *               the messages in the logs
   * @return the dispatch args
   * @throws NoSuchMethodException if declared method not found
   */
  private DispatchArgs createTextDispatchable(int minBytes, int maxBytes, Random rnd, String prefix)
          throws NoSuchMethodException {

    String payload = randomText(minBytes, maxBytes, rnd, prefix);

    Method method = String.class.getDeclaredMethod("toUpperCase");
    Context ctx = new Context(
            "DummyClassForContext.java", 0, DummyClassForContext.class,
            new MethodSignature(String.class, String.class.getName(),
                    Modifier.PUBLIC, "toUpperCase",
                    new Class[]{}, new Params(null, method.getParameterTypes(), method.getParameters()),
                    method, String.class));

    return new DispatchArgs(ctx, this, payload, new Object[]{});
  }

  /**
   * call {@link Arrays#stream(double[])} with an array of {@code size} random doubles
   *
   * @param size number of doubles in the array
   * @param rnd random generator
   * @return DispatchArgs instance
   * @throws NoSuchMethodException if the method to be invoked is not found
   */
   private DispatchArgs createArrayDispatchable(int size, Random rnd) throws NoSuchMethodException {
     double[] doubles = rnd.doubles(size).toArray();

    Class<?> clzToInvoke = Arrays.class;
    String methodName = "stream";
    Method method = clzToInvoke.getDeclaredMethod(methodName, double[].class);
    Context ctx = new Context(
            "MyClass.java",
            34,
            DummyClassForContext.class,
            new MethodSignature(
                    clzToInvoke,
                    clzToInvoke.getName(),
                    Modifier.PUBLIC | Modifier.STATIC,
                    methodName,
                    new Class[]{},
                    new Params(null, method.getParameterTypes(), method.getParameters()),
                    method,
                    DoubleStream.class)
    );

    return new DispatchArgs(ctx, this, null, new Object[]{doubles});
  }

  /**
   * Produces one {@link DispatchArgs} whose size category is chosen
   * according to {@link #sizeDist}.
   * <p>
   * Four categories of variable size are used:
   * <ol>
   *   <li>micro: 0–256B</li>
   *   <li>small: 257B–4KB</li>
   *   <li>medium: 4KB-32KB</li>
   *   <li>large: >32KB-64KB</li>
   * </ol>
   */
  public DispatchArgs randomArgsAccordingToDist(Random rnd) throws NoSuchMethodException {
    int p = rnd.nextInt(100);
    int[] w = sizeDist;   // local alias
    if (p < w[0]) {
      return createTextDispatchable(   1,   256, rnd, "micro=");
    }
    else if (p < w[0] + w[1]) {
      return createTextDispatchable( 257,  4096, rnd, "small=");
    }
    else if (p < w[0] + w[1] + w[2]) {
      int size = 200 + rnd.nextInt(1401); return createArrayDispatchable(size, rnd);
    } else {
      return createTextDispatchable(32_769, 65_536, rnd, "large=");
    }
  }

  /** Expose sizeDist */
  public int[] sizeDistribution() {
     return sizeDist;
  }

  /**
   * Tear down the benchmark run.
   */
  @TearDown(Level.Trial)
  public void tearDown() throws InterruptedException {

    /* -------------------------------------------------
     * Let the publisher flush everything it still has
     * ------------------------------------------------- */

    // wait until the PUB queue is empty
    while (!pubQueue.isEmpty()) {
      Thread.onSpinWait();
    }
    logger.debug("PUB queue empty");

    if (runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      // wait until the PUB socket itself reports “ready for output”
      //    (= everything already handed to ØMQ)
      while (!pubFlushed(pubSocket)) {
        Thread.onSpinWait();
      }
      logger.debug("PUB socket flushed");

      // stop the dummy subscriber *first* so it cannot race with ctx.close()
      if (WITH_DUMMY_SUB && dummySubThread != null) {
        stopDummy = true;              // let the loop exit
        dummySubThread.interrupt();    // break a blocking recv()
        dummySubSocket.close();
        dummySubThread.join();
        logger.debug("Dummy SUB thread finished");
      }
    }

    // now it is safe to shut everything else down

    logger.debug("Stopping services...");
    if (serviceManager != null) {
      serviceManager.stopAsync().awaitStopped();
    }
    logger.debug("All services stopped");

    zmqCtx.close();
    logger.debug("ZMQ ctx closed");

    /* -------------------------------------------------
     * Print the parameters / config, to keep track
     * ------------------------------------------------- */
    System.out.println();
    System.out.println("----- CONFIGURATION / PARAMETERS -----");
    if (runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      if (WITH_DUMMY_SUB) {
        System.out.println("1 dummy subscriber");
      } else {
        System.out.println("No dummy subscribers");
      }
      System.out.println(messagePublisherConfig.toString());
    }

    /* -------------------------------------------------
     * Print the run statistics
     * ------------------------------------------------- */
    System.out.println();
    if (runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      MessagePublisherStats publisherStats = messagePublisher.getEndStats();

      System.out.println("-----------------------------");
      System.out.println("-----  Publisher stats  -----");
      System.out.println("-----------------------------");
      System.out.printf("received  : %,d%n", publisherStats.messagesReceived());
      System.out.printf("published : %,d%n", publisherStats.messagesPublished());
      System.out.printf("dropped new (SPSC)      : %,d%n", publisherStats.messagesDroppedUnforwarded());
      System.out.printf("dropped old (SPSC)      : %,d%n", publisherStats.messagesDroppedEvicted());
      System.out.printf("dropped(send fail) : %,d%n", publisherStats.messagesDroppedPubFail());
      System.out.printf("dropped(socket err): %,d%n", publisherStats.messagesDroppedSocketErr());
      System.out.printf("left in SPSC       : %,d%n", publisherStats.messagesInSpsc());
      System.out.println();
    }

    if (runOpts.contains(RunOptions.WITH_WAL)) {
      KafkaWalWriter walWriter = injector.getInstance(KafkaWalWriter.class);
      WalWriterStats walWriterStats = walWriter.getLiveStats();

      System.out.println("-----------------------------");
      System.out.println("-----  WALWriter stats  -----");
      System.out.println("-----------------------------");
      System.out.printf("received  : %,d%n", walWriterStats.messagesReceived());
      System.out.printf("written   : %,d%n", walWriterStats.messagesWritten());
      System.out.printf("in-flight : %,d%n", walWriterStats.messagesInFlight());
      System.out.printf("error     : %,d%n", walWriterStats.messagesDroppedError());
      System.out.println();
    }

    System.out.println("-----------------------------");
    System.out.println("-----  Benchmark stats  -----");
    System.out.println("-----------------------------");
    System.out.printf("hotPath() called %d times%n", callsMade.get());
    if (runOpts.contains(RunOptions.WITH_TCP_PUB) && WITH_DUMMY_SUB) {
      System.out.printf("Dummy subscriber received %d messages%n", dummyRcvs);
    }
    System.out.println();

    if (runOpts.contains(RunOptions.WITH_TCP_PUB)) {
      System.out.println("-----------------------------");
      System.out.println("-----  PUB queue stats  -----");
      System.out.println("-----------------------------");
      System.out.printf("Peak PUB queue depth (i.e. HWM): %,d%n", pubQueue.highWaterMark());
      MessageQueueStats s = messageGateway.getPubQueueStats();
      long parkedUs = TimeUnit.NANOSECONDS.toMicros(s.totalParkedNanos());

      System.out.printf("PUB: dropped=%d parks=%d parked=%dµs failedOffers=%d%n",
              s.messagesDropped(), s.totalParks(), parkedUs, s.totalFailedOffers());
      System.out.println();

      for (ThreadWaitSnapshot tws : s.perThread()) {
        System.out.printf("  T[%d:%s] parks=%d parked=%dµs failedOffers=%d%n",
                tws.threadId(), tws.threadName(),
                tws.parks(),
                TimeUnit.NANOSECONDS.toMicros(tws.parkedNanos()),
                tws.failedOffers());
      }
      System.out.println();
    }

    if (runOpts.contains(RunOptions.WITH_WAL)) {
      System.out.println("-----------------------------");
      System.out.println("-----  WAL queue stats  -----");
      System.out.println("-----------------------------");
      System.out.printf("Peak WAL queue depth (i.e. HWM): %,d%n", walQueue.highWaterMark());
      MessageQueueStats s = messageGateway.getWalQueueStats();
      long parkedUs = TimeUnit.NANOSECONDS.toMicros(s.totalParkedNanos());

      System.out.printf("WAL: dropped=%d parks=%d parked=%dµs failedOffers=%d%n",
              s.messagesDropped(), s.totalParks(), parkedUs, s.totalFailedOffers());
      System.out.println();

      for (ThreadWaitSnapshot tws : s.perThread()) {
        System.out.printf("  T[%d:%s] parks=%d parked=%dµs failedOffers=%d%n",
                tws.threadId(), tws.threadName(),
                tws.parks(),
                TimeUnit.NANOSECONDS.toMicros(tws.parkedNanos()),
                tws.failedOffers());
      }
      System.out.println();
    }

    /* -------------------------------------------------
     * House-keeping
     * ------------------------------------------------- */
    walQueue.clear();
    pubQueue.clear();
    logger.debug("Queues cleared");
    customClassloader.shutdown();
    logger.debug("Custom classloader shut down");
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
   * Benchmark for a realistic workload, with an open-loop switching between
   * baseline and burst modes.
   *
   * @param plan the open-loop plan
   * @param ts thread state instance - producer of dispatch args
   * @param bh the Blackhole instance that consumes values to prevent JIT optimizations.
   */
  @SuppressWarnings("unused")
  @Benchmark
  @BenchmarkMode(Mode.SampleTime)                 // per-request latency
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Threads(Threads.MAX)
  public void hotPathBurst(BurstPlan plan, InputThreadState ts, Blackhole bh) {

    long now = System.nanoTime();

    // only run the hot path when the next synthetic request is due
    if (now >= plan.nextArrivalAt) {

      DispatchArgs d;
      while ((d = ts.take()) == null) {
        // only Async mode may return null --> spin until producer refills
        Thread.onSpinWait();
      }

      Object returnVal;
      try {
        returnVal = (d.target() == null)
                ? DispatchForwarder.nonVoidClassMethod(d.ctx(), d.sender(), null, d.args())
                : DispatchForwarder.nonVoidInstanceMethod(d.ctx(), d.sender(), d.target(), d.args());
      } catch (Throwable t) {
        logger.error("Caught throwable in call to DispatchForwarder", t);
        // re-throw
        throw new RuntimeException(t);
      }
      bh.consume(returnVal);
      callsMade.incrementAndGet();
      plan.nextArrivalAt = now + plan.pickIntervalNs();
    }
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
    syncSocket.bind(props.getProperty("sync.ready"));
  }

}

