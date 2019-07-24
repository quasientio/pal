package com.ittera.cometa.concentrator.exec.java;

public class InvocationExceptionWrapper {

	private final Exception exception;

	InvocationExceptionWrapper(Exception exception) {
		this.exception = exception;
	}

	public Exception getException() {
		return exception;
	}
}
