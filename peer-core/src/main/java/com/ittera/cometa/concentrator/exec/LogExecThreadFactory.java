package com.ittera.cometa.concentrator.exec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

@Singleton
public class LogExecThreadFactory extends ExecThreadFactory implements LogThreadFactory {

    protected static final Logger logger = LoggerFactory.getLogger(LogExecThreadFactory.class);

    private static final String THREAD_GROUP_NAME = "Log Executor Group";
    private static final String THREAD_BASE_NAME = "Log Executor";

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
        final String newThreadName = THREAD_BASE_NAME + ' ' + threadCounter.getAndIncrement();
        final Thread thread = new Thread(threadGroup, r, newThreadName);
        thread.setPriority(THREAD_PRIORITY);
        thread.setDaemon(THREAD_IS_DAEMON);
        addCreatedThread(thread);
        logger.debug("Created new log executor thread with name: '{}' and id: {}", newThreadName, thread.getId());
        return thread;
    }
}
