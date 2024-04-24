package net.ittera.pal.core.exec.java.reflect;

/** This class is used for testing ReflectionHelper.lookupConstructor() */
public class ClassForTestingConstructorLookup {
  private String param;

  public String getParam() {
    return param;
  }

  // <editor-fold desc="Basic string N-param testing">
  public ClassForTestingConstructorLookup() {
    this.param = "noParams";
  }

  // </editor-fold>

  public ClassForTestingConstructorLookup(String param1) {
    this.param = "stringParam";
  }

  public ClassForTestingConstructorLookup(String param1, String param2) {
    this.param = "twoStringParams";
  }

  // <editor-fold desc="Primitive type testing">
  public ClassForTestingConstructorLookup(char param1) {
    this.param = "charParam";
  }

  public ClassForTestingConstructorLookup(boolean param1) {
    this.param = "booleanParam";
  }

  public ClassForTestingConstructorLookup(byte param1) {
    this.param = "byteParam";
  }

  public ClassForTestingConstructorLookup(short param1) {
    this.param = "shortParam";
  }

  public ClassForTestingConstructorLookup(int param1) {
    this.param = "intParam";
  }

  public ClassForTestingConstructorLookup(long param1) {
    this.param = "longParam";
  }

  public ClassForTestingConstructorLookup(float param1) {
    this.param = "floatParam";
  }

  public ClassForTestingConstructorLookup(double param1) {
    this.param = "doubleParam";
  }

  public ClassForTestingConstructorLookup(boolean param0, float param1, double param2) {
    this.param = "boolFloatAndDoubleParams";
  }

  // </editor-fold>

  // <editor-fold desc="Wrapper type testing">
  public ClassForTestingConstructorLookup(Character param1) {
    this.param = "CharacterParam";
  }

  public ClassForTestingConstructorLookup(Boolean param1) {
    this.param = "BooleanParam";
  }

  public ClassForTestingConstructorLookup(Byte param1) {
    this.param = "ByteParam";
  }

  public ClassForTestingConstructorLookup(Short param1) {
    this.param = "ShortParam";
  }

  public ClassForTestingConstructorLookup(Integer param1) {
    this.param = "IntegerParam";
  }

  public ClassForTestingConstructorLookup(Long param1) {
    this.param = "LongParam";
  }

  public ClassForTestingConstructorLookup(Float param1) {
    this.param = "FloatParam";
  }

  public ClassForTestingConstructorLookup(Double param1) {
    this.param = "DoubleParam";
  }

  // </editor-fold>

  // <editor-fold desc="Arrays testing">
  public ClassForTestingConstructorLookup(double[] param1) {
    this.param = "doubleArrayParam";
  }

  public ClassForTestingConstructorLookup(Double[] param1) {
    this.param = "DoubleArrayParam";
  }

  public ClassForTestingConstructorLookup(Number[] param1) {
    this.param = "NumberArrayParam";
  }

  public ClassForTestingConstructorLookup(Object[] param1) {
    this.param = "ObjectArrayParam";
  }

  // </editor-fold>

  // <editor-fold desc="Varargs testing">
  public ClassForTestingConstructorLookup(Float... param1) {
    this.param = "FloatVarargs";
  }

  public ClassForTestingConstructorLookup(int param1, String... param2) {
    this.param = "constructorWithStringVarargs";
  }

  public ClassForTestingConstructorLookup(String param1, int... param2) {
    this.param = "constructorWithIntVarargs";
  }

  public ClassForTestingConstructorLookup(int param1, Object... param2) {
    this.param = "constructorWithObjectVarargs";
  }

  // </editor-fold>

  // <editor-fold desc="Object type testing">
  public ClassForTestingConstructorLookup(Object param1) {
    this.param = "ObjectParam";
  }

  // </editor-fold>

  // <editor-fold desc="Cache testing">
  public ClassForTestingConstructorLookup(Integer i, Float f, String s) {
    this.param = "IntegerFloatStringParams";
  }

  // </editor-fold>

  // <editor-fold desc="Visibility testing">
  public ClassForTestingConstructorLookup(byte param1, byte param2) {
    this.param = "publicConstructor";
  }

  private ClassForTestingConstructorLookup(byte param1, byte param2, int param3) {
    this.param = "privateConstructor";
  }

  protected ClassForTestingConstructorLookup(byte param1, byte param2, short param3) {
    this.param = "protectedConstructor";
  }

  ClassForTestingConstructorLookup(byte param1, byte param2, long param3) {
    this.param = "packageProtectedConstructor";
  }

  // </editor-fold>

  // <editor-fold desc="Ambiguous call testing">

  public ClassForTestingConstructorLookup(Object param1, Number param2) {
    this.param = "ObjectNumberParams";
  }

  public ClassForTestingConstructorLookup(Object param1, Integer param2) {
    this.param = "ObjectIntegerParams";
  }

  // </editor-fold>
}
