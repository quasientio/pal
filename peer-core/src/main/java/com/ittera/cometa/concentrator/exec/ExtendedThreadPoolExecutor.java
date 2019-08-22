package com.ittera.cometa.concentrator.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;


public class ExtendedThreadPoolExecutor extends ThreadPoolExecutor {

	private static final Logger logger = LoggerFactory.getLogger(ExtendedThreadPoolExecutor.class);


	ExtendedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveSeconds,
														 TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		super(corePoolSize, maximumPoolSize, keepAliveSeconds, unit, workQueue, threadFactory);
		logger.info("Initialized executor, with corePoolSize={}, maximumPoolSize={}, keepAliveSeconds={}",
			corePoolSize, maximumPoolSize, keepAliveSeconds);
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
		logger.info("Sending interrupt to {} threads", execThreadFactory.getCreatedThreads().size());
		for (Thread thread : execThreadFactory.getCreatedThreads()) {
			thread.interrupt();
		}
		return super.shutdownNow();
	}
}
