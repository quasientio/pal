package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class FloatFloat implements Primitivo {
  float value;

  FloatFloat(float f) {
    value = f;
  }

  public Class cogerClass() {
    return float.class;
  }

  public Object cogerJavaWrapper() {
    return new Float(value);
  }
}
