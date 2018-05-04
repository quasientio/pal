package com.ittera.cometa.concentrator.exec;

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
public class LogMessageExecutor extends ExtendedThreadPoolExecutor {

	protected static final Logger logger = LoggerFactory.getLogger(LogMessageExecutor.class);

	@Inject
	public LogMessageExecutor(@Named("log.corePoolSize") String corePoolSize,
														@Named("log.maximumPoolSize") String maximumPoolSize,
														@Named("log.keepAliveSeconds") String keepAliveSeconds,
														ZContext zmqContext, @Named("in.log") String zmqSocketAddress,
														DataMessageBuilder dataMessageBuilder, IncomingMessageDispatcher
															incomingMessageDispatcher, DispatcherConnector dispatcherConnector) {

		super(Integer.valueOf(corePoolSize), Integer.valueOf(maximumPoolSize), Integer.valueOf(keepAliveSeconds),
			TimeUnit.SECONDS, new SynchronousQueue<>(), new ExecThreadFactory(zmqContext, zmqSocketAddress, dataMessageBuilder,
				incomingMessageDispatcher, dispatcherConnector, ExecThreadFactory.ExecChannelType.LOG));
	}
}
