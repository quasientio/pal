package com.ittera.cometa.core;

import static java.lang.String.format;
import static picocli.CommandLine.Option;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ittera.cometa.common.util.Strings;
import com.ittera.cometa.core.exec.ExtendedThreadPoolExecutor;
import com.ittera.cometa.core.exec.LogMessageExecutor;
import com.ittera.cometa.core.exec.PeerMessageExecutor;
import com.ittera.cometa.core.exec.java.CustomClassloader;
import com.ittera.cometa.core.exec.java.SelfCaller;
import com.ittera.cometa.cxn.PALDirectory;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "peer")
public class Main implements Callable<Integer> {

  @Option(
      names = {"-u", "--use-uuid"},
      paramLabel = "PEER_UUID",
      description = "use given uuid")
  private UUID uuid;

  @Option(
      names = {"-n", "--name"},
      arity = "1",
      paramLabel = "PEER_NAME",
      description = "name for this peer")
  private String name;

  @Option(
      names = {"-li", "--log-in"},
      paramLabel = "LOGNAME",
      description = "read from given log")
  private String inLogName;

  @Option(
      names = {"-ls", "--log-start"},
      paramLabel = "OFFSET_START",
      description = "start reading from given offset")
  private Long inLogOffset;

  @Option(
      names = {"-lo", "--log-out"},
      paramLabel = "LOGNAME",
      description = "write to given log")
  private String outLogName;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "LOGNAME",
      description = "read and write from/to given log")
  private String logName;

  @Option(
      names = {"-ll", "--logless"},
      paramLabel = "LOGLESS",
      description = "run without log IO")
  private boolean logless = false;

  @Option(
      names = {"-tp", "--tcp-pub"},
      paramLabel = "[HOST:]PORT",
      description = "publish messages to TCP socket")
  private String tcpPub;

  @Option(
      names = {"-r", "--tcp-req"},
      paramLabel = "[HOST:]PORT",
      description =
          "listen for requests on TCP socket (default: localhost:random_port)\n--tcp-req=no to accept no requests over TCP")
  private String tcpReq;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  @Option(
      names = {"-jar"},
      paramLabel = "JAR_FILE",
      description = "execute jar file")
  private String jarFile;

  @Parameters(hidden = true)
  private List<String> cmdArgList;

  // argList and className will be populated from cmdArgList
  private List<String> argList;
  private String className;

  @Option(
      names = {"-d", "--as-daemon"},
      description = "keep running after call to mainClass/jar returns")
  private boolean asDaemon = false;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  // app properties
  private final Properties properties = new Properties();

  // run options
  private EnumSet<RunOptions> runOptions;

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

    // internal ZMQ channel names
    private static final Properties inprocChannels = new Properties();

    static {
      inprocChannels.put("in.log", "inproc://inlog");
      inprocChannels.put("in.dealer", "inproc://deal");
      inprocChannels.put("out.cell", "inproc://cell");
      inprocChannels.put("out.pub.inproc", "inproc://pub");
      inprocChannels.put("offset.pub", "inproc://offsets");
      inprocChannels.put("sync.ready", "inproc://sync_ready");
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

    // verify and set log options
    if (logless) {
      runOptions = EnumSet.of(RunOptions.LOGLESS);
      if (Stream.of(logName, inLogName, outLogName).anyMatch(Objects::nonNull)) {
        System.err.println(
            "WARNING: -ll (--logless) option takes precedence. All other log (-l) options ignored.");
      }
    } else {
      // set INLOG_SAME_AS_OUTLOG
      if (inLogName == null && outLogName == null) {
        runOptions = EnumSet.of(RunOptions.INLOG_SAME_AS_OUTLOG);
      }
      // if logName is given, assign to both in and out (overriding any of the latter values)
      if (logName != null) {
        inLogName = outLogName = logName;
      }

      // ensure that if offset was given, a log name to read from was also given
      if (inLogOffset != null && inLogName == null) {
        fatalExit(null, PeerException.FatalCode.ERROR_NO_LOG_GIVEN);
      }
    }

    // set TCP options
    final String tcpReqGiven = getParameter("TCP_REQ", tcpReq);
    final boolean reqless = tcpReqGiven != null && tcpReqGiven.equalsIgnoreCase("no");
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

  // load from 1) cmd-line or 2) env variable
  private static String getParameter(String envKey, String paramValue) {
    if (paramValue != null && !paramValue.isEmpty()) {
      return paramValue;
    }
    final String envVar = System.getenv(envKey);
    if (envVar != null) {
      return envVar.trim();
    }
    return null;
  }

  private void addMiscProperties() {

    // load from Environment variable or system property
    String zookeeperUrl = System.getenv("ZOOKEEPER_URL");
    if (zookeeperUrl == null) {
      zookeeperUrl = System.getProperty("zookeeper_url");
    }

    if (zookeeperUrl == null) {
      fatalExit(null, PeerException.FatalCode.ERROR_NO_ZOOKEEPER_URL_GIVEN);
    }
    // add to app properties
    properties.setProperty("zookeeper_url", zookeeperUrl);

    // are we publishing via TCP, or just internally
    if (tcpPub != null) {
      String hostname = "0.0.0.0";
      int port;
      if (tcpPub.contains(":")) {
        hostname = Strings.stringBefore(tcpPub, ":");
        port = Integer.parseInt(Strings.stringAfter(tcpPub, ":"));
      } else {
        port = Integer.parseInt(tcpPub);
      }
      properties.put("out.pub", format("tcp://%s:%d", hostname, port));
    } else {
      properties.setProperty("out.pub", ZMQProps.inprocChannels.getProperty("out.pub.inproc"));
    }

    // are we listening for requests over TCP
    if (!runOptions.contains(RunOptions.REQLESS)) {
      String hostname = "0.0.0.0";
      int port = 0;
      String tcpReqGiven = getParameter("TCP_REQ", tcpReq);
      if (tcpReqGiven != null && !tcpReqGiven.isEmpty()) {
        final String portStr;
        if (tcpReqGiven.contains(":")) {
          hostname = Strings.stringBefore(tcpReqGiven, ":");
          portStr = Strings.stringAfter(tcpReqGiven, ":");
        } else {
          portStr = tcpReqGiven;
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
    if (jmxRemoteHost == null) { // if local.only, then we assume hostname = 'localhost'
      String localOnly = System.getProperty("com.sun.management.jmxremote.local.only");
      if (localOnly != null && !"false".equalsIgnoreCase(localOnly)) {
        jmxRemoteHost = "localhost";
      }
    }
    if (jmxRemoteHost != null && jmxRemotePort != null) {
      return format("%s:%d", jmxRemoteHost, jmxRemotePort);
    } else {
      return null;
    }
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
          .getProperty("out.pub")
          .startsWith("tcp://")) { // only register PUB addr if over tcp
        peerProperties.put("pubAddress", properties.getProperty("out.pub"));
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
      try {
        if (fast) {
          singleExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } else {
          singleExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        logger.error("Unexpected interrupt while closing zmq ctxt", e);
      }
    } catch (TimeoutException ie) {
      logger.error("Timeout exception in shutdown hook", ie);
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
  public Integer call() {

    validateInput();

    loadProps();

    initZContext();

    // add zmq channel names to properties
    properties.putAll(ZMQProps.inprocChannels);

    // set this peer's uuid if not given
    if (uuid == null) {
      uuid = UUID.randomUUID();
    }
    properties.put("id", uuid.toString());

    // add misc variables to app props
    addMiscProperties();

    // init custom classloader
    List<URL> urls = new ArrayList<>();
    if (classpath != null) {
      // split by ':' and add each entry: each should be either a folder or a JAR (just as in
      // $CLASSPATH)
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
    CustomClassloader customClassloader =
        new CustomClassloader(
            urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    logger.info("Initialized custom classloader with paths: {}", urls.toString());

    // inject dependencies
    final Injector injector =
        Guice.createInjector(new PeerWiring(properties, runOptions, zmqContext, customClassloader));
    // circular dependency must be resolved explicitly
    customClassloader.setInterceptProcessor(injector.getInstance(InterceptProcessor.class));

    // register peer
    registerSelfAsPeer(injector);

    // init logs IO
    if (!runOptions.contains(RunOptions.LOGLESS)) {
      try {
        new LogConfigurator(inLogName, inLogOffset, outLogName, properties, runOptions, injector)
            .init();
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS);
      }
    }

    // set up managed services
    final Set<Service> services = new HashSet<>();
    if (!runOptions.contains(RunOptions.LOGLESS)) {
      services.add(injector.getInstance(LogReader.class));
      services.add(injector.getInstance(LogWriter.class));
    }
    services.add(injector.getInstance(OutgoingMessageDispatcher.class));
    if (!runOptions.contains(RunOptions.REQLESS)) {
      services.add(injector.getInstance(DirectRequestDispatcher.class));
    }

    final ServiceManager manager = new ServiceManager(services);
    manager.addListener(
        new ServiceManager.Listener() {
          public void stopped() {
            logger.info("Service manager stopped.");
          }

          public void healthy() {
            logger.info("Managed services ready");
            manager
                .startupTimes()
                .forEach((key, value) -> logger.info("Service '{}' started in {} ms", key, value));
          }

          public void failure(Service service) {
            fatalExit(service.failureCause(), PeerException.FatalCode.ERROR_SERVICE_MANAGER_FAILED);
          }
        },
        MoreExecutors.directExecutor());

    // add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(manager, injector, false)));

    // start services
    manager.startAsync();

    // wait for all services up
    manager.awaitHealthy();

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

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
