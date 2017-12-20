package com.ittera.cometa.concentrator.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ittera.cometa.messages.DataMessageBuilder;

import com.google.inject.Singleton;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.zeromq.ZContext;

@Singleton
public class PeerExecThreadFactory extends ExecThreadFactory implements PeerThreadFactory {

	protected static final Logger logger = LoggerFactory.getLogger(PeerExecThreadFactory.class);

	private static final String THREAD_GROUP_NAME = "Peer Executor Group";
	private static final String THREAD_BASE_NAME = "Peer Executor";

	private DataMessageBuilder dataMessageBuilder;

	// zmq stuff
	private ZContext zmqContext;
	private final String dealerAddress;

	@Inject
	public PeerExecThreadFactory(ZContext zmqContext, @Named("in.dealer") String dealerAddress,
															 DataMessageBuilder dataMessageBuilder) {
		threadGroup = new ThreadGroup(THREAD_GROUP_NAME);
		threadGroup.setDaemon(THREAD_GROUP_IS_DAEMON);
		threadGroup.setMaxPriority(THREAD_GROUP_MAX_PRIORITY);
		this.zmqContext = zmqContext;
		this.dealerAddress = dealerAddress;
		this.dataMessageBuilder = dataMessageBuilder;
		logger.info("Initialized exec thread factory with group name: {}, daemon: {}, maxPriority: {}", THREAD_GROUP_NAME,
			THREAD_GROUP_IS_DAEMON, THREAD_GROUP_MAX_PRIORITY);
	}

	@Override
	public Thread newThread(Runnable r) {
		final String newThreadName = THREAD_BASE_NAME + ' ' + threadCounter.getAndIncrement();
		final Thread thread = new PeerMessageInvoker(threadGroup, r, newThreadName, zmqContext, dataMessageBuilder,
			dealerAddress);
		thread.setPriority(THREAD_PRIORITY);
		thread.setDaemon(THREAD_IS_DAEMON);
		thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				logger.error("Uncaught exception in peer exec thread: {}", newThreadName, e);
			}
		});
		addCreatedThread(thread);
		logger.debug("Created new peer executor thread with name: '{}' and id: {}", newThreadName, thread.getId());
		return thread;
	}
}
