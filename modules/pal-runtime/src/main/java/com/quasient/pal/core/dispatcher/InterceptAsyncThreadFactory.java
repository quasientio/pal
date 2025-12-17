/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread factory for creating async intercept callback executor threads.
 *
 * <p>This factory creates daemon threads for running BEFORE_ASYNC and AFTER_ASYNC intercept
 * callbacks in the background. Threads are configured with:
 *
 * <ul>
 *   <li>Daemon status to allow JVM shutdown without waiting
 *   <li>Custom classloader for proper class resolution
 *   <li>Proper thread group for management
 *   <li>Uncaught exception handler for logging
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. The thread counter uses atomic operations.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "ThreadGroup exposure is intentional for management; ClassLoader is immutable")
public class InterceptAsyncThreadFactory implements ThreadFactory {

  /** Logger instance for debugging thread creation. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptAsyncThreadFactory.class);

  /** Thread group for organizing intercept async callback threads. */
  private final ThreadGroup threadGroup;

  /** Counter for generating unique thread names. */
  private final AtomicInteger threadCounter = new AtomicInteger(0);

  /** Class loader to set as the context classloader for created threads. */
  private final ClassLoader classLoader;

  /** Base name prefix for created threads. */
  private static final String THREAD_NAME_PREFIX = "intercept-async-callback-";

  /**
   * Constructs a new InterceptAsyncThreadFactory.
   *
   * @param serviceThreadGroup the parent thread group for service threads
   * @param classLoader the class loader to set on created threads (typically CustomClassloader)
   */
  public InterceptAsyncThreadFactory(ThreadGroup serviceThreadGroup, ClassLoader classLoader) {
    this.threadGroup = new ThreadGroup(serviceThreadGroup, "intercept-async-callbacks");
    this.threadGroup.setMaxPriority(Thread.NORM_PRIORITY);
    this.classLoader = classLoader;
    logger.debug(
        "Initialized InterceptAsyncThreadFactory with thread group: {}", threadGroup.getName());
  }

  /**
   * Creates a new thread for executing async intercept callbacks.
   *
   * <p>The created thread is configured as a daemon thread with the custom classloader set as its
   * context classloader. This ensures proper class resolution when callback handlers are invoked.
   *
   * @param runnable the runnable task to execute
   * @return a new configured thread ready to be started
   */
  @Override
  public Thread newThread(Runnable runnable) {
    String threadName = THREAD_NAME_PREFIX + threadCounter.getAndIncrement();
    Thread thread = new Thread(threadGroup, runnable, threadName);
    thread.setDaemon(true);
    thread.setContextClassLoader(classLoader);
    thread.setUncaughtExceptionHandler(
        (t, e) ->
            logger.error(
                "Uncaught exception in intercept async callback thread: {}", t.getName(), e));

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Created intercept async callback thread: {} (id={})", threadName, thread.getId());
    }
    return thread;
  }

  /**
   * Returns the thread group used by this factory.
   *
   * @return the thread group for intercept async callback threads
   */
  public ThreadGroup getThreadGroup() {
    return threadGroup;
  }
}
