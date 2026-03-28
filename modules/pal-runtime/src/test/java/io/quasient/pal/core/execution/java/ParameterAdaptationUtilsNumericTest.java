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
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.junit.Test;

/**
 * Additional unit tests for {@link ParameterAdaptationUtils} focusing on numeric type adaptations
 * and edge cases.
 */
public class ParameterAdaptationUtilsNumericTest {

  /** Test class with various numeric parameter types. */
  @SuppressWarnings("unused")
  public static class NumericMethods {
    public static void acceptsDouble(double d) {}

    public static void acceptsFloat(float f) {}

    public static void acceptsShort(short s) {}

    public static void acceptsByte(byte b) {}

    public static void acceptsInt(int i) {}

    public static void acceptsLong(long l) {}

    public static void acceptsDoubleWrapper(Double d) {}

    public static void acceptsFloatWrapper(Float f) {}

    public static void acceptsShortWrapper(Short s) {}

    public static void acceptsByteWrapper(Byte b) {}

    public static void acceptsChar(char c) {}

    public static void acceptsBoolean(boolean b) {}
  }

  private static Method getMethod(String name, Class<?>... params) throws Exception {
    return NumericMethods.class.getMethod(name, params);
  }

  // ===== adaptToMethodParam null handling =====

  /** Tests that null value returns null. */
  @Test
  public void adaptToMethodParam_nullValue_returnsNull() {
    Object result = ParameterAdaptationUtils.adaptToMethodParam(null, Integer.class);
    assertThat(result, nullValue());
  }

  // ===== Primitive double adaptation =====

  /** Tests that Double is adapted to primitive double. */
  @Test
  public void adaptDouble_toDoublePrimitive_returnsDoubleValue() throws Exception {
    Method m = getMethod("acceptsDouble", double.class);
    MessageArgument[] args = {new MessageArgument(3.14, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Double.class));
    assertThat((Double) adapted[0], is(3.14));
  }

  // ===== Primitive float adaptation =====

  /** Tests that Double is adapted to primitive float. */
  @Test
  public void adaptDouble_toFloatPrimitive_returnsFloatValue() throws Exception {
    Method m = getMethod("acceptsFloat", float.class);
    MessageArgument[] args = {new MessageArgument(2.5, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Float.class));
    assertThat((Float) adapted[0], is(2.5f));
  }

  // ===== Primitive short adaptation =====

  /** Tests that Double is adapted to primitive short. */
  @Test
  public void adaptDouble_toShortPrimitive_returnsShortValue() throws Exception {
    Method m = getMethod("acceptsShort", short.class);
    MessageArgument[] args = {new MessageArgument(100.0, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Short.class));
    assertThat((Short) adapted[0], is((short) 100));
  }

  // ===== Primitive byte adaptation =====

  /** Tests that Double is adapted to primitive byte. */
  @Test
  public void adaptDouble_toBytePrimitive_returnsByteValue() throws Exception {
    Method m = getMethod("acceptsByte", byte.class);
    MessageArgument[] args = {new MessageArgument(42.0, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Byte.class));
    assertThat((Byte) adapted[0], is((byte) 42));
  }

  // ===== Wrapper type adaptations =====

  /** Tests Double to Double wrapper type. */
  @Test
  public void adaptDouble_toDoubleWrapper_returnsDoubleValue() throws Exception {
    Method m = getMethod("acceptsDoubleWrapper", Double.class);
    MessageArgument[] args = {new MessageArgument(5.5, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Double.class));
    assertThat((Double) adapted[0], is(5.5));
  }

  /** Tests Double to Float wrapper type. */
  @Test
  public void adaptDouble_toFloatWrapper_returnsFloatValue() throws Exception {
    Method m = getMethod("acceptsFloatWrapper", Float.class);
    MessageArgument[] args = {new MessageArgument(1.5, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Float.class));
    assertThat((Float) adapted[0], is(1.5f));
  }

  /** Tests Double to Short wrapper type. */
  @Test
  public void adaptDouble_toShortWrapper_returnsShortValue() throws Exception {
    Method m = getMethod("acceptsShortWrapper", Short.class);
    MessageArgument[] args = {new MessageArgument(50.0, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Short.class));
    assertThat((Short) adapted[0], is((short) 50));
  }

  /** Tests Double to Byte wrapper type. */
  @Test
  public void adaptDouble_toByteWrapper_returnsByteValue() throws Exception {
    Method m = getMethod("acceptsByteWrapper", Byte.class);
    MessageArgument[] args = {new MessageArgument(10.0, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], instanceOf(Byte.class));
    assertThat((Byte) adapted[0], is((byte) 10));
  }

  // ===== Fractional part validation =====

  /** Tests that fractional double throws for long target. */
  @Test
  public void adaptFractionalDouble_toLong_throwsRuntimeException() throws Exception {
    Method m = getMethod("acceptsLong", long.class);
    MessageArgument[] args = {new MessageArgument(3.14, false)};

    assertThrows(
        RuntimeException.class, () -> ParameterAdaptationUtils.adaptParametersForMethod(m, args));
  }

  /** Tests that fractional double throws for int target. */
  @Test
  public void adaptFractionalDouble_toInt_throwsRuntimeException() throws Exception {
    Method m = getMethod("acceptsInt", int.class);
    MessageArgument[] args = {new MessageArgument(2.5, false)};

    assertThrows(
        RuntimeException.class, () -> ParameterAdaptationUtils.adaptParametersForMethod(m, args));
  }

  // ===== Primitive compatibility tests =====

  /** Tests that Boolean is compatible with boolean primitive. */
  @Test
  public void primitiveBoolean_withBooleanValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsBoolean", boolean.class);
    Boolean value = Boolean.TRUE;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Integer is compatible with int primitive. */
  @Test
  public void primitiveInt_withIntegerValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsInt", int.class);
    Integer value = 42;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Double is compatible with double primitive. */
  @Test
  public void primitiveDouble_withDoubleValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsDouble", double.class);
    Double value = 3.14;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Float is compatible with float primitive. */
  @Test
  public void primitiveFloat_withFloatValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsFloat", float.class);
    Float value = 2.5f;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Short is compatible with short primitive. */
  @Test
  public void primitiveShort_withShortValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsShort", short.class);
    Short value = (short) 100;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Byte is compatible with byte primitive. */
  @Test
  public void primitiveByte_withByteValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsByte", byte.class);
    Byte value = (byte) 10;
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  /** Tests that Character is compatible with char primitive. */
  @Test
  public void primitiveChar_withCharacterValue_skipsAdaptation() throws Exception {
    Method m = getMethod("acceptsChar", char.class);
    Character value = 'A';
    MessageArgument[] args = {new MessageArgument(value, false)};

    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, args);

    assertThat(adapted[0], sameInstance(value));
  }

  // ===== Null raw args handling =====

  /** Tests that null raw args returns empty array. */
  @Test
  public void adaptParameters_nullRawArgs_returnsEmptyArray() throws Exception {
    Method m = getMethod("acceptsInt", int.class);

    // Using reflection to call private adaptParameters with null
    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, null);

    assertThat(adapted.length, is(0));
  }

  // ===== Non-numeric type adaptation =====

  /** Tests that non-Number value throws for numeric target. */
  @Test
  public void adaptNonNumber_toNumeric_throwsIllegalArgumentException() throws Exception {
    Method m = getMethod("acceptsInt", int.class);
    MessageArgument[] args = {new MessageArgument("not-a-number", false)};

    assertThrows(
        IllegalArgumentException.class,
        () -> ParameterAdaptationUtils.adaptParametersForMethod(m, args));
  }

  // ===== Edge case: unhandled numeric type returns original =====

  /** Tests adaptToMethodParam with non-parameterized non-class Type returns value as-is. */
  @Test
  public void adaptToMethodParam_wildcardType_returnsOriginalValue() {
    // Create a Type that is neither ParameterizedType nor Class
    // This exercises the fallback path
    Type wildcardType =
        new Type() {
          @Override
          public String getTypeName() {
            return "?";
          }
        };

    Object value = "test";
    Object result = ParameterAdaptationUtils.adaptToMethodParam(value, wildcardType);

    assertThat(result, sameInstance(value));
  }

  /** Tests that value already matching target class is returned as-is. */
  @Test
  public void adaptToMethodParam_alreadyMatchingClass_returnsSameInstance() {
    String value = "hello";
    Object result = ParameterAdaptationUtils.adaptToMethodParam(value, String.class);

    assertThat(result, sameInstance(value));
  }

  /** Tests adaptation with Integer value to Integer class. */
  @Test
  public void adaptInteger_toIntegerClass_returnsSameInstance() {
    Integer value = 42;
    Object result = ParameterAdaptationUtils.adaptToMethodParam(value, Integer.class);

    assertThat(result, sameInstance(value));
  }

  /** Tests that Long value is not adapted when target is Long class. */
  @Test
  public void adaptLong_toLongClass_returnsSameInstance() {
    Long value = 100L;
    Object result = ParameterAdaptationUtils.adaptToMethodParam(value, Long.class);

    assertThat(result, sameInstance(value));
  }

  // ===== ParameterizedType that is not Collection or Map =====

  /** Tests that parameterized type that is not Collection/Map returns value as-is. */
  @Test
  public void adaptToMethodParam_nonCollectionParameterizedType_returnsOriginalValue()
      throws Exception {
    // Use a method that takes Comparable<String> or similar
    // For simplicity, we just verify through adaptToMethodParam directly
    java.lang.reflect.ParameterizedType pType =
        new java.lang.reflect.ParameterizedType() {
          @Override
          public Type[] getActualTypeArguments() {
            return new Type[] {String.class};
          }

          @Override
          public Type getRawType() {
            return Comparable.class; // Not a Collection or Map
          }

          @Override
          public Type getOwnerType() {
            return null;
          }
        };

    String value = "test";
    Object result = ParameterAdaptationUtils.adaptToMethodParam(value, pType);

    assertThat(result, sameInstance(value));
  }
}
