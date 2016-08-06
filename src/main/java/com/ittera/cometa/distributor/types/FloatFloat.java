package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class FloatFloat implements Primitive {
  float value;

  public FloatFloat(float f) {
    value = f;
  }

  public Class cogerClass() {
    return float.class;
  }

  public Object cogerJavaWrapper() {
    return new Float(value);
  }
}
