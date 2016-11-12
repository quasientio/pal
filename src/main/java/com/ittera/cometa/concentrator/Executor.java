package com.ittera.cometa.concentrator;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;

import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * TODO
 * 1. Parameters must come from properties (# of threads, timeouts)
 * 2. We should also create our own ThreadFactory
 * <p>
 * Created by libre on 11/10/16.
 */

public class Executor extends ThreadPoolExecutor {

  protected static final Logger logger = LogManager.getLogger(Executor.class);

  private static Executor instance;

  //default config values as properties (string)
  private static final String defaultCorePoolSize = "0";
  private static final String defaultMaximumPoolSize = "1000";
  private static final String defaultKeepAliveSeconds = "60";

  private Executor(int corePoolSize, int maximumPoolSize, long keepAliveSeconds) {
    super(corePoolSize, maximumPoolSize, keepAliveSeconds, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ExecThreadFactory());
  }

  //singleton accessor to be called once initialized
  public static Executor getInstance() {
    if (instance == null) {
      throw new IllegalStateException("Executor has not been initialized from properties");
    }
    return instance;
  }

  //singleton accessor for initial construction
  public static Executor getInstance(Properties properties) {

    if (instance == null) {
      int corePoolSize = Integer.parseInt((String) properties.getProperty("corePoolSize", defaultCorePoolSize));
      int maximumPoolSize = Integer.parseInt((String) properties.getProperty("maximumPoolSize", defaultMaximumPoolSize));
      long keepAliveSeconds = Long.parseLong((String) properties.getProperty("keepAliveSeconds", defaultKeepAliveSeconds));
      instance = new Executor(corePoolSize, maximumPoolSize, keepAliveSeconds);
    }

    return instance;
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    if (t == null && r instanceof Future<?>) {
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
      logger.error("Error executing runnable", t);
  }
}
