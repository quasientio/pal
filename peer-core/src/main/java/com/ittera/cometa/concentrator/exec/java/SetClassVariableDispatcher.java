package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.UUID;

public class SetClassVariableDispatcher extends SetFieldDispatcher {

	@Singleton
	@Inject
	public SetClassVariableDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
																		ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}


	@Singleton
	public SetClassVariableDispatcher() {
	}

	@Override
	protected final boolean returnsVoid() {
		return true;
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.PUT_STATIC;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.PUT_STATIC_DONE;
	}
}
