package com.ittera.cometa.messages;

public class NonWrappableObjectException extends RuntimeException {

  private Object nonWrappableObject;
  private Class nonWrappableObjectClass;
  private String nonWrappableObjectClassName;

  public NonWrappableObjectException(String msg) {
    super(msg);
  }

  public NonWrappableObjectException(Object nonWrappableObject) {
    this.nonWrappableObject = nonWrappableObject;
  }

  public NonWrappableObjectException(Object nonWrappableObject, Class nonWrappableObjectClass) {
    this(nonWrappableObject);
    this.nonWrappableObjectClass = nonWrappableObjectClass;
  }

  public NonWrappableObjectException(
      Object nonWrappableObject, String nonWrappableObjectClassName) {
    this(nonWrappableObject);
    this.nonWrappableObjectClassName = nonWrappableObjectClassName;
  }

  public Object getNonWrappableObject() {
    return nonWrappableObject;
  }

  public Class getNonWrappableObjectClass() {
    return nonWrappableObjectClass;
  }

  public String getNonWrappableObjectClassName() {
    return nonWrappableObjectClassName;
  }
}
