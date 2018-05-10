package com.ittera.cometa.concentrator;

import com.ittera.cometa.cxn.PeerLogDirectory;

import com.ittera.cometa.concentrator.exec.PeerMessageExecutor;
import com.ittera.cometa.concentrator.exec.LogMessageExecutor;
import com.ittera.cometa.concentrator.exec.ExtendedThreadPoolExecutor;

import java.io.InputStream;

import java.util.Properties;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.MoreExecutors;

import org.zeromq.ZContext;

public class Concentrator {

	protected static final Logger logger = LoggerFactory.getLogger(Concentrator.class);

	public static UUID uuid;

	private static final Properties properties = new Properties();

	// zmq context -- gets injected to all other threads
	protected static final ZContext zmqContext;

	protected static final String PROPERTIES_FILE = "/peer.properties";

	// defaults for properties
	private static final String ZMQ_LINGER_DEFAULT = "1000";
	private static final String ZMQ_RCVHWM_DEFAULT = "10000";
	private static final String ZMQ_SNDHWM_DEFAULT = "10000";

	static {
		// load properties from file in classpath
		try (final InputStream stream = Concentrator.class.getResourceAsStream(PROPERTIES_FILE)) {
			properties.load(stream);
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_LOADING_PROPERTIES,
				String.format("Make sure to have `%s` in the classpath", PROPERTIES_FILE));
		}

		// initialize zmq context
		zmqContext = new ZContext();
		zmqContext.setLinger(Integer.valueOf(properties.getProperty("ZMQ_LINGER", ZMQ_LINGER_DEFAULT)));
		zmqContext.setRcvHWM(Integer.valueOf(properties.getProperty("ZMQ_RCVHWM", ZMQ_RCVHWM_DEFAULT)));
		zmqContext.setSndHWM(Integer.valueOf(properties.getProperty("ZMQ_SNDHWM", ZMQ_SNDHWM_DEFAULT)));
	}

	private static void closeZmqContext() {
		logger.info("Destroying zmq context");
		zmqContext.destroy();
		logger.info("Destroyed zmq context");
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

	private static void addEnvToProperties(Properties properties) {

		// load from Environment variable or system property
		String zookeeperUrl = System.getenv("ZOOKEEPER_URL");
		if (zookeeperUrl == null) {
			zookeeperUrl = System.getProperty("zookeeper_url");
		}

		// add to app properties
		// TODO if zookeeper_url not present throw new fatal exception
		properties.setProperty("zookeeper_url", zookeeperUrl);
	}

	private static void registerSelfAsPeer(Properties properties, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);

		// connect to directory
		try {
			registry.connect(properties.getProperty("zookeeper_url"));
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_CONNECTING_TO_DIRECTORY);
		}

		// register self as new peer
		try {
			final Properties peerProperties = new Properties();
			peerProperties.put("listenAddress", properties.getProperty("in.router"));
			registry.registerPeer(uuid, peerProperties);
		} catch (Exception ex) {
			fatalExit(ex, PeerFatalCode.ERROR_REGISTERING_PEER);
		}
	}

	public static void main(final String[] args) {

		if (logger.isInfoEnabled()) {
			logger.info("peer::main called w/args: {}", String.join(" ", args));
		}

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
		addEnvToProperties(properties);

		// inject dependencies
		final Injector injector = Guice.createInjector(new PeerGuiceModule(properties, zmqContext));

		// register peer
		registerSelfAsPeer(properties, injector);

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
		services.add(injector.getInstance(KafkaDataMessageReader.class));
		services.add(injector.getInstance(KafkaDataMessageWriter.class));
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
				KafkaDataMessageReader logMessageReader = injector.getInstance(KafkaDataMessageReader.class);
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
				// destroy context
				closeZmqContext();

				// stop peer executor (interrupts all peer exec threads)
				final ExtendedThreadPoolExecutor peerMessageExecutor = injector.getInstance(PeerMessageExecutor.class);
				logger.info("shutting down peer threads");
				peerMessageExecutor.shutdownNow();

				// stop log executor (interrupts all log exec threads)
				final ExtendedThreadPoolExecutor logMessageExecutor = injector.getInstance(LogMessageExecutor.class);
				logger.info("shutting down log threads");
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
