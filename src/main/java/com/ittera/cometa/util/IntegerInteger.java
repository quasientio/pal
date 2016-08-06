package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class IntegerInteger implements Primitivo {
  int value;

  IntegerInteger(int i) {
    value = i;
  }

  public Class cogerClass() {
    return int.class;
  }

  public Object cogerJavaWrapper() {
    return new Integer(value);
  }
}
