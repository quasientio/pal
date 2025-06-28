/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.rpc;

// NOTE THAT INTEGRATION TESTS are dependent on this class
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class StaticArrayVars {

  private static boolean[] aNull_booleanArray;
  private static boolean[] anEmpty_booleanArray = {};
  private static boolean[] a_booleanArray = {false, false, true};

  private static byte[] aNull_byteArray;
  private static byte[] anEmpty_byteArray = {};
  private static byte[] a_byteArray = {0, 1, 2, 3, 4, 5, 6};

  private static short[] aNull_shortArray;
  private static short[] anEmpty_shortArray = {};
  private static short[] a_shortArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

  private static char[] aNull_charArray;
  private static char[] anEmpty_charArray = {};
  private static char[] a_charArray = {'a', 'r', 'r', 'a', 'y'};

  private static int[] aNull_intArray;
  private static int[] anEmpty_intArray = {};
  private static int[] a_intArray = {2333, -2, 0, 892, 9381};

  private static long[] aNull_longArray;
  private static long[] anEmpty_longArray = {};
  private static long[] a_longArray = {23230233L, -8929381L, 0L};

  private static float[] aNull_floatArray;
  private static float[] anEmpty_floatArray = {};
  private static float[] a_floatArray = {23.3f, 0f, -763.03f, 892.938f};

  private static double[] aNull_doubleArray;
  private static double[] anEmpty_doubleArray = {};
  private static double[] a_doubleArray = {383239.3d, 0d, -239923.4038d};

  // arrays - primitive wrappers
  private static Boolean[] aNullBooleanArray;
  private static Boolean[] anEmptyBooleanArray = {};
  private static Boolean[] aBooleanArray = {false, false, true};

  private static Byte[] aNullByteArray;
  private static Byte[] anEmptyByteArray = {};
  private static Byte[] aByteArray = {0, 1, 2, 3, 4, 5, 6};

  private static Short[] aNullShortArray;
  private static Short[] anEmptyShortArray = {};
  private static Short[] aShortArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

  private static Character[] aNullCharacterArray;
  private static Character[] anEmptyCharacterArray = {};
  private static Character[] aCharacterArray = {'a', 'r', 'r', 'a', 'y'};

  private static Integer[] aNullIntegerArray;
  private static Integer[] anEmptyIntegerArray = {};
  private static Integer[] aIntegerArray = {2333, -2, 0, 892, 9381};

  private static Long[] aNullLongArray;
  private static Long[] anEmptyLongArray = {};
  private static Long[] aLongArray = {23230233L, -8929381L, 0L};

  private static Float[] aNullFloatArray;
  private static Float[] anEmptyFloatArray = {};
  private static Float[] aFloatArray = {23.3f, 0f, -763.03f, 892.938f};

  private static Double[] aNullDoubleArray;
  private static Double[] anEmptyDoubleArray = {};
  private static Double[] aDoubleArray = {383239.3d, 0d, -239923.4038d};

  private static String[] aNullStringArray;
  private static String[] anEmptyStringArray = {};
  private static String[] aStringArray = {"hello", "world", "!"};
}
