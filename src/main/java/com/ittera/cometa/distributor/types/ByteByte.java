package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class ByteByte implements Primitive {
  byte value;

  public ByteByte(byte b) {
    value = b;
  }

  public Class cogerClass() {
    return byte.class;
  }

  public Object cogerJavaWrapper() {
    return new Byte(value);
  }
}
