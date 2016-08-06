package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class LongLong implements Primitive {
  long value;

  public LongLong(long l) {
    value = l;
  }

  public Class cogerClass() {
    return long.class;
  }

  public Object cogerJavaWrapper() {
    return new Long(value);
  }
}
