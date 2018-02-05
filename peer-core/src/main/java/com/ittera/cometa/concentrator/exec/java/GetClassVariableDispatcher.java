package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;

public class GetClassVariableDispatcher extends FieldOpDispatcher {

	@Singleton
	public GetClassVariableDispatcher() {
	}

	@Override
	protected final boolean returnsVoid() {
		return false;
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.GET_STATIC;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.RETURN_VALUE;
	}
}
