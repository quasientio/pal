/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.serdes.colfer.Wrapper;

public abstract class WrappingTestBase {
  protected static final List<Class> primitiveClasses =
      Arrays.asList(
          boolean.class,
          byte.class,
          char.class,
          double.class,
          float.class,
          int.class,
          long.class,
          short.class);

  protected static final List<Class> primitiveWrapperClasses =
      Arrays.asList(
          Boolean.class,
          Byte.class,
          Character.class,
          Double.class,
          Float.class,
          Integer.class,
          Long.class,
          Short.class);

  /** Comprehensive list of all java.lang(8) classes */
  protected static final List<Class> nonWrapperJavaLangClasses =
      Arrays.asList(
          Character.Subset.class,
          Character.UnicodeBlock.class,
          Class.class,
          ClassLoader.class,
          ClassValue.class,
          Enum.class,
          InheritableThreadLocal.class,
          Math.class,
          Number.class,
          Object.class,
          Package.class,
          Process.class,
          ProcessBuilder.class,
          ProcessBuilder.Redirect.class,
          Runtime.class,
          RuntimePermission.class,
          StackTraceElement.class,
          StrictMath.class,
          StringBuffer.class,
          StringBuilder.class,
          System.class,
          Thread.class,
          ThreadGroup.class,
          ThreadLocal.class,
          Throwable.class,
          Void.class);

  /** List of objects that should be wrappable */
  protected static final List<Object> wrappableObjects =
      Arrays.asList(
          /** null and void * */
          null,
          Void.class,
          void.class,
          /** primitives * */
          false,
          Byte.parseByte("0"),
          'c',
          0.43d,
          512.5f,
          Integer.parseInt("4"),
          34L,
          Short.parseShort("10"),
          /** char sequences * */
          "hello",
          new StringBuilder("world"),
          new StringBuffer("!!"),
          /** primitive wrappers * */
          Boolean.TRUE,
          Byte.valueOf("1"),
          Character.valueOf('a'),
          Double.valueOf("382.03"),
          Float.valueOf("393.4"),
          Integer.valueOf("458"),
          Long.valueOf("348333"),
          Short.valueOf("25"),
          /** arrays of primitives * */
          new boolean[] {true, false},
          new byte[] {1, 2, 3},
          new char[] {'a', 'b', 'c'},
          new double[] {1.0d, 2.0d, 3.0d},
          new float[] {1.0f, 2.0f, 3.0f},
          new int[] {1, 2, 3},
          new long[] {1L, 2L, 3L},
          new short[] {1, 2, 3},
          /** arrays of wrappers * */
          new Boolean[] {true, false},
          new Byte[] {1, 2, 3},
          new Character[] {'a', 'b', 'c'},
          new Double[] {1.0d, 2.0d, 3.0d},
          new Float[] {1.0f, 2.0f, 3.0f},
          new Integer[] {1, 2, 3},
          new Long[] {1L, 2L, 3L},
          new Short[] {1, 2, 3},
          /** arrays of char sequences * */
          new String[] {"hey", "there"},
          new StringBuilder[] {new StringBuilder("hey"), new StringBuilder("there")},
          new StringBuffer[] {new StringBuffer("hey"), new StringBuffer("ya!")});

  /** List of some objects that should NOT be wrappable */
  protected static final List<Object> someNonWrappableObjects =
      Arrays.asList(
          Object.class,
          new Object(),
          new java.util.Date(),
          new ArrayList(),
          new java.util.HashSet(),
          new java.util.HashMap(),
          new java.util.Stack(),
          new java.util.Random(),
          new Object[1],
          new Class[1]);

  /**
   * Asserts that two arrays assigned to objects are equal. This method does delegates to the proper
   * assertArrayEquals method for the array type.
   *
   * @param objectArray1
   * @param objectArray2
   */
  protected void myAssertArrayEquals(Object objectArray1, Object objectArray2) {
    Class<?> arrayType = objectArray1.getClass().getComponentType();
    if (arrayType.isPrimitive()) {
      if (arrayType == boolean.class) {
        boolean[] array1 = (boolean[]) objectArray1;
        boolean[] array2 = (boolean[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == byte.class) {
        byte[] array1 = (byte[]) objectArray1;
        byte[] array2 = (byte[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == char.class) {
        char[] array1 = (char[]) objectArray1;
        char[] array2 = (char[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == double.class) {
        double[] array1 = (double[]) objectArray1;
        double[] array2 = (double[]) objectArray2;
        assertArrayEquals(array1, array2, 0.0);
      } else if (arrayType == float.class) {
        float[] array1 = (float[]) objectArray1;
        float[] array2 = (float[]) objectArray2;
        assertArrayEquals(array1, array2, 0.0f);
      } else if (arrayType == int.class) {
        int[] array1 = (int[]) objectArray1;
        int[] array2 = (int[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == long.class) {
        long[] array1 = (long[]) objectArray1;
        long[] array2 = (long[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == short.class) {
        short[] array1 = (short[]) objectArray1;
        short[] array2 = (short[]) objectArray2;
        assertArrayEquals(array1, array2);
      }
    } else if (Classes.isPrimitiveWrapper(arrayType)) {
      if (arrayType == Boolean.class) {
        Boolean[] array1 = (Boolean[]) objectArray1;
        Boolean[] array2 = (Boolean[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Byte.class) {
        Byte[] array1 = (Byte[]) objectArray1;
        Byte[] array2 = (Byte[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Character.class) {
        Character[] array1 = (Character[]) objectArray1;
        Character[] array2 = (Character[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Double.class) {
        Double[] array1 = (Double[]) objectArray1;
        Double[] array2 = (Double[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Float.class) {
        Float[] array1 = (Float[]) objectArray1;
        Float[] array2 = (Float[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Integer.class) {
        Integer[] array1 = (Integer[]) objectArray1;
        Integer[] array2 = (Integer[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Long.class) {
        Long[] array1 = (Long[]) objectArray1;
        Long[] array2 = (Long[]) objectArray2;
        assertArrayEquals(array1, array2);
      } else if (arrayType == Short.class) {
        Short[] array1 = (Short[]) objectArray1;
        Short[] array2 = (Short[]) objectArray2;
        assertArrayEquals(array1, array2);
      }
    } else if (Wrapper.isWrappableCharSeqClass(arrayType)) {
      CharSequence[] array1 = (CharSequence[]) objectArray1;
      CharSequence[] array2 = (CharSequence[]) objectArray2;
      assertEquals(array1.length, array2.length);
      for (int i = 0; i < array1.length; i++) {
        assertEquals(array1[i].toString(), array2[i].toString());
      }
    } else {
      Object[] array1 = (Object[]) objectArray1;
      Object[] array2 = (Object[]) objectArray2;
      assertArrayEquals(array1, array2);
    }
  }
}
