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
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.ittera.pal.common.cli.PALCommand;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.core.exec.ExtendedThreadPoolExecutor;
import net.ittera.pal.core.exec.InterceptInformer;
import net.ittera.pal.core.exec.LogMessageExecutor;
import net.ittera.pal.core.exec.PeerMessageExecutor;
import net.ittera.pal.core.exec.java.CustomClassloader;
import net.ittera.pal.core.exec.java.SelfCaller;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "peer",
    description = "Run a new peer",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    usageHelpWidth = 90,
    customSynopsis = {
      "pal run [OPTIONS] class [args...]",
      "            (to execute a class)",
      "    or pal run [OPTIONS] -jar jarFile [args...]",
      "            (to execute a jar file)%n",
    })
public class Main implements Callable<Integer> {

  @ParentCommand private PALCommand palCommand;

  @Option(
      names = {"-c", "-cp", "--classpath"},
      paramLabel = "CLASSPATH", // corresponding ENV var: CLASSPATH
      description = "load classes from given folders/jars")
  private String classpath;

  @Option(
      names = {"-d", "--dir"},
      paramLabel = "HOST:PORT",
      description = "PAL directory (if not given, run unregistered)")
  private String palDirectoryURL; // corresponding ENV var: PAL_DIRECTORY

  @Option(
      names = {"-u", "--uuid"},
      paramLabel = "uuid",
      description = "uuid for this peer (default: <random>)")
  private UUID uuid; // corresponding ENV var: PEER_UUID

  @Option(
      names = {"-n", "--name"},
      arity = "1",
      paramLabel = "name",
      description = "name for this peer")
  private String name; // corresponding ENV var: PEER_NAME

  @Option(
      names = {"-m", "--as-daemon"},
      description = "keep running after call to mainClass/jar returns")
  private boolean asDaemon = false;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "name|auto",
      description = "read from and write to given log ('auto' works only with <pal_directory>)")
  private String log; // corresponding ENV var: LOG

  @Option(
      names = {"-i", "--in-log"},
      paramLabel = "name|auto",
      description = "read from given log ('auto' works only with <pal_directory>)")
  private String inLog; // corresponding ENV var: IN_LOG

  @Option(
      names = {"-s", "--start-at"},
      paramLabel = "log_offset",
      description = "start reading from given offset")
  private Long logOffset;

  @Option(
      names = {"-o", "--out-log"},
      paramLabel = "name|auto",
      description = "write to given log ('auto' works only with <pal_directory>)")
  private String outLog; // corresponding ENV var: OUT_LOG

  @Option(
      names = {"-k", "--kafka"},
      paramLabel = "bootstrap_servers",
      description = "connect to given kafka servers when running with no <pal_directory>")
  private String kafkaServers; // corresponding ENV var: KAFKA_SERVERS

  @Option(
      names = {"-p", "--tcp-pub"},
      paramLabel = "[HOST:]PORT|auto",
      description = "publish messages to TCP socket (auto = localhost:random_port)")
  private String tcpPub; // corresponding ENV var: TCP_PUB

  @Option(
      names = {"-r", "--tcp-req"},
      paramLabel = "[HOST:]PORT|auto",
      description = "listen for requests on TCP socket (auto = localhost:random_port)")
  private String tcpReq; // corresponding ENV var: TCP_REQ

  @Option(
      names = {"--tcp-req-core-threads"},
      defaultValue = "1",
      paramLabel = "num_threads",
      description = "number of core threads for tcp requests (default: ${DEFAULT-VALUE})")
  private Integer tcpReqCoreThreads;

  @Option(
      names = {"--tcp-req-max-threads"},
      defaultValue = "100",
      paramLabel = "num_threads",
      description = "maximum number of threads for tcp requests (default: ${DEFAULT-VALUE})")
  private Integer tcpReqMaxThreads;

  @Option(
      names = {"--tcp-req-threads-keepalive"},
      defaultValue = "60",
      paramLabel = "num_seconds",
      description =
          "seconds to wait before terminating idle tcp request threads (default: ${DEFAULT-VALUE})")
  private Long tcpReqThreadsKeepAliveSecs;

  @Option(
      names = {"--interceptable"},
      description = "allow message interception")
  private boolean interceptable = false;

  @Option(
      names = {"--with-source-context"},
      description = "include source context in messages")
  private boolean includeSourceContext = false;

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

  // countdown latch to await when daemonized and have no services running
  private CountDownLatch daemonizedLatch = new CountDownLatch(1);

  // STATIC constants
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
    }

    private static final String DEFAULT_PUB_HOSTNAME = "localhost";
    private static final String DEFAULT_REQ_HOSTNAME = "localhost";
  }

  private void initLogging() {
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
    if (paramValue != null && !paramValue.trim().isEmpty()) {
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
    classpath = getParameter("CLASSPATH", classpath);
    kafkaServers = getParameter("KAFKA_SERVERS", kafkaServers);
    name = getParameter("PEER_NAME", name);
    uuid = getParameter("PEER_UUID", uuid);
    log = getParameter("LOG", log);
    inLog = getParameter("IN_LOG", inLog);
    outLog = getParameter("OUT_LOG", outLog);
    tcpReq = getParameter("TCP_REQ", tcpReq);
    tcpPub = getParameter("TCP_PUB", tcpPub);

    // if not given as option to this CMD, check if it was given as option to parent (Pal) command
    // else, set it from ENV if present
    if (palDirectoryURL == null || palDirectoryURL.trim().isEmpty()) {
      // check ENV variable
      String palDirectoryEnvVar = System.getenv("PAL_DIRECTORY");
      palDirectoryEnvVar = palDirectoryEnvVar != null ? palDirectoryEnvVar.trim() : null;
      // check if it was given to parent command (Pal) as option
      if (palCommand != null
          && !Arrays.asList(palDirectoryEnvVar, PALDirectory.NO_URL)
              .contains(palCommand.getPalDirectoryConnectionString())) {
        palDirectoryURL = palCommand.getPalDirectoryConnectionString();
      } else {
        // set it from parsed ENV variable (which at this point may be null, that's ok)
        palDirectoryURL = palDirectoryEnvVar;
      }
    }
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

    if (palDirectoryURL == null || palDirectoryURL.isEmpty()) {
      runOptions.add(RunOptions.NO_PALDIR);

      // warn of incompatible options that will be ignored
      if (name != null) {
        System.err.println(
            "WARNING: --name only applicable if registering with a <pal_directory>.");
        logger.warn("--name given, but not <pal_directory>; will be ignored.");
      }
      if (interceptable) {
        System.err.println(
            "WARNING: --interceptable only applicable if registering with a <pal_directory>.");
        logger.warn("--interceptable given, but not <pal_directory>; will be ignored.");
      }
    }

    if (log != null) {
      // if logName is given, assign to both inLog and outLog
      if (inLog != null || outLog != null) {
        System.err.println(
            "WARNING: with --log (LOG), --in-log (IN_LOG) and --out-log (OUT_LOG) options are ignored.");
      }
      inLog = outLog = log;
    }

    if (inLog == null) {
      runOptions.add(RunOptions.NO_INLOG);
    }

    if (outLog == null) {
      runOptions.add(RunOptions.NO_OUTLOG);
    }

    // ensure that if offset was given, a log name to read from was also given
    if (logOffset != null && (inLog == null || inLog.equalsIgnoreCase("auto"))) {
      fatalExit(null, PeerException.FatalCode.ERROR_NO_LOG_GIVEN);
    }

    if (runOptions.contains(RunOptions.NO_OUTLOG) && tcpPub == null) {
      runOptions.add(RunOptions.NO_PUBLISHING);
    }

    if (runOptions.contains(RunOptions.NO_PALDIR) || !interceptable) {
      runOptions.add(RunOptions.NO_INTERCEPTS);
    }

    if (tcpReq == null) {
      runOptions.add(RunOptions.REQLESS);
    }

    logger.info("Running with options: {}", runOptions);
  }

  private static int findOpenPort() throws IOException {
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
    properties.setProperty(
        "paldir_url",
        palDirectoryURL == null || palDirectoryURL.isEmpty()
            ? PALDirectory.NO_URL
            : palDirectoryURL);

    // add kafka servers if given
    if (kafkaServers != null) {
      properties.setProperty("kafka.bootstrap.servers", kafkaServers);
    }

    // are we publishing via TCP, or just internally
    if (tcpPub != null) {
      int port = 0;
      String hostname = ZMQProps.DEFAULT_PUB_HOSTNAME;
      if (tcpPub.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for PUB");
        }
      } else if (tcpPub.contains(":")) {
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
    if (tcpReq != null) {
      String hostname = ZMQProps.DEFAULT_REQ_HOSTNAME;
      int port = 0;
      if (tcpReq.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for REQ");
        }
      } else {
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
      }
      properties.setProperty("in.req.tcp", format("tcp://%s:%d", hostname, port));
      properties.setProperty("peer.corePoolSize", String.valueOf(tcpReqCoreThreads));
      properties.setProperty("peer.maximumPoolSize", String.valueOf(tcpReqMaxThreads));
      properties.setProperty("peer.keepAliveSeconds", String.valueOf(tcpReqThreadsKeepAliveSecs));
    }

    // message content options
    properties.setProperty("messages.with_src_context", String.valueOf(includeSourceContext));
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

    final PALDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);

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
    if (!runOptions.contains(RunOptions.NO_INLOG)) {
      services.add(injector.getInstance(LogReader.class));
    }
    if (!runOptions.contains(RunOptions.NO_OUTLOG)) {
      services.add(injector.getInstance(LogWriter.class));
    }
    if (!runOptions.contains(RunOptions.NO_PUBLISHING)) {
      services.add(injector.getInstance(OutgoingMessageDispatcher.class));
    }
    if (!runOptions.contains(RunOptions.REQLESS)) {
      services.add(injector.getInstance(DirectRequestDispatcher.class));
    }
    if (!runOptions.contains(RunOptions.NO_INTERCEPTS)) {
      services.add(injector.getInstance(InterceptMatcher.class));
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
      if (manager != null) {
        manager.stopAsync();
      }

      // stop peer executor (interrupts all peer exec threads)
      if (!runOptions.contains(RunOptions.REQLESS)) {
        final ExtendedThreadPoolExecutor peerMessageExecutor =
            injector.getInstance(PeerMessageExecutor.class);
        peerMessageExecutor.shutdownNow();
        logger.info("Done shutting down peer threads");
      }

      // stop log executor (interrupts all log exec threads)
      if (!runOptions.contains(RunOptions.NO_INLOG)) {
        final ExtendedThreadPoolExecutor logMessageExecutor =
            injector.getInstance(LogMessageExecutor.class);
        logMessageExecutor.shutdownNow();
        logger.info("Done shutting down log threads");
      }

      // close connection to paldir
      if (!runOptions.contains(RunOptions.NO_PALDIR)) {
        final Optional<PALDirectory> palDirectory =
            injector.getInstance(DirectoryConnectionProvider.class).get();
        palDirectory.ifPresent(PALDirectory::close);
      }

      // close sockets that aren't automatically closed
      if (!runOptions.contains(RunOptions.NO_INTERCEPTS)) {
        final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
        interceptInformer.closeThreadLocalSocket();
      }

      // close zmq context asynchronously
      singleExecutor.submit(this::closeZmqContext);
      singleExecutor.shutdown();

      // wait a bit for services to stop
      if (manager != null) {
        if (fast) {
          manager.awaitStopped(500, TimeUnit.MILLISECONDS);
        } else {
          manager.awaitStopped(3, TimeUnit.SECONDS);
        }
      }

      // wait a bit for exec service to finish closing zmq context
      if (fast) {
        singleExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
      } else {
        singleExecutor.awaitTermination(2, TimeUnit.SECONDS);
      }

      // in case we're daemonized and have no services manager (ie. manager == null)
      if (manager == null) {
        daemonizedLatch.countDown();
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
    CommandLine commandLine = new CommandLine(new Main());
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws InterruptedException {

    initLogging();
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
    if (!runOptions.contains(RunOptions.NO_PALDIR)) {
      singleExecutor.submit(
          () -> {
            registerSelfAsPeer(injector);
            selfRegistrationLatch.countDown();
          });
    }

    // init logs IO
    if (!(runOptions.contains(RunOptions.NO_INLOG) && runOptions.contains(RunOptions.NO_OUTLOG))) {
      try {
        new LogConfigurator(inLog, logOffset, outLog, properties, injector).init();
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS);
      }
    }

    // set up managed services
    final Set<Service> services = createManagedServices(injector);
    final ServiceManager manager = !services.isEmpty() ? createServiceManager(services) : null;

    // add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(manager, injector, false)));

    // start services
    if (manager != null) { // manager = null if there are no services
      manager.startAsync();
    }

    // block until we're registered in Directory
    if (!runOptions.contains(RunOptions.NO_PALDIR)) {
      try {
        selfRegistrationLatch.await();
      } finally {
        singleExecutor.shutdownNow();
      }
    }

    // double-check by collecting all READY signals from services before proceeding
    collectGoSignals(services.size());

    // wait for all services up
    if (manager != null) {
      manager.awaitHealthy();
    }

    // start listening to intercept reqs
    if (!runOptions.contains(RunOptions.NO_INTERCEPTS)) {
      final PALDirectory palDirectory =
          injector
              .getInstance(DirectoryConnectionProvider.class)
              .get()
              .orElseThrow(RuntimeException::new);
      final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
      // add as listener for future requests
      palDirectory.addInterceptNodeListener(interceptInformer);
      // register all current intercepts in directory
      interceptInformer.registerAllInterceptsInDirectory();
    }

    // start accepting Log requests
    if (!runOptions.contains(RunOptions.NO_INLOG)) {
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
      if (manager != null) {
        manager.awaitStopped();
      } else {
        daemonizedLatch.await();
      }
    }
    return 0;
  }
}
