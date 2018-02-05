package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;

public class NonVoidInstanceMethodDispatcher extends InstanceMethodDispatcher {

	@Singleton
	public NonVoidInstanceMethodDispatcher() {
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
