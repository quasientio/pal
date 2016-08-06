package com.ittera.cometa.util;

public class SuperStack extends java.util.Stack<Object> {
  private static final long serialVersionUID = 20040622L;

  public void push(int i) {
    super.push(new IntegerInteger(i));
  }

  public void push(char c) {
    super.push(new CharChar(c));
  }

  public void push(long l) {
    super.push(new LongLong(l));
  }

  public void push(double d) {
    super.push(new DoubleDouble(d));
  }

  public void push(float f) {
    super.push(new FloatFloat(f));
  }

  public void push(short s) {
    super.push(new ShortShort(s));
  }

  public void push(byte b) {
    super.push(new ByteByte(b));
  }

  public void push(boolean b) {
    super.push(new BooleanBoolean(b));
  }

  @Override
  public Object push(Object o) {
    super.push(o);
    return null;
  }

  @Override
  public String toString() {
    return super.toString();
  }
}
