package net.ittera.pal.core.exec.java.reflect;

/** This class is used for testing ReflectionHelper.lookupMethod() TODO: add tests for generics */
public class ClassForTestingMethodLookup {

  // <editor-fold desc="Basic string N-param testing">
  public String methodWithNoParams() {
    return "methodWithNoParams";
  }

  public String methodWithOneParam(String param1) {
    return "methodWithOneStringParam";
  }

  public String methodWithTwoParams(String param1, String param2) {
    return "methodWithTwoStringParams";
  }
  // </editor-fold>

  // <editor-fold desc="Primitive type testing">
  public String methodWithOneParam(char param1) {
    return "methodWithOne_charParam";
  }

  public String methodWithOneParam(boolean param1) {
    return "methodWithOne_booleanParam";
  }

  public String methodWithOneParam(byte param1) {
    return "methodWithOne_byteParam";
  }

  public String methodWithOneParam(short param1) {
    return "methodWithOne_shortParam";
  }

  public String methodWithOneParam(int param1) {
    return "methodWithOne_intParam";
  }

  public String methodWithOneParam(long param1) {
    return "methodWithOne_longParam";
  }

  public String methodWithOneParam(float param1) {
    return "methodWithOne_floatParam";
  }

  public String methodWithOneParam(double param1) {
    return "methodWithOne_doubleParam";
  }
  // </editor-fold>

  // <editor-fold desc="Wrapper type testing">
  public String methodWithOneParam(Character param1) {
    return "methodWithOneCharacterParam";
  }

  public String methodWithOneParam(Boolean param1) {
    return "methodWithOneBooleanParam";
  }

  public String methodWithOneParam(Byte param1) {
    return "methodWithOneByteParam";
  }

  public String methodWithOneParam(Short param1) {
    return "methodWithOneShortParam";
  }

  public String methodWithOneParam(Integer param1) {
    return "methodWithOneIntegerParam";
  }

  public String methodWithOneParam(Long param1) {
    return "methodWithOneLongParam";
  }

  public String methodWithOneParam(Float param1) {
    return "methodWithOneFloatParam";
  }

  public String methodWithOneParam(Double param1) {
    return "methodWithOneDoubleParam";
  }
  // </editor-fold>

  // <editor-fold desc="Object type testing">
  public String methodWithOneParam(Object param1) {
    return "methodWithOneObjectParam";
  }
  // </editor-fold>

  // <editor-fold desc="Visibility testing">
  public String publicMethodWithOneParam(String param1) {
    return "publicMethodWithOneParam";
  }

  private String privateMethodWithOneParam(String param1) {
    return "privateMethodWithOneParam";
  }

  protected String protectedMethodWithOneParam(String param1) {
    return "protectedMethodWithOneParam";
  }

  String packageVisibleMethodWithOneParam(String param1) {
    return "packageVisibleMethodWithOneParam";
  }
  // </editor-fold>

  // <editor-fold desc="Overloading and widening testing">
  public String methodWithTwoParams(String param1, Object param2) {
    return "methodWithStringAndObjectParams";
  }

  public String methodWithTwoParams(String param1, Integer param2) {
    return "methodWithStringAndIntegerParams";
  }

  public String methodWithTwoParams(String param1, Long param2) {
    return "methodWithStringAndLongParams";
  }

  public String methodWithTwoParams(String param1, Float param2) {
    return "methodWithStringAndFloatParams";
  }

  public String methodWithTwoParams(String param1, Double param2) {
    return "methodWithStringAndDoubleParams";
  }
  // </editor-fold>

  // <editor-fold desc="Varargs testing">
  public String methodWithVarargs(String... params) {
    return "methodWithStringVarargs";
  }

  public String methodWithVarargs(Object... params) {
    return "methodWithObjectVarargs";
  }

  public String methodWithVarargsAndOneParam(String param1, String... params) {
    return "methodWithVarargsAndOneParam";
  }
  // </editor-fold>

  // <editor-fold desc="Ambiguous testing">
  public String methodWithThreeParams(String param1, String param2, String param3) {
    return "methodWithTwoParams";
  }

  public String methodWithThreeParams(String param1, String param2, Object param3) {
    return "methodWithTwoParams";
  }
  // </editor-fold>

  // <editor-fold desc="Cache testing">
  public String methodForCacheTest(String param1, Float param2, String param3) {
    return "methodForCacheTest";
  }

  // </editor-fold>

  // <editor-fold desc="Ambiguous call testing">
  public String methodWithObjectAndNumber(Object param1, Number param2) {
    return "methodWithObjectAndNumber";
  }

  public String methodWithObjectAndNumber(Object param1, Integer param2) {
    return "methodWithObjectAndNumber";
  }
  // </editor-fold>
}
