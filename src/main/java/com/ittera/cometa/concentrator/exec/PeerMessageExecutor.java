package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PeerMessageExecutor extends ExtendedThreadPoolExecutor implements PeerExecutor {

    protected static final Logger logger = LogManager.getLogger(PeerMessageExecutor.class);

    @Inject
    public PeerMessageExecutor(@Named("peer.corePoolSize") String corePoolSize,
                               @Named("peer.maximumPoolSize") String maximumPoolSize,
                               @Named("peer.keepAliveSeconds") String keepAliveSeconds,
                               PeerThreadFactory threadFactory) {

        super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), threadFactory);
    }
}
