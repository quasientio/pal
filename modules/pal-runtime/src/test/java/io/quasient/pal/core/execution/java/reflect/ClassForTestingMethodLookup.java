/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java.reflect;

/** This class is used for testing ReflectionHelper.lookupMethod() */
@SuppressWarnings("unused")
public class ClassForTestingMethodLookup {

  // <editor-fold desc="Basic string N-param testing">
  public String methodWithNoParams() {
    return "methodWithNoParams";
  }

  public String methodWithOneParam(String param1) {
    return "methodWithOneStringParam";
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

  // <editor-fold desc="Arrays testing">
  public String methodWithArrayParam(double[] param1) {
    return "doubleArrayParam";
  }

  public String methodWithArrayParam(Double[] param1) {
    return "DoubleArrayParam";
  }

  public String methodWithArrayParam(Number[] param1) {
    return "NumberArrayParam";
  }

  public String methodWithArrayParam(Object[] param1) {
    return "ObjectArrayParam";
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

  String packageProtectedMethodWithOneParam(String param1) {
    return "packageVisibleMethodWithOneParam";
  }

  // </editor-fold>

  // <editor-fold desc="Overloading and widening testing">
  public String methodWithTwoParams(String param1, String param2) {
    return "methodWithTwoStringParams";
  }

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

  public String methodWithOneFloatAndDoubleParam(float param1, double param2) {
    return "methodWithOneFloatAndDoubleParam";
  }

  // </editor-fold>

  // <editor-fold desc="Varargs testing">
  public String methodWithFloatVarargs(Float... params) {
    return "FloatVarargs";
  }

  public String methodWithVarargs(String... params) {
    return "methodWithStringVarargs";
  }

  public String methodWithVarargs(String param, int... params) {
    return "methodWithIntVarargs";
  }

  public String methodWithVarargs(Object... params) {
    return "methodWithObjectVarargs";
  }

  // </editor-fold>

  // <editor-fold desc="Cache testing">
  public String methodForCacheTest(String param1, Float param2, String param3) {
    return "methodForCacheTest";
  }

  // </editor-fold>

  // <editor-fold desc="Ambiguous testing">
  public String methodWithThreeParams(String param1, String param2, String param3) {
    return "methodWithTwoParams";
  }

  public String methodWithThreeParams(String param1, String param2, Object param3) {
    return "methodWithTwoParams";
  }

  public String methodWithObjectAndNumber(Object param1, Number param2) {
    return "methodWithObjectAndNumber";
  }

  public String methodWithObjectAndNumber(Object param1, Integer param2) {
    return "methodWithObjectAndNumber";
  }
  // </editor-fold>
}
