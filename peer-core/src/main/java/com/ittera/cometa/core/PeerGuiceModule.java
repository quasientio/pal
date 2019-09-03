package com.ittera.cometa.core;

import com.ittera.cometa.common.ObjectService;

import com.ittera.cometa.common.lang.DispatchForwarder;
import com.ittera.cometa.common.lang.ProxyDispatcher;
import com.ittera.cometa.core.exec.java.CustomClassloader;
import com.ittera.cometa.cxn.PeerLogDirectory;

import com.ittera.cometa.core.exec.java.AspectProxyDispatcher;

import com.ittera.cometa.messages.ExecMessageBuilder;

import com.google.inject.name.Names;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Properties;
import java.util.UUID;

import com.ittera.cometa.messages.protobuf.ProtobufExecMessageBuilder;
import org.zeromq.ZContext;

class PeerGuiceModule extends AbstractModule {

	private final Properties properties;
	private final ZContext zContext;
	private final UUID peerUuid;
	private final CustomClassloader customClassloader;

	PeerGuiceModule(Properties properties, ZContext zContext, CustomClassloader customClassloader) {
		this.properties = properties;
		this.zContext = zContext;
		this.peerUuid = UUID.fromString(properties.getProperty("id"));
		this.customClassloader = customClassloader;
	}

	@Override
	protected void configure() {

		Names.bindProperties(binder(), properties);

		// bind implementations
		bind(ProxyDispatcher.class).to(com.ittera.cometa.core.exec.java.AspectProxyDispatcher.class);

		// common and cxn library classes are not annotated with @Singleton
		bind(ObjectService.class).to(com.ittera.cometa.common.BiMapObjectService.class).asEagerSingleton();
		bind(ExecMessageBuilder.class).
			to(ProtobufExecMessageBuilder.class).asEagerSingleton();
		bind(PeerLogDirectory.class).to(com.ittera.cometa.cxn.ZkClient.class).asEagerSingleton();

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
	CustomClassloader getCustomClassloader() {
		return customClassloader;
	}
}
