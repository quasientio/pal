package com.ittera.cometa.concentrator.exec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZContext;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class LogExecThreadFactory implements LogThreadFactory {

    protected static final Logger logger = LogManager.getLogger(LogExecThreadFactory.class);

    private final ThreadGroup threadGroup;
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final String THREAD_GROUP_NAME = "Log Executor Group";
    private static final String THREAD_BASE_NAME = "Log Executor";
    private static final int THREAD_GROUP_MAX_PRIORITY = Thread.NORM_PRIORITY;
    private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;
    private static final boolean THREAD_GROUP_IS_DAEMON = false;
    private static final boolean THREAD_IS_DAEMON = false;

    // zmq stuff
    private ZContext zmqContext;

    @Inject
    public LogExecThreadFactory(ZContext zmqContext) {
        threadGroup = new ThreadGroup(THREAD_GROUP_NAME);
        threadGroup.setDaemon(THREAD_GROUP_IS_DAEMON);
        threadGroup.setMaxPriority(THREAD_GROUP_MAX_PRIORITY);
        this.zmqContext = zmqContext;
        logger.info("Initialized exec thread factory with group name: {}, daemon: {}, maxPriority: {}", THREAD_GROUP_NAME, THREAD_GROUP_IS_DAEMON, THREAD_GROUP_MAX_PRIORITY);
    }

    @Override
    public Thread newThread(Runnable r) {
        logger.traceEntry();
        final String newThreadName = THREAD_BASE_NAME + ' ' + threadCounter.getAndIncrement();
        final Thread thread = new Thread(threadGroup, r, newThreadName);
        thread.setPriority(THREAD_PRIORITY);
        thread.setDaemon(THREAD_IS_DAEMON);
        logger.debug("Created new log executor thread with name: '{}' and id: {}", newThreadName, thread.getId());
        logger.traceExit();
        return thread;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

}
