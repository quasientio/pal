package com.ittera.cometa.util;


/**
 *
 * @author libre
 */
class CharChar implements Primitivo {
  char value;

  CharChar(char c) {
    value = c;
  }

  public Class cogerClass() {
    return char.class;
  }

  public Object cogerJavaWrapper() {
    return new Character(value);
  }
}
