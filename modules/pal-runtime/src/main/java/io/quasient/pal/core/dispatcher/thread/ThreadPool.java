/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.dispatcher.thread;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a pool of threads used for message dispatching.
 *
 * <p>This class initializes a fixed-size collection of threads using a provided thread factory,
 * starts them with a no-operation task, and provides a mechanism to gracefully shutdown by
 * interrupting all running threads.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Pool pattern - thread factory reference shared for thread creation")
public class ThreadPool {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ThreadPool.class);

  /**
   * Factory used to create and manage threads.
   *
   * <p>This factory is responsible for tracking the threads created by the thread pool.
   */
  private final InvokerThreadFactory threadFactory;

  /**
   * The fixed number of threads that this pool will manage.
   *
   * <p>This value determines how many threads will be created and started when initiating the pool.
   */
  private final int poolSize;

  /**
   * Constructs a new thread pool with a specified size and thread creation strategy.
   *
   * @param poolSize the number of threads to be managed by this pool; must be a positive integer.
   * @param threadFactory the factory instance used to create new threads; must not be {@code null}.
   */
  public ThreadPool(int poolSize, InvokerThreadFactory threadFactory) {
    this.poolSize = poolSize;
    this.threadFactory = threadFactory;
    logger.info("Initialized thread pool, with poolSize={}", poolSize);
  }

  /**
   * Starts all threads within the pool.
   *
   * <p>This method creates and starts a new thread for each slot in the pool. Each thread is
   * assigned a no-operation runnable task. If threads have already been started (as indicated by
   * the thread factory having already created threads), an {@link IllegalStateException} is thrown.
   *
   * @throws IllegalStateException if threads have already been started.
   */
  public void startAllThreads() {
    if (!threadFactory.getCreatedThreads().isEmpty()) {
      throw new IllegalStateException("Some threads have already been started.");
    }
    Runnable noOpRunnable = new NoOpRunnable();
    for (int i = 0; i < poolSize; i++) {
      Thread t = threadFactory.newThread(noOpRunnable);
      t.start();
      logger.info("Started thread {}", t.getName());
    }
  }

  /**
   * Shuts down all threads managed by the pool.
   *
   * <p>This method iterates over all threads created by the thread factory and sends an interrupt
   * signal to each, requesting that they terminate.
   */
  public void shutdown() {
    if (!threadFactory.getCreatedThreads().isEmpty()) {
      logger.info("Sending interrupt to {} threads", threadFactory.getCreatedThreads().size());
    }
    for (Thread thread : threadFactory.getCreatedThreads()) {
      thread.interrupt();
    }
  }
}
