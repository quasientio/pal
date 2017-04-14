package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.util.concurrent.AbstractIdleService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ExecutionThreadService extends AbstractIdleService implements ExecutionService {

    protected static final Logger logger = LogManager.getLogger(ExecutionThreadService.class);

    private final ExecutorService executor;

    @Inject
    public ExecutionThreadService(ExecutorService executorService) {
        this.executor = executorService;
        logger.info("Initialized thread service with executorService: {}", executorService);
    }


    @Override
    protected void startUp() throws Exception {
        //TODO initialize internal queues, etc.
    }

    @Override
    protected void shutDown() throws Exception {
        //TODO call the executor's shutdown() or shutdownNow()
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (isRunning()) {
            return executor.submit(task);
        } else {
            throw new IllegalStateException("Service is not running");
        }
    }

    @Override
    public void startCoreThreads() {
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).prestartAllCoreThreads();
            logger.debug("All core threads started.");
        }
    }
}
