package com.ittera.cometa.distributor.returntypes;

public class RuntimeExceptionWrapper {
  private RuntimeException runtimeException;

  public RuntimeExceptionWrapper(RuntimeException runtimeException) {
    this.runtimeException = runtimeException;
  }

  public RuntimeException getRuntimeException() {
    return runtimeException;
  }
}
