package com.ittera.cometa.concentrator.exec;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PeerMessageExecutor extends ExtendedThreadPoolExecutor implements PeerExecutor {

	protected static final Logger logger = LoggerFactory.getLogger(PeerMessageExecutor.class);

	@Inject
	public PeerMessageExecutor(@Named("peer.corePoolSize") String corePoolSize,
														 @Named("peer.maximumPoolSize") String maximumPoolSize,
														 @Named("peer.keepAliveSeconds") String keepAliveSeconds,
														 PeerThreadFactory threadFactory) {

		super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
			TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), threadFactory);
	}
}
