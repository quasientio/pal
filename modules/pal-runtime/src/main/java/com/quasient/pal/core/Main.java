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

package com.quasient.pal.core;

import static java.lang.String.format;
import static picocli.CommandLine.Option;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.quasient.pal.common.cli.PalCommand;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.util.Strings;
import com.quasient.pal.core.intercepts.InterceptInformer;
import com.quasient.pal.core.rpc.LogMessageExecutor;
import com.quasient.pal.core.rpc.RpcMessageExecutor;
import com.quasient.pal.core.rpc.ThreadPool;
import com.quasient.pal.core.rpc.exec.java.CustomClassloader;
import com.quasient.pal.core.rpc.exec.java.DynamicResourceBundleControlProvider;
import com.quasient.pal.core.rpc.exec.java.SelfCaller;
import com.quasient.pal.core.rpc.exec.java.reflect.AnnotationsProcessor;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Entry point for a Pal peer execution (i.e. pal run).
 *
 * <p>This class orchestrates initialization of logging, configuration properties, ZeroMQ context,
 * custom classloading, dependency injection, and managed services. It processes command-line
 * options to launch a main class, execute a JAR file, or run as a persistent service.
 */
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
      "            (to execute a jar file)",
      "    or pal run [OPTIONS]",
      "            (to run as service - ie. no entry point)%n",
    })
public class Main implements Callable<Integer> {

  /** Reference to the parent PAL command instance providing common command-line options. */
  @SuppressWarnings("unused")
  @ParentCommand
  private PalCommand palCommand;

  /**
   * Classpath configuration for the peer. Specifies folders or JAR files to load classes from.
   * Corresponds to the CLI options -c, -cp, or --classpath and the CLASSPATH environment variable.
   */
  @Option(
      names = {"-c", "-cp", "--classpath"},
      paramLabel = "CLASSPATH", // corresponding ENV var: CLASSPATH
      description = "load classes from given folders/jars")
  private String classpath;

  /**
   * URL for the Pal directory used to register the peer. Provided as an option or through the
   * PAL_DIRECTORY environment variable. If not specified, the peer runs unregistered.
   */
  @Option(
      names = {"-d", "--dir"},
      paramLabel = "HOST:PORT",
      description = "PAL directory (if not given, run unregistered)")
  private String palDirectoryUrl; // corresponding ENV var: PAL_DIRECTORY

  /**
   * Unique identifier for this peer. If not provided, a random UUID is generated. Mapped from the
   * PEER_UUID environment variable.
   */
  @Option(
      names = {"-u", "--uuid"},
      paramLabel = "uuid",
      description = "uuid for this peer (default: <random>)")
  private UUID uuid; // corresponding ENV var: PEER_UUID

  /**
   * Human-readable name for this peer. Used during registration and corresponds to the PEER_NAME
   * environment variable.
   */
  @Option(
      names = {"-n", "--name"},
      arity = "1",
      paramLabel = "name",
      description = "name for this peer")
  private String name; // corresponding ENV var: PEER_NAME

  /**
   * Flag indicating whether to continue running as a service after executing the main class or JAR.
   */
  @Option(
      names = {"-s", "--as-service"},
      description = "keep running after call to mainClass/jar returns")
  private boolean asService = false;

  /**
   * Log configuration specifying the Log name for both reading and writing. Using 'auto' works only
   * when a PAL directory is specified.
   */
  @Option(
      names = {"-l", "--log"},
      paramLabel = "name|auto",
      description = "read from and write to given log ('auto' works only with <pal_directory>)")
  private String log; // corresponding ENV var: LOG

  /**
   * Specifies the input Log name from which Log messages are read. When set to 'auto', it
   * automatically configures with the Pal directory.
   */
  @Option(
      names = {"-i", "--in-log"},
      paramLabel = "name|auto",
      description = "read from given log ('auto' works only with <pal_directory>)")
  private String inLog; // corresponding ENV var: IN_LOG

  /** Specifies the starting offset for reading Log messages. */
  @Option(
      names = {"--start-at"},
      paramLabel = "log_offset",
      description = "start reading from given offset")
  private Long logOffset;

  /**
   * Specifies the output Log name to which Log messages are written. When set to 'auto', a new Log
   * is created with an autogenerated name (uses the Kafka topic prefix).
   */
  @Option(
      names = {"-o", "--out-log"},
      paramLabel = "name|auto",
      description = "write to given log ('auto' works only with <pal_directory>)")
  private String outLog; // corresponding ENV var: OUT_LOG

  /**
   * Comma-separated list of Kafka bootstrap servers. Required when Log options are provided. Maps
   * to the KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "bootstrap_servers",
      description =
          "connect to given kafka servers (required with -l/--log, -i/--in-log and -o/--out-log)")
  private String kafkaServers; // corresponding ENV var: KAFKA_SERVERS

  /**
   * Configuration for TCP-based message publication via ZeroMQ. It accepts a format of
   * "[HOST:]PORT" or "auto" for automatic assignment. Mapped from the TCP_PUB environment variable.
   */
  @Option(
      names = {"-p", "--tcp-pub"},
      paramLabel = "[HOST:]PORT|auto",
      description = "publish messages to ZeroMQ socket (auto = localhost:random_port)")
  private String tcpPub; // corresponding ENV var: TCP_PUB

  /**
   * Configuration for the BIN-RPC listener over ZeroMQ. Accepts "[HOST:]PORT" or "auto" and
   * corresponds to the RPC environment variable.
   */
  @Option(
      names = {"-r", "--rpc"},
      paramLabel = "[HOST:]PORT|auto",
      description = "listen for RPC requests on ZeroMQ socket (auto = localhost:random_port)")
  private String rpc; // corresponding ENV var: RPC

  /**
   * Configuration for the JSON-RPC listener over WebSocket. Accepts "[HOST:]PORT" or "auto" and
   * corresponds to the JSON_RPC environment variable.
   */
  @Option(
      names = {"-j", "--jsonrpc"},
      paramLabel = "[HOST:]PORT|auto",
      description = "listen for JSON-RPC requests on WebSocket (auto = localhost:random_port)")
  private String jsonRpc; // corresponding ENV var: JSON_RPC

  /** Number of threads allocated for handling RPC requests. The default value is 1. */
  @Option(
      names = {"--rpc-threads"},
      defaultValue = "1",
      paramLabel = "num_threads",
      description = "number of threads for RPC requests (default: ${DEFAULT-VALUE})")
  private Integer rpcThreads;

  /** Flag to allow the invocation of nonpublic methods and fields via RPC. The default is false. */
  @Option(
      names = {"--rpc-allow-nonpublic"},
      defaultValue = "false",
      description = "allow invocation of nonpublic methods and fields (default: ${DEFAULT-VALUE})")
  private boolean rpcAllowNonPublic;

  /**
   * Flag indicating whether message interception is enabled. Only applicable when registering with
   * a PAL directory.
   */
  @Option(
      names = {"--interceptable"},
      description = "allow message interception")
  private boolean interceptable = false;

  /**
   * Flag to include source context in Log messages. When enabled, additional source information is
   * included.
   */
  @Option(
      names = {"--with-source-context"},
      description = "include source context in messages")
  private boolean includeSourceContext = false;

  /** Flag to disable annotation processing during class loading. */
  @Option(
      names = {"--disable-annotation-processing"},
      description = "disable annotation processing during class loading")
  private boolean disableAnnotationProcessing = false;

  /**
   * Flag to trigger display of the help message for command-line usage. Handled automatically by
   * the CLI parser.
   */
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  @SuppressWarnings("unused")
  private boolean helpRequested = false;

  /** Specifies a JAR file to execute. This hidden option is used internally when running a JAR. */
  @Option(
      names = {"-jar"},
      description = "execute jar file",
      hidden = true)
  private String jarFile;

  /**
   * List of command-line arguments passed to the executable. This parameter is hidden from standard
   * usage.
   */
  @SuppressWarnings("unused")
  @Parameters(hidden = true)
  private List<String> cmdArgList;

  /**
   * Positional arguments extracted from the command-line, excluding the main class name or jar
   * specification.
   */
  private List<String> argList;

  /** Fully qualified name of the main class to execute, derived from command-line arguments. */
  private String className;

  /** Properties used to configure the peer. Loaded from the properties file and environment. */
  private final Properties properties = new Properties();

  /** Set of runtime options determined from the command-line arguments and environment. */
  private Set<RunOptions> runOptions;

  /** Custom classloader configured with specified classpath entries and JAR file. */
  private CustomClassloader customClassloader;

  /** ZeroMQ context for message communication. */
  private ZContext zmqContext;

  /** ZeroMQ socket used for synchronization and readiness signaling. */
  private Socket syncSocket;

  /** Latch used to prevent premature exit when running in service mode. */
  private final CountDownLatch runAsServiceLatch = new CountDownLatch(1);

  /** SLF4J logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /** Path to the peer properties file in the classpath. */
  private static final String PROPERTIES_FILE = "/peer.properties";

  /** Path to the default logging configuration file in the classpath. */
  private static final String LOGGING_CONFIG = "/peer-logging-fallback.xml";

  /** Duration to wait for managed services to stop. */
  private static final Duration SERVICE_MANAGER_AWAIT_TERM = Duration.of(15, ChronoUnit.SECONDS);

  /** Duration to wait for exec service to stop. */
  private static final Duration EXECUTOR_AWAIT_TERM = Duration.of(1, ChronoUnit.SECONDS);

  /** Container for default ZeroMQ configuration properties and internal endpoint mappings. */
  private static final class ZmqProperties {
    /** Default linger period (in milliseconds) for the ZeroMQ context. */
    private static final String ZMQ_LINGER_DEFAULT = "1000";

    /** Default receive high watermark for the ZeroMQ context. */
    private static final String ZMQ_RCVHWM_DEFAULT = "10000";

    /** Default send high watermark for the ZeroMQ context. */
    private static final String ZMQ_SNDHWM_DEFAULT = "10000";

    /** Property key for the output publication channel configuration. */
    private static final String OUT_PUB_CHANNEL = "out.pub";

    /** Default hostname for TCP PUB interface when auto-assigned. */
    private static final String DEFAULT_PUB_HOSTNAME = "localhost";

    /** Default hostname for the RPC listener when auto-assigned. */
    private static final String DEFAULT_RPC_HOSTNAME = "localhost";

    /** Default hostname for the JSON-RPC listener when auto-assigned. */
    private static final String DEFAULT_JSONRPC_HOSTNAME = "localhost";

    /** Properties holding internal in-process ZeroMQ endpoint mappings. */
    private static final Properties inprocEndpoints = new Properties();

    static {
      inprocEndpoints.put("in.log", "inproc://in_log");
      inprocEndpoints.put("in.dealer", "inproc://deal_rpc");
      inprocEndpoints.put("json.in.dealer", "inproc://deal_jsonrpc");
      inprocEndpoints.put("out.cell", "inproc://cell");
      inprocEndpoints.put("out.pub.inproc", "inproc://pub");
      inprocEndpoints.put("offset.pub", "inproc://offsets");
      inprocEndpoints.put("sync.ready", "inproc://sync_ready");
      inprocEndpoints.put("intercepts.reg", "inproc://intercept_reg");
      inprocEndpoints.put("session.svc", "inproc://session");
    }
  }

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
   * Loads application properties from the properties file located in the classpath.
   *
   * <p>If the properties cannot be loaded, the application terminates with a fatal error.
   */
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

  /**
   * Initializes and configures the ZeroMQ context.
   *
   * <p>The configuration values for linger, receive high watermark, and send high watermark are
   * read from the application properties. Additionally, a synchronization socket is created and
   * bound to a predefined endpoint.
   */
  private void initZmqContext() {
    zmqContext = new ZContext();
    zmqContext.setLinger(
        Integer.parseInt(properties.getProperty("ZMQ_LINGER", ZmqProperties.ZMQ_LINGER_DEFAULT)));
    zmqContext.setRcvHWM(
        Integer.parseInt(properties.getProperty("ZMQ_RCVHWM", ZmqProperties.ZMQ_RCVHWM_DEFAULT)));
    zmqContext.setSndHWM(
        Integer.parseInt(properties.getProperty("ZMQ_SNDHWM", ZmqProperties.ZMQ_SNDHWM_DEFAULT)));
    logger.info("Created and configured zmq context");

    // start ready socket
    syncSocket = zmqContext.createSocket(SocketType.PULL);
    syncSocket.bind(ZmqProperties.inprocEndpoints.getProperty("sync.ready"));
  }

  /** Closes the ZeroMQ context and associated resources. */
  private void closeZmqContext() {
    if (logger.isDebugEnabled()) {
      logger.debug("Closing zmq context");
    }
    zmqContext.close();
    logger.info("Closed zmq context");
  }

  /**
   * Terminates the application fatally due to a critical PeerException.
   *
   * @param peerException the PeerException triggering the fatal exit
   */
  private void fatalExit(PeerException peerException) {
    fatalExit(peerException, peerException.getFatalCode());
  }

  /**
   * Terminates the application fatally using a Throwable and a fatal code.
   *
   * @param ex the Throwable causing the termination
   * @param fatalCode the fatal error code used for exit
   */
  private void fatalExit(Throwable ex, PeerException.FatalCode fatalCode) {
    fatalExit(ex, fatalCode, null);
  }

  /**
   * Terminates the application fatally, logging the exception, printing error messages, and exiting
   * with the specified code.
   *
   * @param ex the Throwable causing the termination (can be null)
   * @param fatalCode the fatal error code used for exit
   * @param extraMessage an optional extra message to display
   */
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

  /**
   * Retrieves a parameter value by checking the provided value and falling back to an environment
   * variable.
   *
   * @param envKey the environment variable key
   * @param paramValue the command-line provided parameter value
   * @return the non-empty parameter value from either the command-line or environment; null if none
   *     provided
   */
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

  /**
   * Sets various parameters from environment variables if they are not already provided via
   * command-line options.
   *
   * <p>This method prioritizes command-line inputs and, if missing, retrieves values from
   * corresponding environment variables.
   */
  private void setEmptyParamsFromEnv() {
    classpath = getParameter("CLASSPATH", classpath);
    kafkaServers = getParameter("KAFKA_SERVERS", kafkaServers);
    name = getParameter("PEER_NAME", name);
    String uuidString = getParameter("PEER_UUID", uuid == null ? null : uuid.toString());
    uuid = uuidString == null ? null : UUID.fromString(uuidString);
    log = getParameter("LOG", log);
    inLog = getParameter("IN_LOG", inLog);
    outLog = getParameter("OUT_LOG", outLog);
    rpc = getParameter("RPC", rpc);
    jsonRpc = getParameter("JSON_RPC", jsonRpc);
    tcpPub = getParameter("TCP_PUB", tcpPub);

    // if not given as option to this CMD, check if it was given as option to parent (Pal) command
    // else, set it from ENV if present
    if (palDirectoryUrl == null || palDirectoryUrl.trim().isEmpty()) {
      // check ENV variable
      String palDirectoryEnvVar = System.getenv("PAL_DIRECTORY");
      palDirectoryEnvVar = palDirectoryEnvVar != null ? palDirectoryEnvVar.trim() : null;
      // check if it was given to parent command (Pal) as option
      if (palCommand != null
          && !Arrays.asList(palDirectoryEnvVar, PalDirectory.NO_URL)
              .contains(palCommand.getPalDirectoryConnectionString())) {
        palDirectoryUrl = palCommand.getPalDirectoryConnectionString();
      } else {
        // set it from parsed ENV variable (which at this point may be null, that's ok)
        palDirectoryUrl = palDirectoryEnvVar;
      }
    }
  }

  /**
   * Validates input parameters and command-line arguments, sets derived runtime options, and
   * outputs warnings or errors when configuration inconsistencies are detected.
   */
  private void validateInput() {

    // set argList
    if (jarFile != null) { // if -jar, all positional parameters are considered its args
      argList = cmdArgList;
    } else if (cmdArgList != null) { // else, first is considered the mainClass
      className = cmdArgList.get(0);
      argList = cmdArgList.subList(1, cmdArgList.size());
    }

    // warn if as-service flag does not apply
    if (asService && (className == null && jarFile == null)) {
      System.err.println("NOTE: -s (--as-service) option only relevant with mainClass or -jar.");
    }

    // verify and set run options
    runOptions = EnumSet.noneOf(RunOptions.class);

    if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
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
    } else {
      runOptions.add(RunOptions.WITH_PALDIR);
    }

    if (kafkaServers == null && (log != null || inLog != null || outLog != null)) {
      fatalExit(null, PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN);
    }

    if (log != null) {
      // if logName is given, assign to both inLog and outLog
      if (inLog != null || outLog != null) {
        System.err.println(
            "WARNING: with --log (LOG), --in-log (IN_LOG) and"
                + " --out-log (OUT_LOG) options are ignored.");
      }
      inLog = outLog = log;
    }

    if (inLog != null) {
      runOptions.add(RunOptions.WITH_IN_LOG);
    }

    if (outLog != null) {
      runOptions.add(RunOptions.WITH_OUT_LOG);
    }

    // ensure that if offset was given, a log name to read from was also given
    if (logOffset != null && (inLog == null || inLog.equalsIgnoreCase("auto"))) {
      fatalExit(null, PeerException.FatalCode.ERROR_NO_LOG_GIVEN);
    }

    if (runOptions.contains(RunOptions.WITH_OUT_LOG) || tcpPub != null) {
      runOptions.add(RunOptions.WITH_TCP_PUB);
    }

    if (runOptions.contains(RunOptions.WITH_PALDIR) && interceptable) {
      runOptions.add(RunOptions.WITH_INTERCEPTS);
    }

    if (rpc != null) {
      runOptions.add(RunOptions.WITH_RPC);
    }

    if (jsonRpc != null) {
      runOptions.add(RunOptions.WITH_JSONRPC);
    }

    logger.info("Running with options: {}", runOptions);
  }

  /**
   * Finds and returns an available TCP port on the host.
   *
   * @return an open TCP port number
   * @throws IOException if an error occurs while attempting to open a socket
   */
  private static int findOpenPort() throws IOException {
    try (ServerSocket tmpSocket = new ServerSocket(0, 0)) {
      return tmpSocket.getLocalPort();
    }
  }

  /**
   * Adds miscellaneous properties to the application configuration.
   *
   * <p>This method sets the peer's UUID, directory URL, Kafka servers, and messaging addresses (TCP
   * PUB, RPC, JSONRPC) based on the current configuration and environment.
   */
  private void addMiscProperties() {
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
        palDirectoryUrl == null || palDirectoryUrl.isEmpty()
            ? PalDirectory.NO_URL
            : palDirectoryUrl);

    // add kafka servers if given
    if (kafkaServers != null) {
      properties.setProperty("kafka.bootstrap.servers", kafkaServers);
    }

    // are we publishing via TCP, or just internally
    if (tcpPub != null) {
      int port = 0;
      String hostname = ZmqProperties.DEFAULT_PUB_HOSTNAME;
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
      properties.setProperty(ZmqProperties.OUT_PUB_CHANNEL, format("tcp://%s:%d", hostname, port));
    } else {
      properties.setProperty(
          ZmqProperties.OUT_PUB_CHANNEL,
          ZmqProperties.inprocEndpoints.getProperty("out.pub.inproc"));
    }

    // are we listening for RPC requests
    if (rpc != null) {
      String hostname = ZmqProperties.DEFAULT_RPC_HOSTNAME;
      int port = 0;
      if (rpc.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for RPC");
        }
      } else {
        final String portStr;
        if (rpc.contains(":")) {
          hostname = Strings.stringBefore(rpc, ":");
          portStr = Strings.stringAfter(rpc, ":");
        } else {
          portStr = rpc;
        }
        try {
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
          fatalExit(e, PeerException.FatalCode.ERROR_PARSING_RPC_PORT_NUMBER);
        }
      }
      properties.setProperty("in.rpc", format("tcp://%s:%d", hostname, port));
      properties.setProperty("rpc.threadPoolSize", String.valueOf(rpcThreads));
    }

    // are we listening for JSONRPC requests
    if (jsonRpc != null) {
      String hostname = ZmqProperties.DEFAULT_JSONRPC_HOSTNAME;
      int port = 0;
      if (jsonRpc.equalsIgnoreCase("auto")) {
        try {
          port = findOpenPort();
        } catch (IOException e) {
          fatalExit(
              null,
              PeerException.FatalCode.ERROR_FINDING_RND_PORT,
              "Could not find random port for JSONRPC");
        }
      } else {
        final String portStr;
        if (jsonRpc.contains(":")) {
          hostname = Strings.stringBefore(jsonRpc, ":");
          portStr = Strings.stringAfter(jsonRpc, ":");
        } else {
          portStr = jsonRpc;
        }
        try {
          port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
          fatalExit(e, PeerException.FatalCode.ERROR_PARSING_JSONRPC_PORT_NUMBER);
        }
      }
      properties.setProperty("in.jsonrpc", format("ws://%s:%d", hostname, port));
      properties.setProperty("rpc.threadPoolSize", String.valueOf(rpcThreads));
    }

    // message content options
    properties.setProperty("messages.with_src_context", String.valueOf(includeSourceContext));

    // rpc options
    properties.setProperty("rpc.allow_nonpublic", String.valueOf(rpcAllowNonPublic));
  }

  /**
   * Retrieves the JMX connection address based on system properties and environment variables.
   *
   * @return a string in the format "host:port" representing the JMX address, or null if JMX is
   *     disabled
   */
  private String getJmxAddress() {
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
      } else if (localOnly != null && !"false".equalsIgnoreCase(localOnly)) {
        // if local.only, then we assume hostname = 'localhost'
        jmxRemoteHost = "localhost";
      }
    }
    if (jmxRemoteHost != null && jmxRemotePort != null) {
      return format("%s:%d", jmxRemoteHost, jmxRemotePort);
    } else {
      return null;
    }
  }

  /**
   * Creates and configures a custom classloader using the provided classpath entries and JAR file.
   *
   * <p>The custom classloader is used for dynamic class loading during peer execution.
   */
  private void createCustomClassloader() {
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

    this.customClassloader =
        new CustomClassloader(
            urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
  }

  /**
   * Registers the input and output Log configurations to be used by this peer with the Pal
   * directory.
   *
   * @param injector the Guice injector used to obtain the DirectoryConnectionProvider
   * @param self the PeerInfo instance representing this peer
   * @param inLog the input log configuration (can be null)
   * @param outLog the output log configuration (can be null)
   */
  private void registerLogsToUse(Injector injector, PeerInfo self, LogInfo inLog, LogInfo outLog) {
    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    try {
      if (inLog != null) {
        palDirectory.registerPeerInLog(self, inLog);
      }
      if (outLog != null) {
        palDirectory.registerPeerOutLog(self, outLog);
      }
    } catch (Exception ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_SELF_LOGS);
    }
  }

  /**
   * Registers this peer with the PAL directory.
   *
   * <p>A new PeerInfo instance is created, configured with available network interfaces and
   * addresses, and then registered with the PAL directory.
   *
   * @param injector the Guice injector used for obtaining necessary dependencies
   * @return the registered PeerInfo instance representing this peer
   */
  private PeerInfo registerSelfAsPeer(Injector injector) {

    final PalDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    PeerInfo self = null;

    // register self as new peer
    try {
      self = new PeerInfo(uuid);
      // public listening interfaces
      if (runOptions.contains(RunOptions.WITH_RPC)) {
        self.setRpcAddress(properties.getProperty("in.rpc"));
      }
      if (runOptions.contains(RunOptions.WITH_JSONRPC)) {
        self.setJsonrpcAddress(properties.getProperty("in.jsonrpc"));
      }
      if (properties
          .getProperty(ZmqProperties.OUT_PUB_CHANNEL)
          .startsWith("tcp://")) { // only register PUB address if over TCP
        self.setPubAddress(properties.getProperty(ZmqProperties.OUT_PUB_CHANNEL));
      }
      String jmxAddress = getJmxAddress();
      if (jmxAddress != null) {
        self.setJmxAddress(jmxAddress);
      }
      // other info
      if (name != null) {
        self.setName(name);
      }
      palDirectory.registerPeer(self);
    } catch (Exception ex) {
      fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_SELF);
    }
    logger.info("Registered self in directory with new PeerInfo: {}", self);
    return self;
  }

  /**
   * Creates a set of managed services based on the enabled runtime options.
   *
   * <p>Services may include Log reader/writer, message publisher, BIN-RPC and JSON-RPC dispatchers,
   * intercept matcher, and a session service if required.
   *
   * @param injector the Guice injector used to obtain service instances
   * @return a Set of Service instances to be managed
   */
  private Set<Service> createManagedServices(Injector injector) {
    final Set<Service> services = new HashSet<>();
    boolean sessionRequired = false;

    if (runOptions.contains(RunOptions.WITH_IN_LOG)) {
      services.add(injector.getInstance(LogReader.class));
      sessionRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_OUT_LOG)) {
      services.add(injector.getInstance(LogWriter.class));
    }
    if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
      services.add(injector.getInstance(MessagePublisher.class));
    }
    if (runOptions.contains(RunOptions.WITH_RPC)) {
      services.add(injector.getInstance(ZmqRpcRequestDispatcher.class));
      sessionRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_JSONRPC)) {
      services.add(injector.getInstance(JsonRpcRequestDispatcher.class));
      sessionRequired = true;
    }
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
      services.add(injector.getInstance(InterceptMatcher.class));
    }
    if (sessionRequired) {
      services.add(injector.getInstance(SessionService.class));
    }
    return services;
  }

  /**
   * Creates and configures a ServiceManager to manage the given services.
   *
   * <p>The manager listens for state changes, logs service startup times, and triggers fatal exit
   * on failure.
   *
   * @param services an iterable collection of Service instances to be managed
   * @return the configured ServiceManager instance
   */
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
          public void failure(@Nonnull Service service) {
            fatalExit(service.failureCause(), PeerException.FatalCode.ERROR_SERVICE_MANAGER_FAILED);
          }
        },
        MoreExecutors.directExecutor());
    return manager;
  }

  /**
   * Shuts down managed services, executors, and associated resources.
   *
   * <p>This includes stopping messaging executors, unregistering the peer from the directory,
   * closing custom classloader executors, and shutting down the ZeroMQ context.
   *
   * @param manager the ServiceManager instance managing the services (can be null)
   * @param injector the Guice injector used to obtain service instances for shutdown procedures
   */
  private void shutdown(ServiceManager manager, Injector injector) {
    if (logger.isInfoEnabled()) {
      logger.info("Shutting down...");
    }
    ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
    try {
      // stop services
      if (manager != null) {
        manager.stopAsync();
      }

      // stop peer executor (interrupts all peer exec threads)
      if (runOptions.contains(RunOptions.WITH_RPC)
          || runOptions.contains(RunOptions.WITH_JSONRPC)) {
        final ThreadPool rpcMessageExecutor = injector.getInstance(RpcMessageExecutor.class);
        rpcMessageExecutor.shutdown();
        logger.info("Done shutting down peer threads");
      }

      // stop log executor (interrupts all log exec threads)
      if (runOptions.contains(RunOptions.WITH_IN_LOG)) {
        final ThreadPool logMessageExecutor = injector.getInstance(LogMessageExecutor.class);
        logMessageExecutor.shutdown();
        logger.info("Done shutting down log threads");
      }

      // asynchronously shutdown custom classloader notifications executor
      singleExecutor.execute(() -> customClassloader.shutdown());

      // unregister self and close connection to paldir
      if (runOptions.contains(RunOptions.WITH_PALDIR)) {
        final Optional<PalDirectory> palDirectory =
            injector.getInstance(DirectoryConnectionProvider.class).get();
        palDirectory.ifPresent(
            dir -> {
              try {
                dir.unregisterPeer(this.uuid);
              } catch (Exception e) {
                logger.warn("Error unregistering self from PAL directory.", e);
              }
              dir.close();
            });
      }

      // close sockets that aren't automatically closed
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
        final InterceptInformer interceptInformer = injector.getInstance(InterceptInformer.class);
        interceptInformer.closeThreadLocalSocket();
      }

      // close zmq context asynchronously
      singleExecutor.execute(this::closeZmqContext);
      singleExecutor.shutdown();

      // wait a bit for services to stop
      if (manager != null) {
        manager.awaitStopped(SERVICE_MANAGER_AWAIT_TERM);
      }

      // wait a bit for exec service to finish closing zmq context
      boolean terminated =
          singleExecutor.awaitTermination(EXECUTOR_AWAIT_TERM.toMillis(), TimeUnit.MILLISECONDS);
      if (!terminated) {
        logger.debug("Executor service did not terminate gracefully.");
      }

      // in case we're running asService and manager == null
      if (manager == null) {
        runAsServiceLatch.countDown();
      }
    } catch (TimeoutException ie) {
      logger.error("Timeout exception in shutdown hook", ie);
    } catch (InterruptedException e) {
      logger.error("Interrupted while shutting down", e);
    } finally {
      logger.info("This peer is done! bye");
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
        logger.warn("ignoring unexpected msg: '{}'", received);
      }
    }
    syncSocket.close();
  }

  /**
   * Main entry point of the peer application.
   *
   * <p>Parses command-line arguments using Picocli and executes the Main callable. The application
   * exits with the return code from the executed main class or JAR.
   *
   * @param args command-line arguments
   */
  public static void main(final String[] args) {
    CommandLine commandLine = new CommandLine(new Main());
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }

  /**
   * Executes the peer startup sequence.
   *
   * <p>This method performs logging setup, property loading, ZeroMQ context initialization,
   * dependency injection, service registration, and optionally self-executes a main class or JAR
   * file. It returns the exit code resulting from the main class or JAR execution.
   *
   * @return the exit code from executing the main class or JAR file
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  @Override
  public Integer call() throws InterruptedException {

    initLogging();
    // for async calls
    final ExecutorService singleExecutor = Executors.newSingleThreadExecutor();

    setEmptyParamsFromEnv();
    validateInput();
    loadProps();

    // initialize ZMQ and local sockets
    initZmqContext();

    // add zmq channel names to properties
    properties.putAll(ZmqProperties.inprocEndpoints);

    // add misc variables to app props
    addMiscProperties();

    // init custom classloader
    createCustomClassloader();

    // tell our resource bundle control provider to use our custom classloader
    DynamicResourceBundleControlProvider.setClassLoaderResolver(
        (baseName, locale) -> customClassloader);

    // inject dependencies
    final Injector injector =
        Guice.createInjector(new PeerWiring(properties, runOptions, zmqContext, customClassloader));

    // add the annotations processor as classloader listener
    if (!disableAnnotationProcessing) {
      customClassloader.addClassLoadListener(injector.getInstance(AnnotationsProcessor.class));
    }

    // init logs IO
    LogConfigurator logConfigurator = null;
    if (runOptions.contains(RunOptions.WITH_IN_LOG)
        || runOptions.contains(RunOptions.WITH_OUT_LOG)) {
      try {
        logConfigurator = new LogConfigurator(inLog, logOffset, outLog, properties, injector);
        logConfigurator.init();
      } catch (Exception ex) {
        fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS);
      }
    }

    // register self in directory
    final CountDownLatch selfRegistrationLatch = new CountDownLatch(1);
    if (runOptions.contains(RunOptions.WITH_PALDIR)) {
      final LogInfo peerInLog =
          logConfigurator != null ? logConfigurator.getInLog().orElse(null) : null;
      final LogInfo peerOutLog =
          logConfigurator != null ? logConfigurator.getOutLog().orElse(null) : null;
      singleExecutor.execute(
          () -> {
            PeerInfo self = registerSelfAsPeer(injector);
            registerLogsToUse(injector, self, peerInLog, peerOutLog);
            selfRegistrationLatch.countDown();
          });
    }

    // set up managed services
    final Set<Service> services = createManagedServices(injector);
    final ServiceManager manager = !services.isEmpty() ? createServiceManager(services) : null;

    // add shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(manager, injector)));

    // start services
    if (manager != null) { // manager = null if there are no services
      manager.startAsync();
    }

    // block until we're registered in Directory
    if (runOptions.contains(RunOptions.WITH_PALDIR)) {
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

    // start listening to intercept requests
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
      final PalDirectory palDirectory =
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
    if (runOptions.contains(RunOptions.WITH_IN_LOG)) {
      LogReader logMessageReader = injector.getInstance(LogReader.class);
      logMessageReader.acceptRequests(true);
      injector.getInstance(LogMessageExecutor.class).startAllThreads();
    }

    // pre-start threads to create the REP sockets; this must be done after DEALER
    if (runOptions.contains(RunOptions.WITH_RPC) || runOptions.contains(RunOptions.WITH_JSONRPC)) {
      injector.getInstance(RpcMessageExecutor.class).startAllThreads();
    }

    // now call target (main class or JAR file), if given
    boolean mainCalled = false;
    int returnValue = 0;
    if (className != null) {
      // self-call className.main() if given, and then we're done
      returnValue = injector.getInstance(SelfCaller.class).callMain(className, argList);
      mainCalled = true;
    } else if (jarFile != null) { // NOTE: jarFile was previously added to classpath
      // self-call Main-Class found in manifest
      try {
        returnValue = injector.getInstance(SelfCaller.class).callJar(jarFile, argList);
      } catch (PeerException e) {
        fatalExit(e);
      }
      mainCalled = true;
    }

    if (!mainCalled || asService) {
      if (manager != null) {
        manager.awaitStopped();
      } else {
        runAsServiceLatch.await();
      }
    }
    return returnValue;
  }
}
