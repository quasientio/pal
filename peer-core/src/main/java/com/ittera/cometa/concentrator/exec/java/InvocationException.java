package com.ittera.cometa.concentrator.exec.java;

public class InvocationException {

	private Exception exception;

	InvocationException(Exception exception) {
		this.exception = exception;
	}

	public Exception getException() {
		return exception;
	}
}
