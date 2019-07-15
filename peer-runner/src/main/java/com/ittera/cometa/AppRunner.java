package com.ittera.cometa;

import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.common.lang.ObjectRef;

import java.util.Queue;
import java.util.UUID;
import java.util.Properties;
import java.util.List;

import java.io.InputStream;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

@Command(name = "runner")
public class AppRunner implements Callable<Integer> {

	protected static final Logger logger = LoggerFactory.getLogger(AppRunner.class);
	protected static final String RUNNER_PROPERTIES_PATH = "/runner.properties";
	protected static final long REPLY_PROCESSOR_SLEEP_MS = 100;
	protected final DataMessageBuilder dataMessageBuilder;

	/**
	 * Normal Options
	 */
	@Option(names = {"-n", "--num-requests"}, defaultValue = "1", paramLabel = "NUM_REQUESTS", description = "number of requests to send")
	protected int requests;

	@Option(names = {"-c", "--num-clients"}, defaultValue = "1", paramLabel = "NUM_CLIENTS", description = "number of clients to use")
	protected int clients;

	@Option(names = {"-r", "--read-log"}, paramLabel = "IN_LOGNAME", description = "read from given log")
	protected String inLogName;

	@Option(names = {"-w", "--write-log"}, paramLabel = "OUT_LOGNAME", description = "write to given log")
	protected String outLogName;

	@Option(names = {"-l", "--log"}, paramLabel = "LOGNAME", description = "read and write from/to given log")
	protected String logName;

	@Option(names = {"-a", "--async"}, description = "send to log in async mode")
	protected boolean async;

	@Option(names = {"-f", "--forget-reply"}, description = "do not wait for replies")
	protected boolean sendAndForget;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
	protected boolean helpRequested = false;

	@Option(names = "-v", description = "run verbosely")
	protected boolean verbose;

	/**
	 * Parameters
	 */
	@Parameters(index = "0")
	protected String className;

	protected String methodName = "main";

	@Parameters(index = "1..*")
	protected List<String> argList;

	AppRunner() {
		this.dataMessageBuilder = new ProtobufDataMessageBuilder();
	}

	/**
	 * Serially sends all requests in a single (ThinPeer) thread.
	 * Sends 1st req to log and waits for Future reply, then sends all other directly to peer
	 */
	protected int runReqsWithSingleClient() throws Exception {

		// load properties and init ThinPeer
		ThinPeer thinPeer;
		if (logName != null) {
			inLogName = outLogName = logName;
		}
		LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
		LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);

		final Properties properties = new Properties();
		try (final InputStream stream = AppRunner.class.getResourceAsStream(RUNNER_PROPERTIES_PATH)) {
			properties.load(stream);
		}
		thinPeer = new ThinPeer(properties, inLog, outLog);

		long start = System.currentTimeMillis();
		int reqsSent = 0;
		DataMessage replyMsg;
		Future<DataMessage> messageFuture;

		// prepare arrays for message construction
		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};
		// TODO: generalize this to other methods (non-varargs)
		if ("main".equals(methodName) && argList != null) {
			parameters[0] = argList.toArray(new String[0]);
		}

		// send 1st request
		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
			parameterTypesNamesArray, this, null, parameters, new ObjectRef[parameterTypes.length]);
		messageFuture = thinPeer.sendToLogAsync(requestMsg);
		reqsSent++;

		// wait for reply (blocking)
		logger.debug("Waiting for async reply ...");
		replyMsg = messageFuture.get();
		logger.debug("Got 1st (future) reply w/uuid: {}", replyMsg.getMessageUuid());

		// switch to direct p2p talk
		String concentratorUuid = replyMsg.getConcentratorUuid();
		PeerInfo newPeer = null;
		thinPeer.connectToPeer(UUID.fromString(concentratorUuid));

		// send rest of requests
		for (; reqsSent < requests; reqsSent++) {
			requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
				parameterTypesNamesArray, this, null, parameters, new ObjectRef[parameterTypes.length]);
			thinPeer.sendAndReceive(requestMsg);
		}

		thinPeer.close();

		if (verbose) {
			System.out.println(String.format("sent and received %s requests in %s ms", reqsSent,
				(System.currentTimeMillis() - start)));
		}

		return reqsSent;
	}

	/**
	 * Use this method when no direct peer-to-peer talk is available or desirable.
	 * Sends all requests asynchronously to log, waits for reply offsets in directory, then fetches them from log.
	 * If sendAndForget=true, it doesn't wait for replies, useful for void methods or any other type of call where
	 * we don't care about the returned value or thrown exceptions.
	 */
	protected int runReqsWithSingleClientAsync()
		throws Exception {

		// load properties and init ThinPeer
		ThinPeer thinPeer;
		if (logName != null) {
			inLogName = outLogName = logName;
		}
		LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
		LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);
		final Properties properties = new Properties();
		try (final InputStream stream = AppRunner.class.getResourceAsStream(RUNNER_PROPERTIES_PATH)) {
			properties.load(stream);
		}
		thinPeer = new ThinPeer(properties, inLog, outLog);

		long start = System.currentTimeMillis();
		int reqsSent = 0;
		Future<DataMessage> messageFuture;

		// a queue to store futures (async mode)
		final Queue<Future<DataMessage>> messageFutureQueue = new ConcurrentLinkedQueue<>();
		Thread replyProcessorThread = null;
		if (!sendAndForget) {
			replyProcessorThread = new Thread(() -> {
				int totalProcessed = 0;
				int processed;
				while (totalProcessed < requests) {
					processed = 0;
					for (Future<DataMessage> futureReply : messageFutureQueue) {
						if (futureReply.isDone()) {
							messageFutureQueue.remove(futureReply);
							processed++;
						}
					}
					totalProcessed += processed;
					if (logger.isDebugEnabled()) {
						int queueSize = messageFutureQueue.size();
						logger.debug("processed {} records, total so far: {}, size of queue: {}", processed, totalProcessed,
							queueSize);
						if (logger.isTraceEnabled() && queueSize > 0) {
							logger.trace("PENDING:");
							for (Future<DataMessage> futureReply : messageFutureQueue) {
								logger.trace(futureReply.toString());
							}
						}
					}
					try {
						Thread.sleep(REPLY_PROCESSOR_SLEEP_MS);
					} catch (InterruptedException e) {
						// what to do
					}
				}
			});

			// start background reply processor
			replyProcessorThread.setDaemon(true);
			replyProcessorThread.start();
		}

		// prepare arrays for message construction
		Class[] parameterTypes = new Class[]{String[].class};
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}
		Object[] parameters = new Object[]{new String[]{}};
		// TODO: generalize this to other methods (non-varargs)
		if ("main".equals(methodName) && argList != null) {
			parameters[0] = argList.toArray(new String[0]);
		}

		// send all requests
		for (; reqsSent < requests; reqsSent++) {
			DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
				parameterTypesNamesArray, this, null, parameters, new ObjectRef[parameterTypes.length]);
			if (sendAndForget) {
				// send to log and forget
				thinPeer.sendToLogAndForget(requestMsg);
			} else {
				// send async, store future reply
				messageFuture = thinPeer.sendToLogAsync(requestMsg);
				messageFutureQueue.add(messageFuture);
			}
		}

		// wait for background reply processor to be done
		if (!sendAndForget) {
			replyProcessorThread.join();
		}

		thinPeer.close();

		if (verbose) {
			System.out.println(String.format("sent and received %s requests in %s ms", reqsSent,
				(System.currentTimeMillis() - start)));
		}

		return reqsSent;
	}

	/**
	 * Use this method to send requests in parallel with separate client (ThinPeer) threads
	 * NOTE that this method calls either the runReqsWithSingleClient() or runReqsWithSingleClientAsync()
	 * methods in parallel threads
	 */
	protected void runReqsWithNClients()
		throws Exception {

		if (requests <= 1) {
			throw new IllegalArgumentException("Method must be called with requests > 1. requests = " + requests);
		}
		if (clients <= 1) {
			throw new IllegalArgumentException("Method must be called with clients > 1. clients = " + clients);
		}

		Thread[] clientList = new Thread[clients];
		final AtomicInteger finishedThreads = new AtomicInteger(0);
		final AtomicInteger reqsSent = new AtomicInteger(0);

		// start timing
		long start = System.currentTimeMillis();

		// create all threads
		for (int i = 0; i < clients; i++) {
			Thread client = new Thread(() -> {
				try {
					int sent;
					if (async || sendAndForget) {
						sent = runReqsWithSingleClientAsync();
					} else {
						sent = runReqsWithSingleClient();
					}
					finishedThreads.getAndIncrement();
					reqsSent.getAndAdd(sent);
				} catch (Exception e) {
					logger.error("Caught error running requests", e);
				}
			});
			clientList[i] = client;
		}

		// then start all clients at once
		for (int i = 0; i < clients; i++) {
			clientList[i].start();
		}

		// wait for threads to finish
		while (finishedThreads.get() < clients) {
			Thread.sleep(10);
		}

		if (verbose) {
			System.out.println(String.format("sent %s requests with %s client(s) in %s ms", reqsSent.get(), clients,
				(System.currentTimeMillis() - start)));
		}
	}

	public static void main(String[] args) throws Exception {
		int exitCode = new CommandLine(new AppRunner()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		if (requests == 1 || clients == 1) {
			if (async || sendAndForget) {
				runReqsWithSingleClientAsync();
			} else {
				runReqsWithSingleClient();
			}
		} else {
			runReqsWithNClients();
		}
		return 0;
	}
}
