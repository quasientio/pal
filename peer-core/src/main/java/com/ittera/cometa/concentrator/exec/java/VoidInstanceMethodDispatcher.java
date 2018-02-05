package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import javax.inject.Singleton;

public class VoidInstanceMethodDispatcher extends InstanceMethodDispatcher {

	@Singleton
	public VoidInstanceMethodDispatcher() {
	}

	@Override
	protected final boolean returnsVoid() {
		return true;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.VOID;
	}
}
