package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ExtendedExecutor extends ThreadPoolExecutor {

  protected static final Logger logger = LogManager.getLogger(ExtendedExecutor.class);

  @Inject
  public ExtendedExecutor(@Named("corePoolSize") String corePoolSize,
                          @Named("maximumPoolSize") String maximumPoolSize,
                          @Named("keepAliveSeconds") String keepAliveSeconds,
                          ThreadFactory threadFactory) {

    super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
      TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), threadFactory);
    logger.info("Initialized executor, with corePoolSize={}, maximumPoolSize={}, keepAliveSeconds={}",
      corePoolSize, maximumPoolSize, keepAliveSeconds);
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
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
    if (t != null)
      logger.error("Error executing r", t);
  }
}
