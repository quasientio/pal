package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;

import org.apache.commons.lang3.StringUtils;

public class ExecThreadFactory implements ThreadFactory {

	private final List<Thread> createdThreads = new ArrayList<>();

	protected ThreadGroup threadGroup;
	protected final AtomicInteger threadCounter = new AtomicInteger(0);

	protected static final int THREAD_GROUP_MAX_PRIORITY = Thread.NORM_PRIORITY;
	protected static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;
	protected static final boolean THREAD_GROUP_IS_DAEMON = false;
	protected static final boolean THREAD_IS_DAEMON = false;

	protected static final Logger logger = LoggerFactory.getLogger(ExecThreadFactory.class);

	protected final ExecChannelType execChannelType;
	protected final DataMessageBuilder dataMessageBuilder;
	protected final DispatcherConnector dispatcherConnector;
	protected final IncomingMessageDispatcher incomingMessageDispatcher;

	// zmq stuff
	protected final ZContext zmqContext;
	protected final String zmqSocketAddress;

	protected final ClassLoader classLoader;

	enum ExecChannelType {
		PEER("peer"), LOG("log");

		final String name;

		ExecChannelType(String name) {
			this.name = name;
		}
	}

	public ExecThreadFactory(ZContext zmqContext, String zmqSocketAddress, DataMessageBuilder dataMessageBuilder,
													 IncomingMessageDispatcher incomingMessageDispatcher, DispatcherConnector dispatcherConnector,
													 ExecChannelType execChannelType, ClassLoader classLoader) {

		this.execChannelType = execChannelType;
		threadGroup = new ThreadGroup(getThreadGroupName());
		threadGroup.setDaemon(THREAD_GROUP_IS_DAEMON);
		threadGroup.setMaxPriority(THREAD_GROUP_MAX_PRIORITY);
		this.zmqContext = zmqContext;
		this.zmqSocketAddress = zmqSocketAddress;
		this.dataMessageBuilder = dataMessageBuilder;
		this.dispatcherConnector = dispatcherConnector;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.classLoader = classLoader;
		logger.info("Initialized exec thread factory with group name: {}, daemon: {}, maxPriority: {}", getThreadGroupName(),
			THREAD_GROUP_IS_DAEMON, THREAD_GROUP_MAX_PRIORITY);
	}

	@Override
	public Thread newThread(Runnable r) {
		final String newThreadName = getThreadBaseName() + ' ' + threadCounter.getAndIncrement();
		final Thread thread;
		switch (execChannelType) {
			case LOG:
				thread = new LogMessageInvoker(threadGroup, r, newThreadName, zmqContext, dataMessageBuilder,
					zmqSocketAddress, incomingMessageDispatcher, dispatcherConnector);
				break;
			case PEER:
				thread = new PeerMessageInvoker(threadGroup, r, newThreadName, zmqContext, dataMessageBuilder,
					zmqSocketAddress, incomingMessageDispatcher, dispatcherConnector);
				break;
			default:
				throw new IllegalArgumentException("Unknown ExecChannelType: " + execChannelType);
		}
		thread.setContextClassLoader(classLoader);
		thread.setPriority(THREAD_PRIORITY);
		thread.setDaemon(THREAD_IS_DAEMON);
		thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in {} exec thread: {}",
			execChannelType.name, newThreadName, e));
		addCreatedThread(thread);
		logger.debug("Created new {} executor thread with name: '{}' and id: {}", execChannelType.name, newThreadName,
			thread.getId());
		return thread;
	}

	public List<Thread> getCreatedThreads() {
		return createdThreads;
	}

	protected void addCreatedThread(Thread t) {
		createdThreads.add(t);
	}

	public ThreadGroup getThreadGroup() {
		return threadGroup;
	}

	private String getThreadBaseName() {
		return String.format("%s Executor", StringUtils.capitalize(execChannelType.name));

	}

	private String getThreadGroupName() {
		return String.format("%s Executor Group", StringUtils.capitalize(execChannelType.name));
	}
}
