package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;
import javax.inject.Inject;

import java.util.UUID;

public class NonVoidClassMethodDispatcher extends ClassMethodDispatcher {

	@Singleton
	@Inject
	public NonVoidClassMethodDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
																	 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final boolean returnsVoid() {
		return false;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.RETURN_VALUE;
	}
}
