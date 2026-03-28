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
package io.quasient.foobar.apps.rpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// NOTE THAT INTEGRATION TESTS are dependent on this class
@SuppressWarnings({"unused", "FieldMayBeFinal"})
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD", "UUF_UNUSED_FIELD", "MS_SHOULD_BE_FINAL"},
    justification =
        "Test fixture - fields intentionally unused and mutable, accessed via RPC for testing")
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
