package com.ittera.cometa.apps;

public class Constructors {

  private Constructors innerInstance;

  public Constructors() {}

  public Constructors(Integer anInt) {}

  public Constructors(String someString) {
    Integer integer = Integer.parseInt(someString);
  }

  Constructors(String msg, Integer times) {

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) {
      sb.append(msg).append(",");
    }
  }

  private Constructors(String[] aStringArrayParam) {

    StringBuilder sb = new StringBuilder();
    for (String anAStringArrayParam : aStringArrayParam) {
      sb.append(anAStringArrayParam).append(",");
    }
  }

  protected Constructors(Constructors aConstructor) {
    this.innerInstance = aConstructor;
  }
}
