package com.ittera.cometa.concentrator;

import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * TODO Parameters must come from properties (# of threads, timeouts)
 */

public class Executor extends ThreadPoolExecutor {

  protected static final Logger logger = LogManager.getLogger(Executor.class);

  private static ThreadPoolExecutor instance;

  //default config values as properties (string)
  private static final String DEFAULT_CORE_POOL_SIZE = "0";
  private static final String DEFAULT_MAXIMUM_POOL_SIZE = "1000";
  private static final String DEFAULT_KEEP_ALIVE_SECONDS = "60";

  private static final Object initLock = new Object();

  private Executor(int corePoolSize, int maximumPoolSize, long keepAliveSeconds) {
    super(corePoolSize, maximumPoolSize, keepAliveSeconds, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ExecThreadFactory());
  }

  //singleton accessor to be called once initialized
  public static ThreadPoolExecutor getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Executor has not been initialized from properties");
    }
    return instance;
  }

  //singleton accessor for initial construction
  public static ThreadPoolExecutor getInstance(Properties properties) {
    synchronized (initLock) {
      if (instance == null) {
        final int corePoolSize = Integer.parseInt(properties.getProperty("corePoolSize", DEFAULT_CORE_POOL_SIZE));
        final int maximumPoolSize = Integer.parseInt(properties.getProperty("maximumPoolSize", DEFAULT_MAXIMUM_POOL_SIZE));
        final long keepAliveSeconds = Long.parseLong(properties.getProperty("keepAliveSeconds", DEFAULT_KEEP_ALIVE_SECONDS));
        instance = new Executor(corePoolSize, maximumPoolSize, keepAliveSeconds);
        logger.info("Initialized Executor with corePoolSize={}, maximumPoolSize={}, keepAliveSeconds={}", corePoolSize, maximumPoolSize, keepAliveSeconds);
      }
    }
    return instance;
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
