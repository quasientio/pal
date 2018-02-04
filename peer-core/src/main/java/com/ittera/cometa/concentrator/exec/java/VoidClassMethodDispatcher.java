package com.ittera.cometa.concentrator.exec.java;

import javax.inject.Singleton;

public class VoidClassMethodDispatcher extends ClassMethodDispatcher {

	@Singleton
	public VoidClassMethodDispatcher() {
	}

	@Override
	protected boolean returnsVoid() {
		return true;
	}
}
