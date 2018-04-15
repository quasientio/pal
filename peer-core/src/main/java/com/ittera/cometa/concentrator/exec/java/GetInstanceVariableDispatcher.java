package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.UUID;

public class GetInstanceVariableDispatcher extends GetFieldDispatcher {

	@Singleton
	@Inject
	public GetInstanceVariableDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
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
	protected final Type getBeforeExecMessageType() {
		return Type.GET_FIELD;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.RETURN_VALUE;
	}
}
