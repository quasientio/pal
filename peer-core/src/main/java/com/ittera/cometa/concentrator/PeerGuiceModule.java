package com.ittera.cometa.concentrator;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.exec.PeerThreadFactory;
import com.ittera.cometa.concentrator.exec.PeerExecutor;
import com.ittera.cometa.concentrator.exec.LogThreadFactory;
import com.ittera.cometa.concentrator.exec.LogExecutor;
import com.ittera.cometa.concentrator.messages.IncomingMessageDispatcher;

import com.google.inject.name.Names;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Properties;

import org.zeromq.ZContext;

public class PeerGuiceModule extends AbstractModule {

	private Properties properties;
	private ZContext zContext;

	PeerGuiceModule(Properties properties, ZContext zContext) {
		this.properties = properties;
		this.zContext = zContext;
	}

	@Override
	protected void configure() {

		Names.bindProperties(binder(), properties);

		// bind implementations
		bind(PeerThreadFactory.class).to(com.ittera.cometa.concentrator.exec.PeerExecThreadFactory.class);
		bind(PeerExecutor.class).to(com.ittera.cometa.concentrator.exec.PeerMessageExecutor.class);
		bind(LogThreadFactory.class).to(com.ittera.cometa.concentrator.exec.LogExecThreadFactory.class);
		bind(LogExecutor.class).to(com.ittera.cometa.concentrator.exec.LogMessageExecutor.class);

		bind(KafkaMessageWriter.class).to(com.ittera.cometa.concentrator.KafkaDataMessageWriter.class);
		bind(IncomingMessageDispatcher.class).to(com.ittera.cometa.concentrator.KafkaDataMessageReader.class);
		bind(OutgoingMessageDispatcher.class).to(com.ittera.cometa.concentrator.JeromqOutMessageDispatcher.class);
		bind(InRequestMessageDispatcher.class).to(com.ittera.cometa.concentrator.JeromqInRequestDispatcher.class);

		// common and cxn library classes are not annotated with @Singleton
		bind(ObjectService.class).to(com.ittera.cometa.common.BiMapObjectService.class).asEagerSingleton();
		bind(DataMessageBuilder.class).
			to(com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder.class).asEagerSingleton();
		bind(PeerLogDirectory.class).to(com.ittera.cometa.cxn.ZkClient.class).asEagerSingleton();
	}

	@Provides
	ZContext getZmqContext() {
		return zContext;
	}
}
