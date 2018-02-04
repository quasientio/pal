package com.ittera.cometa.concentrator.exec.java;

import javax.inject.Singleton;

public class VoidInstanceMethodDispatcher extends InstanceMethodDispatcher {

	@Singleton
	public VoidInstanceMethodDispatcher() {
	}

	@Override
	protected boolean returnsVoid() {
		return true;
	}
}
