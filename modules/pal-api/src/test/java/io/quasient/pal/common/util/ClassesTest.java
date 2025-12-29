/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClassesTest {

  @Test
  public void getClassForPrimitive() {
    // reminder that <primitive>.class == <Wrapper>.type
    // -> assert boolean.class == Boolean.TYPE;

    assertEquals(boolean.class, Classes.getClassForPrimitive("boolean"));
    assertEquals(Boolean.TYPE, Classes.getClassForPrimitive("boolean"));
    assertEquals(Byte.TYPE, Classes.getClassForPrimitive("byte"));
    assertEquals(Character.TYPE, Classes.getClassForPrimitive("char"));
    assertEquals(Short.TYPE, Classes.getClassForPrimitive("short"));
    assertEquals(Integer.TYPE, Classes.getClassForPrimitive("int"));
    assertEquals(Long.TYPE, Classes.getClassForPrimitive("long"));
    assertEquals(Double.TYPE, Classes.getClassForPrimitive("double"));
    assertEquals(Float.TYPE, Classes.getClassForPrimitive("float"));
    assertEquals(Void.TYPE, Classes.getClassForPrimitive("void"));
  }

  @Test
  public void isPrimitiveWrapper() {
    assertFalse(Classes.isPrimitiveWrapper(boolean.class));
    assertTrue(Classes.isPrimitiveWrapper(Boolean.class));

    assertFalse(Classes.isPrimitiveWrapper(byte.class));
    assertTrue(Classes.isPrimitiveWrapper(Byte.class));

    assertFalse(Classes.isPrimitiveWrapper(char.class));
    assertTrue(Classes.isPrimitiveWrapper(Character.class));

    assertFalse(Classes.isPrimitiveWrapper(short.class));
    assertTrue(Classes.isPrimitiveWrapper(Short.class));

    assertFalse(Classes.isPrimitiveWrapper(int.class));
    assertTrue(Classes.isPrimitiveWrapper(Integer.class));

    assertFalse(Classes.isPrimitiveWrapper(long.class));
    assertTrue(Classes.isPrimitiveWrapper(Long.class));

    assertFalse(Classes.isPrimitiveWrapper(double.class));
    assertTrue(Classes.isPrimitiveWrapper(Double.class));

    assertFalse(Classes.isPrimitiveWrapper(float.class));
    assertTrue(Classes.isPrimitiveWrapper(Float.class));

    assertFalse(Classes.isPrimitiveWrapper(void.class));
    // !! Void.class is a pseudo type, and it's not considered a Wrapper !!
    assertFalse(Classes.isPrimitiveWrapper(Void.class));

    // misc stuff that shouldn't be
    assertFalse(Classes.isPrimitiveWrapper(String.class));
    assertFalse(Classes.isPrimitiveWrapper(Object.class));
    assertFalse(Classes.isPrimitiveWrapper(Enum.class));
    assertFalse(Classes.isPrimitiveWrapper(Throwable.class));
  }

  @Test
  public void isPrimitiveOrWrapper() {
    assertTrue(Classes.isPrimitiveOrWrapper(boolean.class)); // == Boolean.TYPE
    assertTrue(Classes.isPrimitiveOrWrapper(Boolean.class));

    assertTrue(Classes.isPrimitiveOrWrapper(byte.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Byte.class));

    assertTrue(Classes.isPrimitiveOrWrapper(char.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Character.class));

    assertTrue(Classes.isPrimitiveOrWrapper(short.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Short.class));

    assertTrue(Classes.isPrimitiveOrWrapper(int.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Integer.class));

    assertTrue(Classes.isPrimitiveOrWrapper(long.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Long.class));

    assertTrue(Classes.isPrimitiveOrWrapper(double.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Double.class));

    assertTrue(Classes.isPrimitiveOrWrapper(float.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Float.class));

    assertTrue(Classes.isPrimitiveOrWrapper(void.class));
    // !! Void.class is a pseudo type, and it's not considered a Wrapper !!
    assertFalse(Classes.isPrimitiveOrWrapper(Void.class));

    // misc stuff that shouldn't be
    assertFalse(Classes.isPrimitiveOrWrapper(null));
    assertFalse(Classes.isPrimitiveOrWrapper(String.class));
    assertFalse(Classes.isPrimitiveOrWrapper(Object.class));
    assertFalse(Classes.isPrimitiveOrWrapper(Enum.class));
    assertFalse(Classes.isPrimitiveOrWrapper(Throwable.class));
  }

  @Test
  public void isPrimitive() {
    assertTrue(Classes.isPrimitive("boolean"));
    assertTrue(Classes.isPrimitive("byte"));
    assertTrue(Classes.isPrimitive("char"));
    assertTrue(Classes.isPrimitive("short"));
    assertTrue(Classes.isPrimitive("int"));
    assertTrue(Classes.isPrimitive("long"));
    assertTrue(Classes.isPrimitive("double"));
    assertTrue(Classes.isPrimitive("float"));

    assertFalse(Classes.isPrimitive("void"));
    assertFalse(Classes.isPrimitive("Integer"));
    assertFalse(Classes.isPrimitive("Double"));
    assertFalse(Classes.isPrimitive("String"));
    assertFalse(Classes.isPrimitive("Object"));
    assertFalse(Classes.isPrimitive("Enum"));
    assertFalse(Classes.isPrimitive("Throwable"));
  }

  @Test
  public void simpleToLongName() {
    assertEquals("java.lang.String", Classes.simpleToLongName("String"));
    assertEquals("java.lang.Character", Classes.simpleToLongName("Character"));
    assertEquals("java.lang.Boolean", Classes.simpleToLongName("Boolean"));
    assertEquals("java.lang.Byte", Classes.simpleToLongName("Byte"));
    assertEquals("java.lang.Short", Classes.simpleToLongName("Short"));
    assertEquals("java.lang.Integer", Classes.simpleToLongName("Integer"));
    assertEquals("java.lang.Long", Classes.simpleToLongName("Long"));
    assertEquals("java.lang.Float", Classes.simpleToLongName("Float"));
    assertEquals("java.lang.Double", Classes.simpleToLongName("Double"));
  }

  @Test
  public void isOneDimensionalPrimitiveWrapperArray() {
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Boolean[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Byte[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Character[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Short[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Integer[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Long[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Double[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveWrapperArray(Float[].class.getName()));

    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(boolean[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(byte[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(char[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(short[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(int[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(long[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(double[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(float[].class.getName()));

    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(Void[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(Object[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(String[].class.getName()));
  }

  @Test
  public void isOneDimensionalPrimitiveArray() {
    assertTrue(Classes.isOneDimensionalPrimitiveArray(boolean[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(byte[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(char[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(short[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(int[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(long[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(double[].class.getName()));
    assertTrue(Classes.isOneDimensionalPrimitiveArray(float[].class.getName()));

    assertFalse(Classes.isOneDimensionalPrimitiveArray(Boolean[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Byte[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Character[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Short[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Integer[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Long[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Double[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveArray(Float[].class.getName()));

    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(Void[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(Object[].class.getName()));
    assertFalse(Classes.isOneDimensionalPrimitiveWrapperArray(String[].class.getName()));
  }

  @Test
  public void isValidClassName_returnsTrueForValidClassNames() {
    assertTrue(Classes.isValidClassName("java.lang.String"));
    assertTrue(Classes.isValidClassName("java.util.List"));
    assertTrue(Classes.isValidClassName("util.io.quasient.pal.common.Classes"));
    assertTrue(Classes.isValidClassName("[Ljava.lang.String;"));
    assertTrue(Classes.isValidClassName("[I"));
  }

  @Test
  public void isValidClassName_returnsFalseForInvalidClassNames() {
    assertFalse(Classes.isValidClassName("123invalid"));
    assertFalse(Classes.isValidClassName("invalid-class"));
    assertFalse(Classes.isValidClassName("invalid package.name"));
    assertFalse(Classes.isValidClassName("invalid[package.name]"));
    assertFalse(Classes.isValidClassName("invalid[package.name];"));
  }

  @Test
  public void mapToProperArrayClassName_returnsProperClassnameOrNull() {
    assertNull(Classes.mapToProperArrayClassName(null));

    // int[] -> [I
    assertEquals("[I", Classes.mapToProperArrayClassName("int[]"));
    // Integer[] -> [Ljava.lang.Integer;
    assertEquals("[Ljava.lang.Integer;", Classes.mapToProperArrayClassName("Integer[]"));

    // boolean[] -> [Z
    assertEquals("[Z", Classes.mapToProperArrayClassName("boolean[]"));
    // Boolean[] -> [Ljava.lang.Boolean;
    assertEquals("[Ljava.lang.Boolean;", Classes.mapToProperArrayClassName("Boolean[]"));

    // byte[] -> [B
    assertEquals("[B", Classes.mapToProperArrayClassName("byte[]"));
    // Byte[] -> [Ljava.lang.Byte;
    assertEquals("[Ljava.lang.Byte;", Classes.mapToProperArrayClassName("Byte[]"));

    // char[] -> [C
    assertEquals("[C", Classes.mapToProperArrayClassName("char[]"));
    // Character[] -> [Ljava.lang.Character;
    assertEquals("[Ljava.lang.Character;", Classes.mapToProperArrayClassName("Character[]"));

    // short[] -> [S
    assertEquals("[S", Classes.mapToProperArrayClassName("short[]"));
    // Short[] -> [Ljava.lang.Short;
    assertEquals("[Ljava.lang.Short;", Classes.mapToProperArrayClassName("Short[]"));

    // long[] -> [J
    assertEquals("[J", Classes.mapToProperArrayClassName("long[]"));
    // Long[] -> [Ljava.lang.Long;
    assertEquals("[Ljava.lang.Long;", Classes.mapToProperArrayClassName("Long[]"));

    // float[] -> [F
    assertEquals("[F", Classes.mapToProperArrayClassName("float[]"));
    // Float[] -> [Ljava.lang.Float;
    assertEquals("[Ljava.lang.Float;", Classes.mapToProperArrayClassName("Float[]"));

    // double[] -> [D
    assertEquals("[D", Classes.mapToProperArrayClassName("double[]"));
    // Double[] -> [Ljava.lang.Double;
    assertEquals("[Ljava.lang.Double;", Classes.mapToProperArrayClassName("Double[]"));

    // String[] -> [Ljava.lang.String;
    assertEquals("[Ljava.lang.String;", Classes.mapToProperArrayClassName("String[]"));
  }

  @Test
  public void isValidClassName_returnsFalseForNull() {
    assertFalse(Classes.isValidClassName(null));
  }

  @Test
  public void isValidClassName_returnsFalseForEmptyString() {
    assertFalse(Classes.isValidClassName(""));
  }

  @Test
  public void getPrimitiveForWrapper_returnsPrimitiveClassForWrapper() {
    assertEquals(boolean.class, Classes.getPrimitiveClassForWrapper(Boolean.class));
    assertEquals(byte.class, Classes.getPrimitiveClassForWrapper(Byte.class));
    assertEquals(char.class, Classes.getPrimitiveClassForWrapper(Character.class));
    assertEquals(short.class, Classes.getPrimitiveClassForWrapper(Short.class));
    assertEquals(int.class, Classes.getPrimitiveClassForWrapper(Integer.class));
    assertEquals(long.class, Classes.getPrimitiveClassForWrapper(Long.class));
    assertEquals(float.class, Classes.getPrimitiveClassForWrapper(Float.class));
    assertEquals(double.class, Classes.getPrimitiveClassForWrapper(Double.class));
  }
}
