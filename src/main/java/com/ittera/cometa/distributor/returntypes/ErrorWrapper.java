package com.ittera.cometa.distributor.returntypes;

public class ErrorWrapper {
  private Error error;

  public ErrorWrapper(Error error) {
    this.error = error;
  }

  public Throwable getError() {
    return error;
  }
}
