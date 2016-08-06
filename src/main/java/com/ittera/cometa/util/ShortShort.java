package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class ShortShort implements Primitivo {
  short value;

  ShortShort(short s) {
    value = s;
  }

  public Class cogerClass() {
    return short.class;
  }

  public Object cogerJavaWrapper() {
    return new Short(value);
  }
}
