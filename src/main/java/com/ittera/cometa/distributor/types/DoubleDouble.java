package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class DoubleDouble implements Primitive {
  double value;

  public DoubleDouble(double d) {
    value = d;
  }

  public Class cogerClass() {
    return double.class;
  }

  public Object cogerJavaWrapper() {
    return new Double(value);
  }
}
