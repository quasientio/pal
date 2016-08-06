package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class IntegerInteger implements Primitive {
  int value;

  public IntegerInteger(int i) {
    value = i;
  }

  public Class cogerClass() {
    return int.class;
  }

  public Object cogerJavaWrapper() {
    return new Integer(value);
  }
}
