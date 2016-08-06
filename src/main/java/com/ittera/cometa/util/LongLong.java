package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class LongLong implements Primitivo {
  long value;

  LongLong(long l) {
    value = l;
  }

  public Class cogerClass() {
    return long.class;
  }

  public Object cogerJavaWrapper() {
    return new Long(value);
  }
}
