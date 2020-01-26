package net.ittera.pal.core;

import static java.lang.String.format;
import static picocli.CommandLine.Option;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.core.exec.ExtendedThreadPoolExecutor;
import net.ittera.pal.core.exec.InterceptInformer;
import net.ittera.pal.core.exec.LogMessageExecutor;
import net.ittera.pal.core.exec.PeerMessageExecutor;
import net.ittera.pal.core.exec.java.CustomClassloader;
import net.ittera.pal.core.exec.java.SelfCaller;
import net.ittera.pal.cxn.PALDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "peer",
    sortOptions = false,
    customSynopsis = {
      "peer [OPTIONS] class [args...]",
      "            (to execute a class)",
      "or     peer [OPTIONS] -jar jarFile [args...]",
      "            (to execute a jar file)",
    })
public class Main implements Callable<Integer> {
  @Option(
      names = {"-c", "-cp", "--classpath"},
      paramLabel = "CLASSPATH",
      description = "load classes from given folders/jars")
  private String classpath;

  @Option(
      names = {"-d", "--dir"},
      defaultValue = "localhost:2181",
      description = "PAL directory URL (default: ${DEFAULT-VALUE})")
  private String palDirectoryURL; // corresponding ENV var: PAL_DIRECTORY

  @Option(
      names = {"-u", "--uuid"},
      description = "uuid for this peer (default: random)")
  private UUID uuid; // corresponding ENV var: PEER_UUID

  @Option(
      names = {"-n", "--name"},
      arity = "1",
      description = "name for this peer")
  private String name; // corresponding ENV var: PEER_NAME

  @Option(
      names = {"-m", "--as-daemon"},
      description = "keep running after call to mainClass/jar returns")
  private boolean asDaemon = false;

  @Option(
      names = {"-l", "--log"},
      description = "read and write from/to given log\n--log=no to run without log IO")
  private String log; // corresponding ENV var: LOG

  @Option(
      names = {"-i", "--in-log"},
      description = "read from given log")
  private String inLog; // corresponding ENV var: IN_LOG

  @Option(
      names = {"-s", "--start-at"},
      description = "start reading from given offset")
  private Long logOffset;

  @Option(
      names = {"-o", "--out-log"},
      description = "write to given log")
  private String outLog; // corresponding ENV var: OUT_LOG

  @Option(
      names = {"-p", "--tcp-pub"},
      paramLabel = "[HOST:]PORT",
      description = "publish messages to TCP socket")
  private String tcpPub; // corresponding ENV var: TCP_PUB

  @Option(
      names = {"-r", "--tcp-req"},
      paramLabel = "[HOST:]PORT",
      description =
          "listen for requests on TCP socket (default: localhost:random_port)\n--tcp-req=no to accept no requests over TCP")
  private String tcpReq; // corresponding ENV var: TCP_REQ

  @Option(
      names = {"--no-intercepts"},
      description = "don't allow message interception")
  private boolean noIntercepts = false;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  @Option(
      names = {"-jar"},
      description = "execute jar file",
      hidden = true)
  private String jarFile;

  @Parameters(hidden = true)
  private List<String> cmdArgList;

  // argList and className will be populated from cmdArgList
  private List<String> argList;
  private String className;

  // app properties
  private final Properties properties = new Properties();

  // run options
  private Set<RunOptions> runOptions;

  // zmq context
  private ZContext zmqContext;
  private Socket syncSocket;

  // STATIC variables
  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String PROPERTIES_FILE = "/peer.properties";
  private static final String LOGGING_CONFIG = "/peer-logging.xml";

  private static final class ZMQProps {
    // defaults for ZMQ properties
    private static final String ZMQ_LINGER_DEFAULT = "1000";
    private static final String ZMQ_RCVHWM_DEFAULT = "10000";
    private static final String ZMQ_SNDHWM_DEFAULT = "10000";

    private static final String OUT_PUB_CHANNEL = "out.pub";

    // internal ZMQ channel names
    private static final Properties inprocChannels = new Properties();

    static {
      inprocChannels.put("in.log", "inproc://inlog");
      inprocChannels.put("in.dealer", "inproc://deal");
      inprocChannels.put("out.cell", "inproc://cell");
      inprocChannels.put("out.pub.inproc", "inproc://pub");
      inprocChannels.put("offset.pub", "inproc://offsets");
      inprocChannels.put("sync.ready", "inproc://sync_ready");
      inprocChannels.put("intercepts.reg", "inproc://intcept_reg");
      inprocChannels.put("intercepts.mtx", "inproc://intcept_match");
    }
  }

  static {
    // configure logging
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    context.reset();
    try (final InputStream stream = Main.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (Exception ie) {
      System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
      // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
      ie.printStackTrace();
    }
  }

  private void loadProps() {
    // load properties from file in classpath
    try (final InputStream stream = Main.class.getResourceAsStream(PROPERTIES_FILE)) {
      properties.load(stream);
    } catch (Exception ex) {
      fatalExit(
          ex,
          PeerException.FatalCode.ERROR_LOADING_PROPERTIES,
          format("Make sure to have `%s` in the classpath", PROPERTIES_FILE));
    }
    logger.info("Loaded application properties from `{}`", PROPERTIES_FILE);
  }

  private void initZContext() {
    zmqContext = new ZContext();
    zmqContext.setLinger(
        Integer.parseInt(properties.getProperty("ZMQ_LINGER", ZMQProps.ZMQ_LINGER_DEFAULT)));
    zmqContext.setRcvHWM(
        Integer.parseInt(properties.getProperty("ZMQ_RCVHWM", ZMQProps.ZMQ_RCVHWM_DEFAULT)));
    zmqContext.setSndHWM(
        Integer.parseInt(properties.getProperty("ZMQ_SNDHWM", ZMQProps.ZMQ_SNDHWM_DEFAULT)));
    logger.info("Created and configured zmq context");

    // start ready socket
    syncSocket = zmqContext.createSocket(SocketType.PULL);
    syncSocket.bind(ZMQProps.inprocChannels.getProperty("sync.ready"));
  }

  private void closeZmqContext() {
    if (logger.isDebugEnabled()) {
      logger.debug("Closing zmq context");
    }
    zmqContext.close();
    logger.info("Closed zmq context");
  }

  private void fatalExit(PeerException peerException) {
    fatalExit(peerException, peerException.getFatalCode());
  }

  private void fatalExit(Throwable ex, PeerException.FatalCode fatalCode) {
    fatalExit(ex, fatalCode, null);
  }

  private void fatalExit(Throwable ex, PeerException.FatalCode fatalCode, String extraMessage) {
    if (ex != null) {
      logger.error(fatalCode.getMessage(), ex);
    }
    System.err.println(fatalCode.getMessage());
    if (extraMessage != null) {
      System.err.println(extraMessage);
    }
    System.exit(fatalCode.getCode());
  }

  // load from 1) cmd-line or 2) env variable
  private static String getParameter(String envKey, String paramValue) {
    if (paramValue != null && !paramValue.isEmpty()) {
      return paramValue;
    }
    final String envVar = System.getenv(envKey);
    if (envVar != null && !envVar.trim().isEmpty()) {
      return envVar.trim();
    }
    return null;
  }

  private static UUID getParameter(String envKey, UUID paramValue) {
    final String uuidAsString =
        getParameter(envKey, paramValue == null ? null : paramValue.toString());
    if (uuidAsString != null) {
      return UUID.fromString(uuidAsString);
    }
    return null;
  }

  private void setEmptyParamsFromEnv() {
    palDirectoryURL = getParameter("PAL_DIRECTORY", palDirectoryURL);
    name = getParameter("PEER_NAME", name);
    uuid = getParameter("PEER_UUID", uuid);
    log = getParameter("LOG", log);
    inLog = getParameter("IN_LOG", inLog);
    outLog = getParameter("OUT_LOG", outLog);
    tcpReq = getParameter("TCP_REQ", tcpReq);
    tcpPub = getParameter("TCP_PUB", tcpPub);
  }

  private void validateInput() {

    // set argList
    if (jarFile != null) { // if -jar, all positional parameters are considered its args
      argList = cmdArgList;
    } else if (cmdArgList != null) { // else, first is considered the mainClass
      className = cmdArgList.get(0);
      argList = cmdArgList.subList(1, cmdArgList.size());
    }

    // warn if daemon flag does not apply
    if (asDaemon && (className == null && jarFile == null)) {
      System.err.println("WARNING: -d (--as-daemon) option only relevant with mainClass or -jar.");
    }

    // verify and set run options
    runOptions = EnumSet.noneOf(RunOptions.class);
    if (log != null && (inLog != null || outLog != null)) {
      System.err.println(
          "WARNING: with --log (LOG), --in-log (IN_LOG) and --out-log (OUT_LOG) options are ignored.");
    }
    final boolean logless = log != null && log.equalsIgnoreCase("no");
    if (logless) {
      runOptions.add(RunOptions.LOGLESS);
    } else {
      // set INLOG_SAME_AS_OUTLOG
      if (inLog == null && outLog == null) {
        runOptions.add(RunOptions.INLOG_SAME_AS_OUTLOG);
      }
      // if logName is given, assign to both in and out (overriding any of the latter values)
      if (log != null) {
        inLog = outLog = log;
      }

      // ensure that if offset was given, a log name to read from was also given
      if (logOffset != null && inLog == null) {
        fatalExit(null, PeerException.FatalCode.ERROR_NO_LOG_GIVEN);
      }
    }
    if (logless && tcpPub == null) {
      runOptions.add(RunOptions.NO_PUBLISHING);
    }
    if (noIntercepts) {
      runOptions.add(RunOptions.NO_INTERCEPTS);
    }

    // set TCP-related options
    final boolean reqless = tcpReq != null && tcpReq.equalsIgnoreCase("no");
    if (reqless) {
      runOptions.add(RunOptions.REQLESS);
    }

    logger.info("Running with options: {}", runOptions);
  }

  public static int findOpenPort() throws IOException {
    final ServerSocket tmpSocket = new ServerSocket(0, 0);
    try {
      return tmpSocket.getLocalPort();
    } finally {
      tmpSocket.close();
    }
  }

  private void addMiscProperties() {

    if (logger.isDebugEnabled()) {
      logger.debug("Environment variables:");
      System.getenv().entrySet().forEach(e -> logger.debug("{}={}", e.getKey(), e.getValue()));
    }

    // set this peer's UUID if given from param or ENV, otherwise create random UUID
    if (uuid == null) {
      final String envUuid = System.getenv("PEER_UUID");
      if (envUuid != null) {
        uuid = UUID.fromString(envUuid.trim());
      } else {
        uuid = UUID.randomUUID();
      }
    }
    properties.put("id", uuid.toString());

    // add Directory url to app properties
    properties.setProperty("paldir_url", palDirectoryURL);

    // are we publishing via TCP, or just internally
    if (tcpPub != null) {
      int port;
      String hostname = "0.0.0.0";
      if (tcpPub.contains(":")) {
        hostname = Strings.stringBefore(tcpPub, ":");
        port = Integer.parseInt(Strings.stringAfter(tcpPub, ":"));
      } else {
        port = Integer.parseInt(tcpPub);
      }
      properties.setProperty(ZMQProps.OUT_PUB_CHANNEL, format("tcp://%s:%d", hostname, port));
    } else {
      properties.setProperty(
          ZMQProps.OUT_PUB_CHANNEL, ZMQProps.inprocChannels.getProperty("out.pub.inproc"));
    }

    // are we listening for requests over TCP
    if (!runOptions.contains(RunOptions.REQLESS)) {
      String hostname = "0.0.0.0";
      int port = 0;
      if (tcpReq != null) {
        final String portStr;
        if (tcpReq.contains(":")) {
          hostname = Strings.stringBefore(tcpReq, ":");
          portStr = Strings.stringAfter(tcpReq, ":");
        } else {
          portStr = tcpReq;
        }
        try {
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
          fatalExit(e, PeerException.FatalCode.ERROR_PARSING_REQ_PORT_NUMBER);
        }
      } else { // default is to listen on 0.0.0.0:RANDOM_PORT
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(null, PeerException.FatalCode.ERROR_FINDING_REQ_SOCKET);
        }
      }
      properties.setProperty("in.req.tcp", format("tcp://%s:%d", hostname, port));
    }
  }

  private String getJMXAddress() {
    final String jmxRemote = System.getProperty("com.sun.management.jmxremote");
    if ("false".equalsIgnoreCase(jmxRemote)) {
      return null;
    }
    final String jmxRemotePortStr = System.getProperty("com.sun.management.jmxremote.port");
    Integer jmxRemotePort = jmxRemotePortStr != null ? Integer.parseInt(jmxRemotePortStr) : null;
    String jmxRemoteHost = System.getProperty("java.rmi.server.hostname");
    if (jmxRemoteHost == null) {
      final String localOnly = System.getProperty("com.sun.management.jmxremote.local.only");
      // see if JMX_HOST env variable exists
      final String hostEnv = System.getenv("JMX_HOST");
      if (hostEnv != null && !hostEnv.isEmpty()) {
        jmxRemoteHost = hostEnv;
      } // if local.only, then we assume hostname = 'localhost'
      else if (localOnly != null && !"false".equalsIgnoreCase(localOnly)) {
        jmxRemoteHost = "localhost";
      }
    }
    if (jmxRemoteHost != null && jmxRemotePort != null) {
      return format("%s:%d", jmxRemoteHost, jmxRemotePort);
    } else {
      return null;
    }
  }

  private CustomClassloader createCustomClassloader() {
    List<URL> urls = new ArrayList<>();
    if (classpath != null) {
      // colon-separated list of classpath entries where each is either a folder or a JAR file
      Arrays.stream(classpath.split(":"))
          .map(File::new)
          .forEach(
              f -> {
                try {
                  urls.add(f.toURI().toURL());
                } catch (MalformedURLException ex) {
                  logger.error("Error adding classpath entry as URL for custom classloader", ex);
                }
              });
    }
    if (jarFile != null) {
      try {
        urls.add(new File(jarFile).toURI().toURL());
      } catch (MalformedURLException ex) {
        logger.error("Error adding JAR file as URL for custom classloader", ex);
      }
    }
    return new CustomClassloader(
        urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
  }

  private void registerSelfAsPeer(Injector injector) {
    final PALDirectory palDirectory = injector.getInstance(PALDirectory.class);

    // register self as new peer
    try {
      final Properties peerProperties = new Properties();
      // public listening interfaces
      if (!runOptions.contains(RunOptions.REQLESS)) {
        peerProperties.put("reqAddress", properties.getProperty("in.req.tcp"));
      }
      if (properties
          .getProperty(ZMQProps.OUT_PUB_CHANNEL)
          .startsWith("tcp://")) { // only register PUB addr if over TCP
        peerProperties.put("pubAddress", properties.getProperty(ZMQProps.OUT_PUB_CHANNEL));
      }
      String jmxAddress = getJMXAddress();
      if (jmxAddress != null) {
        peerProperties.put("jmxAddress", jmxAddress);
      }
      // other info
      if (name != null) {
        peerProperties.put("name", name);
      }
      palDirectory.registerPeer(uuid, peerProperties);
    } catch (Exception ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_PEER);
    }
    logger.info("Registered self in directory");
  }

  private Set<Service> createManagedServices(Injector injector) {
    final Set<Service> services = new HashSet<>();
    if (!runOptions.contains(RunOptions.LOGLESS)) {
      services.add(injector.getInstance(LogReader.class));
      services.add(injector.getInstance(LogWriter.class));
    }
    if (!runOptions.contains(RunOptions.NO_PUBLISHING)) {
      services.add(injector.getInstance(OutgoingMessageDispatcher.class));
    }
    if (!runOptions.contains(RunOptions.REQLESS)) {
      services.add(injector.getInstance(DirectRequestDispatcher.class));
    }
    if (!runOptions.contains(RunOptions.NO_INTERCEPTS)) {
      services.add(injector.getInstance(Intercepts.class));
    }
    return services;
  }

  private ServiceManager createServiceManager(Iterable<Service> services) {
    final ServiceManager manager = new ServiceManager(services);
    manager.addListener(
        new ServiceManager.Listener() {
          @Override
          public void stopped() {
            logger.info("Service manager stopped.");
          }

          @Override
          public void healthy() {
            logger.info("Managed services ready");
            manager
                .startupTimes()
                .forEach((key, value) -> logger.info("Service '{}' started in {} ms", key, value));
          }

          @Override
          public void failure(Service service) {
            fatalExit(service.failureCause(), PeerException.FatalCode.ERROR_SERVICE_MANAGER_FAILED);
          }
        },
        MoreExecutors.directExecutor());
    return manager;
  }

  private void shutdown(ServiceManager manager, Injector injector, boolean fast) {
    if (logger.isDebugEnabled()) {
      logger.debug("We are shutting down ...");
    }
    ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
    try {
      // stop services
      manager.stopAsync();

      // stop peer executor (interrupts all peer exec threads)
      final ExtendedThreadPoolExecutor peerMessageExecutor =
          injector.getInstance(PeerMessageExecutor.class);
      peerMessageExecutor.shutdownNow();
      logger.info("Done shutting down peer threads");

      // stop log executor (interrupts all log exec threads)
      final ExtendedThreadPoolExecutor logMessageExecutor =
          injector.getInstance(LogMessageExecutor.class);
      logMessageExecutor.shutdownNow();
      logger.info("Done shutting down log threads");

      // close zookeeper conn
      final PALDirectory palDirectory = injector.getInstance(PALDirectory.class);
      palDirectory.close();

      // close sockets that aren't automatically closed
      final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
      interceptInformer.closeThreadLocalSocket();

      // close zmq context asynchronously
      singleExecutor.submit(this::closeZmqContext);
      singleExecutor.shutdown();

      // wait a bit for services to stop
      if (fast) {
        manager.awaitStopped(500, TimeUnit.MILLISECONDS);
      } else {
        manager.awaitStopped(3, TimeUnit.SECONDS);
      }

      // wait a bit for exec service to finish closing zmq context
      if (fast) {
        singleExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
      } else {
        singleExecutor.awaitTermination(2, TimeUnit.SECONDS);
      }
    } catch (TimeoutException ie) {
      logger.error("Timeout exception in shutdown hook", ie);
    } catch (InterruptedException e) {
      logger.error("Interrupted while shutting down", e);
    } finally {
      logger.info("This peer is done! bye");
    }
  }

  private void collectGoSignals(int numberOfSignals) {
    CountDownLatch latch = new CountDownLatch(numberOfSignals);
    while (latch.getCount() > 0) {
      String rcvd = syncSocket.recvStr();
      if (rcvd.equalsIgnoreCase("go!")) {
        latch.countDown();
      } else {
        logger.warn("ignoring unexpected msg: '{}'", rcvd);
      }
    }
    syncSocket.close();
  }

  public static void main(final String[] args) {
    logger.info("peer::main called w/args: {}", String.join(" ", args));
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws InterruptedException {

    // for async calls
    final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();

    setEmptyParamsFromEnv();
    validateInput();
    loadProps();

    // initialize ZMQ and local sockets
    initZContext();

    // add zmq channel names to properties
    properties.putAll(ZMQProps.inprocChannels);

    // add misc variables to app props
    addMiscProperties();

    // init custom classloader
    final CustomClassloader customClassloader = createCustomClassloader();

    // inject dependencies
    final Injector injector =
        Guice.createInjector(new PeerWiring(properties, runOptions, zmqContext, customClassloader));
    // circular dependency must be resolved explicitly
    customClassloader.setInterceptProcessor(injector.getInstance(InterceptProcessor.class));

    // register peer async
    final CountDownLatch selfRegistrationLatch = new CountDownLatch(1);
    singleExecutor.submit(
        () -> {
          registerSelfAsPeer(injector);
          selfRegistrationLatch.countDown();
        });

    // init logs IO
    if (!runOptions.contains(RunOptions.LOGLESS)) {
      try {
        new LogConfigurator(inLog, logOffset, outLog, properties, runOptions, injector).init();
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS);
      }
    }

    // set up managed services
    final Set<Service> services = createManagedServices(injector);
    final ServiceManager manager = createServiceManager(services);

    // add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(manager, injector, false)));

    // start services
    manager.startAsync();

    // wait for all services up
    manager.awaitHealthy();

    // block until we're registered in Directory
    try {
      selfRegistrationLatch.await();
    } finally {
      singleExecutor.shutdownNow();
    }

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

    // start listening to intercept reqs
    if (!runOptions.contains(RunOptions.NO_INTERCEPTS)) {
      final PALDirectory palDir = injector.getInstance(PALDirectory.class);
      final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
      // add as listener for future requests
      palDir.addInterceptNodeListener(interceptInformer);
      // register all current intercepts in directory
      interceptInformer.registerAllInterceptsInDirectory();
    }

    // start accepting Log requests
    if (!runOptions.contains(RunOptions.LOGLESS)) {
      LogReader logMessageReader = injector.getInstance(LogReader.class);
      logMessageReader.acceptRequests(true);
      injector.getInstance(LogMessageExecutor.class).prestartAllCoreThreads();
    }

    // prestart threads to create the REP sockets; this must be done after DEALER
    if (!runOptions.contains(RunOptions.REQLESS)) {
      injector.getInstance(PeerMessageExecutor.class).prestartAllCoreThreads();
    }

    // now call target (main class or JAR file), if given
    boolean selfCalled = false;
    if (className != null) {
      // self-call className.main() if given, and then we're done
      injector.getInstance(SelfCaller.class).callMain(className, argList);
      selfCalled = true;
    } else if (jarFile != null) { // NOTE: jarFile was previously added to classpath
      // self-call Main-Class found in manifest
      try {
        injector.getInstance(SelfCaller.class).callJar(jarFile, argList);
      } catch (PeerException e) {
        fatalExit(e);
      }
      selfCalled = true;
    }
    if (selfCalled && !asDaemon) {
      shutdown(manager, injector, true);
    } else {
      // wait here
      manager.awaitStopped();
    }
    return 0;
  }
}
