package com.ittera.cometa.messages;

public class NonWrappableObjectException extends RuntimeException {

  private final transient Object nonWrappableObject;

  public NonWrappableObjectException(Object nonWrappableObject) {
    this.nonWrappableObject = nonWrappableObject;
  }

  public Object getNonWrappableObject() {
    return nonWrappableObject;
  }
}
