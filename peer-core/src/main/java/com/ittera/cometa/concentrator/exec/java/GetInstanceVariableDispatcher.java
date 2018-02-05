package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;

public class GetInstanceVariableDispatcher extends FieldOpDispatcher {

	@Singleton
	public GetInstanceVariableDispatcher() {
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
