package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.exec.java.CustomClassloader;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.zeromq.ZContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PeerMessageExecutor extends ExtendedThreadPoolExecutor {

	protected static final Logger logger = LoggerFactory.getLogger(PeerMessageExecutor.class);

	@Inject
	public PeerMessageExecutor(@Named("peer.corePoolSize") String corePoolSize,
														 @Named("peer.maximumPoolSize") String maximumPoolSize,
														 @Named("peer.keepAliveSeconds") String keepAliveSeconds,
														 ZContext zmqContext, @Named("in.dealer") String zmqSocketAddress,
														 DataMessageBuilder dataMessageBuilder, IncomingMessageDispatcher
															 incomingMessageDispatcher, DispatcherConnector dispatcherConnector,
														 CustomClassloader customClassloader) {

		super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
			TimeUnit.SECONDS, new SynchronousQueue<>(), new ExecThreadFactory(zmqContext, zmqSocketAddress, dataMessageBuilder,
				incomingMessageDispatcher, dispatcherConnector, ExecThreadFactory.ExecChannelType.PEER, customClassloader));
	}
}
