package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class BooleanBoolean implements Primitive {
  boolean value;

  public BooleanBoolean(boolean b) {
    value = b;
  }

  public Class cogerClass() {
    return boolean.class;
  }

  public Object cogerJavaWrapper() {
    return new Boolean(value);
  }
}
