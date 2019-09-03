package com.ittera.cometa.core;

import com.ittera.cometa.core.exec.java.SelfCaller;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.core.exec.PeerMessageExecutor;
import com.ittera.cometa.core.exec.LogMessageExecutor;
import com.ittera.cometa.core.exec.ExtendedThreadPoolExecutor;
import com.ittera.cometa.core.exec.java.CustomClassloader;

import java.io.InputStream;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.MoreExecutors;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static picocli.CommandLine.Option;

import org.zeromq.ZContext;

@Command(name = "peer")
public class Main implements Callable<Integer> {

	@Option(names = {"-u", "--use-uuid"}, paramLabel = "PEER_UUID", description = "use given uuid")
	private UUID uuid;

	@Option(names = {"-r", "--read-log"}, paramLabel = "LOGNAME", description = "read from given log")
	private String inLogName;

	@Option(names = {"-s", "--offset-start"}, paramLabel = "OFFSET_START",
		description = "read from given offset (requires -l or -r)")
	private Long inLogOffset;

	@Option(names = {"-w", "--write-log"}, paramLabel = "LOGNAME", description = "write to given log")
	private String outLogName;

	@Option(names = {"-l", "--log"}, paramLabel = "LOGNAME", description = "read and write from/to given log")
	private String logName;

	@Option(names = {"-cp", "--classpath"}, paramLabel = "CLASSPATH",
		description = "load classes from given folders/jars")
	private String classpath;

	@Option(names = {"-jar"}, paramLabel = "JAR_FILE", description = "execute jar file")
	private String jarFile;

	@Parameters(hidden = true)
	private List<String> cmdArgList;

	// argList and className will be populated from cmdArgList
	private List<String> argList;
	private String className;

	@Option(names = {"-d", "--as-daemon"}, description = "keep running after call to mainClass/jar returns")
	private boolean asDaemon = false;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
	private boolean helpRequested = false;

	// app properties
	private final Properties properties = new Properties();

	// zmq context
	private ZContext zmqContext;

	// STATIC variables
	private static final Logger logger = LoggerFactory.getLogger(Main.class);
	private static final String PROPERTIES_FILE = "/peer.properties";
	private static final String LOGGING_CONFIG = "/peer-logging.xml";

	// defaults for ZMQ properties
	private static final String ZMQ_LINGER_DEFAULT = "1000";
	private static final String ZMQ_RCVHWM_DEFAULT = "10000";
	private static final String ZMQ_SNDHWM_DEFAULT = "10000";

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
			fatalExit(ex, PeerException.FatalCode.ERROR_LOADING_PROPERTIES,
				String.format("Make sure to have `%s` in the classpath", PROPERTIES_FILE));
		}
		logger.info("Loaded application properties from `{}`", PROPERTIES_FILE);
	}

	private void initZContext() {
		zmqContext = new ZContext();
		zmqContext.setLinger(Integer.parseInt(properties.getProperty("ZMQ_LINGER", ZMQ_LINGER_DEFAULT)));
		zmqContext.setRcvHWM(Integer.parseInt(properties.getProperty("ZMQ_RCVHWM", ZMQ_RCVHWM_DEFAULT)));
		zmqContext.setSndHWM(Integer.parseInt(properties.getProperty("ZMQ_SNDHWM", ZMQ_SNDHWM_DEFAULT)));
		logger.info("Created and configured zmq context");
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
		} else if (cmdArgList != null) {  // else, first is considered the mainClass
			className = cmdArgList.get(0);
			argList = cmdArgList.subList(1, cmdArgList.size());
		}

		if (asDaemon && (className == null && jarFile == null)) {
			System.err.println("WARNING: -d (--as-daemon) option only relevant with mainClass or -jar.");
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

	private void addEnvToProperties() {

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
		logger.info("Added env variables to properties");
	}

	private void registerSelfAsPeer(Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);

		// connect to directory
		try {
			registry.connect(properties.getProperty("zookeeper_url"));
		} catch (Exception ex) {
			fatalExit(ex, PeerException.FatalCode.ERROR_CONNECTING_TO_DIRECTORY);
		}

		// register self as new peer
		try {
			final Properties peerProperties = new Properties();
			peerProperties.put("listenAddress", properties.getProperty("in.router"));
			registry.registerPeer(uuid, peerProperties);
		} catch (Exception ex) {
			fatalExit(ex, PeerException.FatalCode.ERROR_REGISTERING_PEER);
		}
		logger.info("Registered self in peer directory");
	}

	private void shutdown(ServiceManager manager, Injector injector, boolean fast) {
		ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
		try {
			// stop services
			manager.stopAsync();

			// stop peer executor (interrupts all peer exec threads)
			final ExtendedThreadPoolExecutor peerMessageExecutor = injector.getInstance(PeerMessageExecutor.class);
			peerMessageExecutor.shutdownNow();
			logger.info("Done shutting down peer threads");

			// stop log executor (interrupts all log exec threads)
			final ExtendedThreadPoolExecutor logMessageExecutor = injector.getInstance(LogMessageExecutor.class);
			logMessageExecutor.shutdownNow();
			logger.info("Done shutting down log threads");

			// close zmq context asynchronously
			singleExecutor.submit(() -> closeZmqContext());
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

		// set this peer's uuid if not given
		if (uuid == null) {
			uuid = UUID.randomUUID();
		}
		properties.put("id", uuid.toString());

		// add env variables to app props
		addEnvToProperties();

		// init custom classloader
		List<URL> urls = new ArrayList<>();
		if (classpath != null) {
			// split by ':' and add each entry: each should be either a folder or a JAR (just as in $CLASSPATH)
			Arrays.stream(classpath.split(":"))
				.map(File::new)
				.forEach(f -> {
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

		CustomClassloader customClassloader = new CustomClassloader(urls.toArray(new URL[0]),
			Thread.currentThread().getContextClassLoader());
		logger.info("Initialized custom classloader with paths: {}", urls.toString());

		// inject dependencies
		final Injector injector = Guice.createInjector(new PeerGuiceModule(properties, zmqContext, customClassloader));

		// register peer
		registerSelfAsPeer(injector);

		// init logs IO
		try {
			new LogConfigurator(inLogName, inLogOffset, outLogName, properties, injector).init();
		} catch (Exception ex) {
			fatalExit(ex, PeerException.FatalCode.ERROR_INITIALIZING_LOGS);
		}

		// set up managed services
		final Set<Service> services = new HashSet<>();
		services.add(injector.getInstance(LogReader.class));
		services.add(injector.getInstance(LogWriter.class));
		services.add(injector.getInstance(OutgoingMessageDispatcher.class));
		services.add(injector.getInstance(DirectRequestDispatcher.class));

		final ServiceManager manager = new ServiceManager(services);

		manager.addListener(new ServiceManager.Listener() {
			public void stopped() {
				logger.info("Service manager stopped.");
			}

			public void healthy() {
				// start accepting requests
				logger.info("Service manager is healthy.");
				LogReader logMessageReader = injector.getInstance(LogReader.class);
				logMessageReader.acceptConnections(true);

				// We must prestart threads to create the REP sockets, and this must be done after DEALER
				injector.getInstance(PeerMessageExecutor.class).prestartAllCoreThreads();
				injector.getInstance(LogMessageExecutor.class).prestartAllCoreThreads();

				// now call target (main class or JAR file), if given
				boolean selfCalled = false;
				if (className != null) {
					// self-call className.main() if given, and then we're done
					injector.getInstance(SelfCaller.class).callMain(className, argList);
					selfCalled = true;
				} else if (jarFile != null) { // NOTE: jarFile was previously added to classpath
					// self-call Main-Class found in manifest, and we're done
					try {
						injector.getInstance(SelfCaller.class).callJar(jarFile, argList);
					} catch (PeerException e) {
						fatalExit(e);
					}
					selfCalled = true;
				}
				if (selfCalled && !asDaemon) {
					shutdown(manager, injector, true);
				}
			}

			public void failure(Service service) {
				fatalExit(service.failureCause(), PeerException.FatalCode.ERROR_SERVICE_MANAGER_FAILED);
			}
		}, MoreExecutors.directExecutor());

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(manager, injector, false)));

		// start services
		manager.startAsync();

		// wait here
		manager.awaitStopped();

		return 0;
	}
}
