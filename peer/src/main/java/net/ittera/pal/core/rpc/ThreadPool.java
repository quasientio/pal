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

public class ThreadPool {

  private static final Logger logger = LoggerFactory.getLogger(ThreadPool.class);
  private final RpcThreadFactory threadFactory;
  private final int poolSize;

  ThreadPool(int poolSize, RpcThreadFactory threadFactory) {
    this.poolSize = poolSize;
    this.threadFactory = threadFactory;
    logger.info("Initialized thread pool, with poolSize={}", poolSize);
  }

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

  public void shutdown() {
    if (!threadFactory.getCreatedThreads().isEmpty()) {
      logger.info("Sending interrupt to {} threads", threadFactory.getCreatedThreads().size());
    }
    for (Thread thread : threadFactory.getCreatedThreads()) {
      thread.interrupt();
    }
  }
}
