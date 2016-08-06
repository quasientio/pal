package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class DoubleDouble implements Primitivo {
  double value;

  DoubleDouble(double d) {
    value = d;
  }

  public Class cogerClass() {
    return double.class;
  }

  public Object cogerJavaWrapper() {
    return new Double(value);
  }
}
