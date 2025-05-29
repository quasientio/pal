/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a pool of threads used for message dispatching.
 *
 * <p>This class initializes a fixed-size collection of threads using a provided thread factory,
 * starts them with a no-operation task, and provides a mechanism to gracefully shutdown by
 * interrupting all running threads.
 */
public class ThreadPool {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ThreadPool.class);

  /**
   * Factory used to create and manage threads.
   *
   * <p>This factory is responsible for tracking the threads created by the thread pool.
   */
  private final RpcThreadFactory threadFactory;

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
  ThreadPool(int poolSize, RpcThreadFactory threadFactory) {
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
