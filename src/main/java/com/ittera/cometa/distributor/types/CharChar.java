package com.ittera.cometa.distributor.types;


import com.ittera.cometa.util.Primitive;

/**
 *
 * @author libre
 */
public class CharChar implements Primitive {
  char value;

  public CharChar(char c) {
    value = c;
  }

  public Class cogerClass() {
    return char.class;
  }

  public Object cogerJavaWrapper() {
    return new Character(value);
  }
}
