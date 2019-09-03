package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.CustomClassloader;
import com.ittera.cometa.messages.ExecMessageBuilder;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.zeromq.ZContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LogMessageExecutor extends ExtendedThreadPoolExecutor {

	protected static final Logger logger = LoggerFactory.getLogger(LogMessageExecutor.class);

	@Inject
	public LogMessageExecutor(@Named("log.corePoolSize") String corePoolSize,
														@Named("log.maximumPoolSize") String maximumPoolSize,
														@Named("log.keepAliveSeconds") String keepAliveSeconds,
														ZContext zmqContext, @Named("in.log") String zmqSocketAddress,
														ExecMessageBuilder execMessageBuilder, IncomingMessageDispatcher
															incomingMessageDispatcher, DispatcherConnector dispatcherConnector,
														CustomClassloader customClassloader, UUID peerUuid) {

		super(Integer.parseInt(corePoolSize), Integer.parseInt(maximumPoolSize), Integer.parseInt(keepAliveSeconds),
			TimeUnit.SECONDS, new SynchronousQueue<>(), new ExecThreadFactory(zmqContext, zmqSocketAddress,
				execMessageBuilder, incomingMessageDispatcher, dispatcherConnector, ExecThreadFactory.ExecChannelType.LOG,
				customClassloader, peerUuid));
	}
}
