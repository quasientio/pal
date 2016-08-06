package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class ShortShort implements Primitive {
  short value;

  public ShortShort(short s) {
    value = s;
  }

  public Class cogerClass() {
    return short.class;
  }

  public Object cogerJavaWrapper() {
    return new Short(value);
  }
}
