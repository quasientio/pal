package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;

public class SetClassVariableDispatcher extends FieldOpDispatcher {

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
