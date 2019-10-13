package com.ittera.cometa.core;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.DispatchForwarder;
import com.ittera.cometa.common.lang.ProxyDispatcher;
import com.ittera.cometa.core.exec.java.CustomClassloader;
import com.ittera.cometa.core.exec.java.AspectProxyDispatcher;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;

import com.google.inject.name.Names;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.*;

import org.zeromq.ZContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PeerWiring extends AbstractModule {

	private static final Logger logger = LoggerFactory.getLogger(PeerWiring.class);
	private final Properties properties;
	private final ZContext zContext;
	private final UUID peerUuid;
	private final CustomClassloader customClassloader;
	private final EnumSet<RunOptions> runOptions;
	private final ThreadGroup serviceThreadGroup = new ThreadGroup("service-threads");

	PeerWiring(Properties properties, EnumSet<RunOptions> runOptions, ZContext zContext, CustomClassloader customClassloader) {
		if (logger.isDebugEnabled()) {
			printProperties(properties);
		}
		this.properties = properties;
		addServiceNamesToProps();
		this.runOptions = runOptions;
		this.zContext = zContext;
		this.peerUuid = UUID.fromString(properties.getProperty("id"));
		this.customClassloader = customClassloader;
	}

	private static void printProperties(Properties props) {
		final StringBuilder sb = new StringBuilder();
		final List<String> keys = new ArrayList<>(props.stringPropertyNames());
		Collections.sort(keys);
		for (String key : keys) {
			sb.append("\n").append(key).append(":").append(props.getProperty(key));
		}
		logger.debug("Created guice module with properties:{}", sb.toString());
	}

	private void addServiceNamesToProps() {
		// use underscore in names to better filter service-related traces
		properties.setProperty("LogReader.service", "Log_Reader");
		properties.setProperty("LogWriter.service", "Log_Writer");
		properties.setProperty("DirectRequestDispatcher.service", "Direct_Request_Dispatcher");
		properties.setProperty("OutgoingMessageDispatcher.service", "Outgoing_Message_Dispatcher");
	}

	@Override
	protected void configure() {

		Names.bindProperties(binder(), properties);

		// bind implementations
		bind(ProxyDispatcher.class).to(com.ittera.cometa.core.exec.java.AspectProxyDispatcher.class);

		// common and cxn library classes are not annotated with @Singleton
		bind(ObjectService.class).to(com.ittera.cometa.common.BiMapObjectService.class).asEagerSingleton();
		bind(MessageBuilder.class).
			to(ProtobufMessageBuilder.class).asEagerSingleton();
		bind(PALDirectory.class).asEagerSingleton();

		// AspectProxy and DispatchForwarder's fields are static
		requestStaticInjection(AspectProxyDispatcher.class);
		requestStaticInjection(DispatchForwarder.class);
	}

	@Provides
	ZContext getZmqContext() {
		return zContext;
	}

	@Provides
	UUID getPeerUuid() {
		return peerUuid;
	}

	@Provides
	ThreadGroup getServiceThreadGroup() {
		return serviceThreadGroup;
	}

	@Provides
	CustomClassloader getCustomClassloader() {
		return customClassloader;
	}

	@Provides
	EnumSet<RunOptions> getRunOptions() {
		return runOptions;
	}
}
