package com.ittera.cometa.concentrator.exec;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO set context classloader to created threads
 */
public abstract class ExecThreadFactory  {

  private List<Thread> createdThreads = new ArrayList<>();

  protected ThreadGroup threadGroup;
  protected final AtomicInteger threadCounter = new AtomicInteger(0);

  protected static final int THREAD_GROUP_MAX_PRIORITY = Thread.NORM_PRIORITY;
  protected static final int THREAD_PRIORITY = Thread.NORM_PRIORITY;
  protected static final boolean THREAD_GROUP_IS_DAEMON = false;
  protected static final boolean THREAD_IS_DAEMON = false;

  public List<Thread> getCreatedThreads() {
    return createdThreads;
  }

  protected void addCreatedThread(Thread t) {
    createdThreads.add(t);
  }

  public ThreadGroup getThreadGroup() {
    return threadGroup;
  }
}
