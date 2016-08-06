package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class ByteByte implements Primitivo {
  byte value;

  ByteByte(byte b) {
    value = b;
  }

  public Class cogerClass() {
    return byte.class;
  }

  public Object cogerJavaWrapper() {
    return new Byte(value);
  }
}
