package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.exec.java.CustomClassloader;
import com.ittera.cometa.cxn.PeerLogDirectory;

import com.ittera.cometa.concentrator.exec.PeerMessageExecutor;
import com.ittera.cometa.concentrator.exec.LogMessageExecutor;
import com.ittera.cometa.concentrator.exec.ExtendedThreadPoolExecutor;

import java.io.InputStream;
import java.io.File;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.MoreExecutors;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class Concentrator {

	private static final Logger logger = LoggerFactory.getLogger(Concentrator.class);

	private static UUID uuid;

	private static final Properties properties = new Properties();

	// zmq context -- gets injected to all other threads
	private static final ZContext zmqContext;

	private static final String PROPERTIES_FILE = "/peer.properties";

	private static final String LOGGING_CONFIG = "/peer-logging.xml";

	// defaults for properties
	private static final String ZMQ_LINGER_DEFAULT = "1000";
	private static final String ZMQ_RCVHWM_DEFAULT = "10000";
	private static final String ZMQ_SNDHWM_DEFAULT = "10000";

	static {
		// configure logging
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		context.reset();
		try (final InputStream stream = Concentrator.class.getResourceAsStream(LOGGING_CONFIG)) {
			configurator.doConfigure(stream);
		} catch (Exception ie) {
			System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
			// for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
			ie.printStackTrace();
		}

		// load properties from file in classpath
		try (final InputStream stream = Concentrator.class.getResourceAsStream(PROPERTIES_FILE)) {
			properties.load(stream);
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_LOADING_PROPERTIES,
				String.format("Make sure to have `%s` in the classpath", PROPERTIES_FILE));
		}

		// initialize zmq context
		zmqContext = new ZContext();
		zmqContext.setLinger(Integer.parseInt(properties.getProperty("ZMQ_LINGER", ZMQ_LINGER_DEFAULT)));
		zmqContext.setRcvHWM(Integer.parseInt(properties.getProperty("ZMQ_RCVHWM", ZMQ_RCVHWM_DEFAULT)));
		zmqContext.setSndHWM(Integer.parseInt(properties.getProperty("ZMQ_SNDHWM", ZMQ_SNDHWM_DEFAULT)));
		logger.info("Created and configured zmq context");
	}

	private static void terminateProxies() {
		String proxyCtrlAddress = Concentrator.properties.getProperty("in.proxy.ctrl");
		ZMQ.Socket ctrl = zmqContext.createSocket(SocketType.PAIR);
		ctrl.connect(proxyCtrlAddress);
		ctrl.send(ZMQ.PROXY_TERMINATE);
		ctrl.close();
		logger.info("Sent TERM cmd to proxies");
	}

	private static void closeZmqContext() {
		zmqContext.close();
		logger.info("Closed zmq context");
	}

	private static void fatalExit(Throwable ex, PeerFatalCode fatalCode) {
		fatalExit(ex, fatalCode, null);
	}

	private static void fatalExit(Throwable ex, PeerFatalCode fatalCode, String extraMessage) {
		if (ex != null) {
			logger.error(fatalCode.getMessage(), ex);
		}
		System.err.println(fatalCode.getMessage());
		if (extraMessage != null) {
			System.err.println(extraMessage);
		}
		if (ex != null) {
			ex.printStackTrace();
		}
		System.exit(fatalCode.getCode());
	}

	private static void addEnvToProperties() {

		// load from Environment variable or system property
		String zookeeperUrl = System.getenv("ZOOKEEPER_URL");
		if (zookeeperUrl == null) {
			zookeeperUrl = System.getProperty("zookeeper_url");
		}

		if (zookeeperUrl == null) {
			fatalExit(null, PeerFatalCode.ERROR_NO_ZOOKEEPER_URL_GIVEN);
		}
		// add to app properties
		Concentrator.properties.setProperty("zookeeper_url", zookeeperUrl);
	}

	private static void registerSelfAsPeer(Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);

		// connect to directory
		try {
			registry.connect(Concentrator.properties.getProperty("zookeeper_url"));
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_CONNECTING_TO_DIRECTORY);
		}

		// register self as new peer
		try {
			final Properties peerProperties = new Properties();
			peerProperties.put("listenAddress", Concentrator.properties.getProperty("in.router"));
			registry.registerPeer(uuid, peerProperties);
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_REGISTERING_PEER);
		}
	}

	public static void main(final String[] args) {

		logger.info("peer::main called w/args: {}", String.join(" ", args));

		// parse options
		PeerOptions options = PeerOptions.parse(args);
		if (options.helpNeeded) {
			options.printHelp();
			System.exit(0);
		}
		// set this peer's uuid
		uuid = options.uuid != null ? options.uuid : UUID.randomUUID();
		properties.put("id", uuid.toString());

		// check and add env variables to app props
		addEnvToProperties();

		// init custom classloader
		List<URL> urls = new ArrayList<>();
		if (options.classpath != null) {
			// split by ':' and add each entry: each should be either a folder or a JAR (just as in $CLASSPATH)
			Arrays.stream(options.classpath.split(":"))
				.map(File::new)
				.forEach(f -> {
					try {
						urls.add(f.toURI().toURL());
					} catch (MalformedURLException ex) {
						logger.error("Error adding classpath entry as URL for custom classloader", ex);
					}
				});
		}
		CustomClassloader customClassloader = new CustomClassloader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
		logger.info("initialized custom classloader with paths: {}", urls.toString());

		// inject dependencies
		final Injector injector = Guice.createInjector(new PeerGuiceModule(properties, zmqContext, customClassloader));

		// register peer
		registerSelfAsPeer(injector);

		// init logs IO
		if (options.offsetGiven && options.inLog == null) {
			fatalExit(null, PeerFatalCode.ERROR_NO_LOG_GIVEN);
		}

		try {
			new LogConfigurator(options, properties, injector).init();
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_INITIALIZING_LOGS);
		}

		// set up managed services
		final Set<Service> services = new HashSet<>();
		services.add(injector.getInstance(LogReader.class));
		services.add(injector.getInstance(LogWriter.class));
		services.add(injector.getInstance(JeromqOutMessageDispatcher.class));
		services.add(injector.getInstance(JeromqInRequestDispatcher.class));

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
			}

			public void failure(Service service) {
				fatalExit(service.failureCause(), PeerFatalCode.ERROR_SERVICE_MANAGER_FAILED);
			}
		}, MoreExecutors.directExecutor());

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				// terminate zmq proxies
				terminateProxies();

				// close/destroy zmq context
				closeZmqContext();

				// stop peer executor (interrupts all peer exec threads)
				final ExtendedThreadPoolExecutor peerMessageExecutor = injector.getInstance(PeerMessageExecutor.class);
				peerMessageExecutor.shutdownNow();
				logger.info("done shutting down peer threads");

				// stop log executor (interrupts all log exec threads)
				final ExtendedThreadPoolExecutor logMessageExecutor = injector.getInstance(LogMessageExecutor.class);
				logger.info("done shutting down log threads");
				logMessageExecutor.shutdownNow();

				// stop all services
				manager.stopAsync().awaitStopped(3, TimeUnit.SECONDS);

			} catch (TimeoutException ie) {
				logger.error("Timeout exception in shutdown hook", ie);
			} finally {
				logger.info("This peer is done! bye");
			}
		}));

		//start services
		manager.startAsync();
	}
}
