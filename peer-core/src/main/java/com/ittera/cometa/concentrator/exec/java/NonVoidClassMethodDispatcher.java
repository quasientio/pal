package com.ittera.cometa.concentrator.exec.java;

import javax.inject.Singleton;

public class NonVoidClassMethodDispatcher extends ClassMethodDispatcher {

	@Singleton
	public NonVoidClassMethodDispatcher() {
	}

	@Override
	protected boolean returnsVoid() {
		return false;
	}
}
