package com.ittera.cometa.distributor.returntypes;

public class ExceptionWrapper {
  private Exception exception;

  public ExceptionWrapper(Exception exception) {
    this.exception = exception;
  }

  public Exception getException() {
    return exception;
  }
}
