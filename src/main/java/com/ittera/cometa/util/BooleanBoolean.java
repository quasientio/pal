package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class BooleanBoolean implements Primitivo {
  boolean value;

  BooleanBoolean(boolean b) {
    value = b;
  }

  public Class cogerClass() {
    return boolean.class;
  }

  public Object cogerJavaWrapper() {
    return new Boolean(value);
  }
}
