package com.ittera.cometa.concentrator.exec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@Singleton
public class LogMessageExecutor extends ExtendedThreadPoolExecutor implements LogExecutor {

    protected static final Logger logger = LogManager.getLogger(LogMessageExecutor.class);

    @Inject
    public LogMessageExecutor(@Named("log.corePoolSize") String corePoolSize,
                              @Named("log.maximumPoolSize") String maximumPoolSize,
                              @Named("log.keepAliveSeconds") String keepAliveSeconds,
                              LogThreadFactory threadFactory) {

        super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
                TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), threadFactory);
    }
}
