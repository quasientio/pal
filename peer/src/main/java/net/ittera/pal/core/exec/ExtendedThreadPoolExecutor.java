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

package net.ittera.pal.core.exec;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedThreadPoolExecutor extends ThreadPoolExecutor {

  private static final Logger logger = LoggerFactory.getLogger(ExtendedThreadPoolExecutor.class);

  ExtendedThreadPoolExecutor(
      int corePoolSize,
      int maximumPoolSize,
      long keepAliveSeconds,
      TimeUnit unit,
      BlockingQueue<Runnable> workQueue,
      ThreadFactory threadFactory) {
    super(corePoolSize, maximumPoolSize, keepAliveSeconds, unit, workQueue, threadFactory);
    logger.info(
        "Initialized executor, with corePoolSize={}, maximumPoolSize={}, keepAliveSeconds={}",
        corePoolSize,
        maximumPoolSize,
        keepAliveSeconds);
  }

  @Override
  protected void beforeExecute(Thread t, Runnable r) {
    if (logger.isDebugEnabled()) {
      logger.debug("Before executing runnable: {} by thread: {}", r, t.getName());
    }
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    if (logger.isDebugEnabled()) {
      logger.debug("After executing runnable: {} with throwable: {}", r, t);
    }
    super.afterExecute(r, t);
    if ((t == null) && (r instanceof Future<?>)) {
      try {
        Future<?> future = (Future<?>) r;
        if (future.isDone()) {
          future.get();
        }
      } catch (CancellationException ce) {
        t = ce;
      } catch (ExecutionException ee) {
        t = ee.getCause();
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt(); // ignore/reset
      }
    }
    if (t != null) {
      logger.error("Error executing r", t);
    }
  }

  @Override
  public List<Runnable> shutdownNow() {

    ExecThreadFactory execThreadFactory = (ExecThreadFactory) getThreadFactory();
    if (!execThreadFactory.getCreatedThreads().isEmpty()) {
      logger.info("Sending interrupt to {} threads", execThreadFactory.getCreatedThreads().size());
    }
    for (Thread thread : execThreadFactory.getCreatedThreads()) {
      thread.interrupt();
    }
    return super.shutdownNow();
  }
}
