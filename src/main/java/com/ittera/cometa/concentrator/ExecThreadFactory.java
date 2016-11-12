package com.ittera.cometa.concentrator;

import java.util.concurrent.ThreadFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Created by libre on 11/12/16.
 */
public class ExecThreadFactory implements ThreadFactory {

  protected static final Logger logger = LogManager.getLogger(Executor.class);

  private int threadCounter = 0;
  private static final String threadGroupName = "Executor Thread Group";
  private static final String threadBaseName = "Executor Thread";
  private ThreadGroup threadGroup;
  private static final int threadGroupMaxPriority = Thread.NORM_PRIORITY;
  private static final int threadPriority = Thread.NORM_PRIORITY;
  private static final boolean threadGroupIsDaemon = false;
  private static final boolean threadIsDaemon = false;

  public ExecThreadFactory() {
    threadGroup = new ThreadGroup("Executor Thread Group");
    threadGroup.setDaemon(threadGroupIsDaemon);
    threadGroup.setMaxPriority(threadGroupMaxPriority);
    logger.info("Created new thread group with name: {}, daemon: {}, maxPriority: {}", threadGroupName, threadGroupIsDaemon, threadGroupMaxPriority);
  }

  @Override
  public Thread newThread(Runnable runnable) {
    logger.traceEntry();
    String newThreadName = threadBaseName + " " + threadCounter;
    Thread thread = new Thread(threadGroup, runnable, newThreadName);
    thread.setPriority(threadPriority);
    thread.setDaemon(threadIsDaemon);
    logger.info("Created new executor thread with name: {}", newThreadName);
    logger.traceExit();
    return thread;
  }


  public ThreadGroup getThreadGroup() {
    return threadGroup;
  }

}
