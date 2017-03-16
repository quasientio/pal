package com.ittera.cometa.concentrator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecThreadFactory implements ThreadFactory {

  protected static final Logger logger = LogManager.getLogger(Executor.class);

  private final ThreadGroup threadGroup;
  private final AtomicInteger threadCounter = new AtomicInteger(0);
  private static final String THREAD_GROUP_NAME = "Executor Thread Group";
  private static final String THREAD_BASE_NAME = "Executor Thread";
  private static final int THREAD_GROUP_MAX_PRIORITY = Thread.NORM_PRIORITY;
  private static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;
  private static final boolean THREAD_GROUP_IS_DAEMON = false;
  private static final boolean THREAD_IS_DAEMON = false;

  public ExecThreadFactory() {
    threadGroup = new ThreadGroup("Executor Thread Group");
    threadGroup.setDaemon(THREAD_GROUP_IS_DAEMON);
    threadGroup.setMaxPriority(THREAD_GROUP_MAX_PRIORITY);
    logger.info("Created new thread group with name: {}, daemon: {}, maxPriority: {}", THREAD_GROUP_NAME, THREAD_GROUP_IS_DAEMON, THREAD_GROUP_MAX_PRIORITY);
  }

  @Override
  public Thread newThread(Runnable r) {
    logger.traceEntry();
    final String newThreadName = THREAD_BASE_NAME + ' ' + threadCounter.getAndIncrement();
    final Thread thread = new Thread(threadGroup, r, newThreadName);
    thread.setPriority(THREAD_PRIORITY);
    thread.setDaemon(THREAD_IS_DAEMON);
    logger.info("Created new executor thread with name: {}", newThreadName);
    logger.traceExit();
    return thread;
  }

  public ThreadGroup getThreadGroup() {
    return threadGroup;
  }

}
