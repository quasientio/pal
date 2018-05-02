package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.concentrator.exec.java.IncomingProxyDispatcher;
import com.ittera.cometa.cxn.PeerLogDirectory;

import com.ittera.cometa.concentrator.exec.*;

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

	protected static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

	static {

		// load properties from file in classpath
		try (final InputStream stream = Concentrator.class.getResourceAsStream(PROPERTIES_FILE)) {
			properties.load(stream);
		} catch (Exception e) {
			logger.error("Could not load properties", e);
			System.err.println(String.format("Could not load properties. Make sure to have `%s` in the classpath",
				PROPERTIES_FILE));
			e.printStackTrace();
			System.exit(1);
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

	// <editor-fold defaultstate="collapsed" desc="PEER INIT METHODS">
	private static void registerSelfAsPeer(Properties properties, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);

		// connect to directory
		try {
			registry.connect(properties.getProperty("zookeeper.url"));
		} catch (Exception ex) {
			logger.error("Error connecting to directory", ex);
			ex.printStackTrace();
			System.exit(3);
		}

		// register self as new peer
		try {
			final Properties peerProperties = new Properties();
			peerProperties.put("listenAddress", properties.getProperty("in.router"));
			registry.registerPeer(uuid, peerProperties);
		} catch (Exception ex) {
			logger.error("Error registering peer", ex);
			ex.printStackTrace();
			System.exit(4);
		}
	}

	private static LogInfo registerNewLog(Properties properties, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		final String kafkaTopicPrefix = properties.getProperty("kafkaTopic");
		LogInfo newLogInfo = null;

		// register new log
		try {
			newLogInfo = registry.createLog(kafkaTopicPrefix, DEFAULT_BOOTSTRAP_SERVERS);
		} catch (Exception ex) {
			logger.error("Error registering new log", ex);
			ex.printStackTrace();
			System.exit(5);
		}

		return newLogInfo;
	}

	private static LogInfo registerGivenLog(LogInfo givenLogInfo, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		LogInfo logInfo = null;

		// register given log if not registered
		try {
			if (registry.logExists(givenLogInfo.getName())) {
				logInfo = registry.getLogInfo(givenLogInfo.getName());
			} else {
				logInfo = registry.addGivenLog(givenLogInfo.getName(), DEFAULT_BOOTSTRAP_SERVERS);
			}
		} catch (Exception ex) {
			logger.error("Error registering given log", ex);
			ex.printStackTrace();
			System.exit(5);
		}

		return logInfo;
	}

	private static void readFromLog(LogInfo log, Injector injector, boolean inAndOutAreSameLog, Long initialOffset) {
		KafkaMessageReader kafkaMessageReader = injector.getInstance(KafkaMessageReader.class);
		try {
			boolean skipWrittenOffsets = inAndOutAreSameLog; // for clarity
			kafkaMessageReader.readFromLog(log.getName(), skipWrittenOffsets, initialOffset);
		} catch (Exception ex) {
			logger.error("Could not initialize log reader. Aborting ...", ex);
			ex.printStackTrace();
			System.exit(6);
		}
	}

	private static void writeToLog(LogInfo outLog, LogInfo inLog, Injector injector) {
		KafkaMessageWriter kafkaMessageWriter = injector.getInstance(KafkaMessageWriter.class);
		try {
			boolean publishOffsets = outLog.equals(inLog);
			kafkaMessageWriter.writeToLog(outLog, inLog, publishOffsets);
		} catch (Exception ex) {
			logger.error("Could not initialize log writer. Aborting ...", ex);
			ex.printStackTrace();
			System.exit(6);
		}
	}
	// </editor-fold>

	public static void main(final String[] args) {

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			for (String arg : args) {
				sb.append(arg).append(" ");
			}
			logger.info("::main called w/args: {}", sb);
		}

		PeerOptions options = PeerOptions.parse(args);
		if (options.helpNeeded) {
			options.printHelp();
			System.exit(0);
		}
		// set uuid
		if (options.uuid != null) {
			uuid = options.uuid;
		} else {
			uuid = UUID.randomUUID();
		}
		properties.put("id", uuid.toString());

		final Injector injector = Guice.createInjector(new PeerGuiceModule(properties, zmqContext));

		// register peer
		registerSelfAsPeer(properties, injector);

		// init log IO
		if (options.offsetGiven && options.inLog == null) {
			System.err.println("Offset given but no given log to read from. Try `runner -h`.");
			System.exit(1);
		}

		// register log(s)
		LogInfo inLog, outLog, newLog = null;

		if (options.inLog != null) {
			inLog = registerGivenLog(options.inLog, injector);
		} else { // no log given, create new
			inLog = registerNewLog(properties, injector);
			newLog = inLog;
		}

		if (options.outLog != null) {
			outLog = registerGivenLog(options.outLog, injector);
		} else { // no log given, create new if not done already
			if (newLog == null) {
				newLog = registerNewLog(properties, injector);
			}
			outLog = newLog;
		}

		// init log reader+writer
		boolean inAndOutAreSame = inLog.equals(outLog);
		readFromLog(inLog, injector, inAndOutAreSame, options.offset);
		writeToLog(outLog, inLog, injector);

		// managed services
		final Set<Service> services = new HashSet<>();
		services.add((Service) injector.getInstance(KafkaMessageReader.class));
		services.add((Service) injector.getInstance(OutgoingMessageDispatcher.class));
		services.add(injector.getInstance(KafkaDataMessageWriter.class));
		services.add(injector.getInstance(JeromqInRequestDispatcher.class));

		final ServiceManager manager = new ServiceManager(services);

		manager.addListener(new ServiceManager.Listener() {
			public void stopped() {
				logger.info("Service manager stopped.");
			}

			public void healthy() {
				// start accepting requests...
				logger.info("Service manager is healthy.");
				KafkaMessageReader kafkaMessageReader = injector.getInstance(KafkaMessageReader.class);
				kafkaMessageReader.acceptConnections(true);

				// We must prestart threads to create the REP sockets, and this must be done after DEALER
				ExtendedThreadPoolExecutor executor = (PeerMessageExecutor) injector.getInstance(PeerExecutor.class);
				executor.prestartAllCoreThreads();
				executor = (LogMessageExecutor) injector.getInstance(LogExecutor.class);
				executor.prestartAllCoreThreads();
			}

			public void failure(Service service) {
				logger.error("Service manager failed. Exiting ...", service.failureCause());
				System.exit(7);
			}
		}, MoreExecutors.directExecutor());

		// add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				// destroy context
				Concentrator.closeZmqContext();

				// stop peer executor (interrupts all peer exec threads)
				final ExtendedThreadPoolExecutor peerMessageExecutor =
					(PeerMessageExecutor) injector.getInstance(PeerExecutor.class);
				logger.info("shutting down peer threads");
				peerMessageExecutor.shutdownNow();

				// stop log executor (interrupts all log exec threads)
				final ExtendedThreadPoolExecutor logMessageExecutor =
					(LogMessageExecutor) injector.getInstance(LogExecutor.class);
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
