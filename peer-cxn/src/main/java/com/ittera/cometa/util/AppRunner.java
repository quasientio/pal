package com.ittera.cometa.util;

import com.ittera.cometa.PeerInfo;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.BiMapObjectService;

import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppRunner {

	protected final static Logger logger = LoggerFactory.getLogger(AppRunner.class);
	protected final DataMessageBuilder dataMessageBuilder;
	final ServiceManager manager;
	protected final boolean verbose;
	protected static final long REPLY_PROCESSOR_SLEEP_MS = 100;

	protected static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

	AppRunner(boolean verbose) {
		this.verbose = verbose;

		// configure wiring
		AbstractModule module = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ObjectService.class).to(BiMapObjectService.class).asEagerSingleton();
				bind(DataMessageBuilder.class).to(ProtobufDataMessageBuilder.class).asEagerSingleton();
			}
		};

		final Injector injector = Guice.createInjector(module);
		dataMessageBuilder = injector.getInstance(DataMessageBuilder.class);
		dataMessageBuilder.dontStoreObjects();

		// configure services
		final Set<Service> services = new HashSet<>();
		services.add((Service) injector.getInstance(ObjectService.class));
		manager = new ServiceManager(services);
		manager.startAsync();
	}

	/**
	 * Serially sends all requests in a single (ThinPeer) thread.
	 * Sends 1st req to log and waits for Future reply, then sends all other directly to peer
	 */
	protected int runReqsWithSingleClient(String className, String methodName, AppRunnerOptions opts) throws Exception {

		// init ThinPeer
		ThinPeer thinPeer;
		LogInfo inLog = opts.inLog == null ? null : new LogInfo(opts.inLog, DEFAULT_BOOTSTRAP_SERVERS);
		LogInfo outLog = opts.outLog == null ? null : new LogInfo(opts.outLog, DEFAULT_BOOTSTRAP_SERVERS);
		thinPeer = new ThinPeer("/runner.properties", inLog, outLog);

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
		if (methodName.equals("main") && !opts.argList.isEmpty()) {
			parameters[0] = opts.argList.toArray(new String[0]);
		}

		// send 1st request
		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
			parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
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
		for (; reqsSent < opts.requests; reqsSent++) {
			requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
				parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
			thinPeer.sendAndReceive(requestMsg);
		}

		thinPeer.close();
		manager.stopAsync();

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
	protected int runReqsWithSingleClientAsync(String className, String methodName, AppRunnerOptions opts)
		throws Exception {

		// init ThinPeer
		ThinPeer thinPeer;
		LogInfo inLog = opts.inLog == null ? null : new LogInfo(opts.inLog, DEFAULT_BOOTSTRAP_SERVERS);
		LogInfo outLog = opts.outLog == null ? null : new LogInfo(opts.outLog, DEFAULT_BOOTSTRAP_SERVERS);
		thinPeer = new ThinPeer("/runner.properties", inLog, outLog);

		long start = System.currentTimeMillis();
		int reqsSent = 0;
		Future<DataMessage> messageFuture;

		// a queue to store futures (async mode)
		final Queue<Future<DataMessage>> messageFutureQueue = new ConcurrentLinkedQueue<>();
		Thread replyProcessorThread = null;
		if (!opts.sendAndForget) {
			replyProcessorThread = new Thread(() -> {
				int totalProcessed = 0;
				int processed;
				while (totalProcessed < opts.requests) {
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
		if (methodName.equals("main") && !opts.argList.isEmpty()) {
			parameters[0] = opts.argList.toArray(new String[0]);
		}

		// send all requests
		for (; reqsSent < opts.requests; reqsSent++) {
			DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
				parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
			if (opts.sendAndForget) {
				// send to log and forget
				thinPeer.sendToLogAndForget(requestMsg);
			} else {
				// send async, store future reply
				messageFuture = thinPeer.sendToLogAsync(requestMsg);
				messageFutureQueue.add(messageFuture);
			}
		}

		// wait for background reply processor to be done
		if (!opts.sendAndForget) {
			replyProcessorThread.join();
		}

		thinPeer.close();
		manager.stopAsync();

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
	protected void runReqsWithNClients(final String className, final String methodName, AppRunnerOptions opts)
		throws Exception {

		if (opts.requests <= 1) {
			throw new IllegalArgumentException("Method must be called with requests > 1. requests = " + opts.requests);
		}
		if (opts.clients <= 1) {
			throw new IllegalArgumentException("Method must be called with clients > 1. clients = " + opts.clients);
		}

		Thread[] clientList = new Thread[opts.clients];
		final AtomicInteger finishedThreads = new AtomicInteger(0);
		final AtomicInteger reqsSent = new AtomicInteger(0);

		// start timing
		long start = System.currentTimeMillis();

		// create all threads
		for (int i = 0; i < opts.clients; i++) {
			Thread client = new Thread(() -> {
				try {
					int sent;
					if (opts.async || opts.sendAndForget) {
						sent = runReqsWithSingleClientAsync(className, methodName, opts);
					} else {
						sent = runReqsWithSingleClient(className, methodName, opts);
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
		for (int i = 0; i < opts.clients; i++) {
			clientList[i].start();
		}

		// wait for threads to finish
		while (finishedThreads.get() < opts.clients) {
			Thread.sleep(10);
		}

		if (verbose) {
			System.out.println(String.format("sent %s requests with %s client(s) in %s ms", reqsSent.get(), opts.clients,
				(System.currentTimeMillis() - start)));
		}

	}

	public static void main(String[] args) throws Exception {

		AppRunnerOptions opts = AppRunnerOptions.parseFrom(args);

		if (opts.async && opts.sendAndForget) {
			System.err.println("async (-a) and forget-reply (-f) options are mutually-exclusive");
			System.exit(1);
		}

		String className = opts.argList.remove(0);

		AppRunner appRunner = new AppRunner(opts.verbose);
		if (opts.requests == 1 || opts.clients == 1) {
			if (opts.async || opts.sendAndForget) {
				appRunner.runReqsWithSingleClientAsync(className, "main", opts);
			} else {
				appRunner.runReqsWithSingleClient(className, "main", opts);
			}
		} else {
			appRunner.runReqsWithNClients(className, "main", opts);
		}
	}
}
