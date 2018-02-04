package com.ittera.cometa.concentrator.exec.java;

import javax.inject.Singleton;

public class NonVoidInstanceMethodDispatcher extends InstanceMethodDispatcher {

	@Singleton
	public NonVoidInstanceMethodDispatcher() {
	}

	@Override
	protected boolean returnsVoid() {
		return false;
	}
}
