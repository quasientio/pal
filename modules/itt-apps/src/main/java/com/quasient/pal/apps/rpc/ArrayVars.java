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

package com.quasient.pal.apps.rpc;

// NOTE THAT INTEGRATION TESTS are dependent on this class
@SuppressWarnings({"unused", "FieldMayBeFinal"})
public class ArrayVars {

  private static boolean[] aNull_booleanArray;
  private static boolean[] anEmpty_booleanArray = {};
  private static boolean[] a_booleanArray = {false, false, true};
  private boolean[] instanceBooleanPrimitiveArray;

  private static byte[] aNull_byteArray;
  private static byte[] anEmpty_byteArray = {};
  private static byte[] a_byteArray = {0, 1, 2, 3, 4, 5, 6};
  private byte[] instanceBytePrimitiveArray;

  private static short[] aNull_shortArray;
  private static short[] anEmpty_shortArray = {};
  private static short[] a_shortArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};
  private short[] instanceShortPrimitiveArray;

  private static char[] aNull_charArray;
  private static char[] anEmpty_charArray = {};
  private static char[] a_charArray = {'a', 'r', 'r', 'a', 'y'};
  private char[] instanceCharPrimitiveArray;

  private static int[] aNull_intArray;
  private static int[] anEmpty_intArray = {};
  private static int[] a_intArray = {2333, -2, 0, 892, 9381};
  private int[] instanceIntPrimitiveArray;

  private static long[] aNull_longArray;
  private static long[] anEmpty_longArray = {};
  private static long[] a_longArray = {23230233L, -8929381L, 0L};
  private long[] instanceLongPrimitiveArray;

  private static float[] aNull_floatArray;
  private static float[] anEmpty_floatArray = {};
  private static float[] a_floatArray = {23.3f, 0f, -763.03f, 892.938f};
  private float[] instanceFloatPrimitiveArray;

  private static double[] aNull_doubleArray;
  private static double[] anEmpty_doubleArray = {};
  private static double[] a_doubleArray = {383239.3d, 0d, -239923.4038d};
  private double[] instanceDoublePrimitiveArray;

  // arrays - primitive wrappers
  private static Boolean[] aNullBooleanArray;
  private static Boolean[] anEmptyBooleanArray = {};
  private static Boolean[] aBooleanArray = {false, false, true};
  private Boolean[] instanceBooleanWrapperArray;

  private static Byte[] aNullByteArray;
  private static Byte[] anEmptyByteArray = {};
  private static Byte[] aByteArray = {0, 1, 2, 3, 4, 5, 6};
  private Byte[] instanceByteWrapperArray;

  private static Short[] aNullShortArray;
  private static Short[] anEmptyShortArray = {};
  private static Short[] aShortArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};
  private Short[] instanceShortWrapperArray;

  private static Character[] aNullCharacterArray;
  private static Character[] anEmptyCharacterArray = {};
  private static Character[] aCharacterArray = {'a', 'r', 'r', 'a', 'y'};
  private Character[] instanceCharacterWrapperArray;

  private static Integer[] aNullIntegerArray;
  private static Integer[] anEmptyIntegerArray = {};
  private static Integer[] aIntegerArray = {2333, -2, 0, 892, 9381};
  private Integer[] instanceIntegerWrapperArray;

  private static Long[] aNullLongArray;
  private static Long[] anEmptyLongArray = {};
  private static Long[] aLongArray = {23230233L, -8929381L, 0L};
  private Long[] instanceLongWrapperArray;

  private static Float[] aNullFloatArray;
  private static Float[] anEmptyFloatArray = {};
  private static Float[] aFloatArray = {23.3f, 0f, -763.03f, 892.938f};
  private Float[] instanceFloatWrapperArray;

  private static Double[] aNullDoubleArray;
  private static Double[] anEmptyDoubleArray = {};
  private static Double[] aDoubleArray = {383239.3d, 0d, -239923.4038d};
  private Double[] instanceDoubleWrapperArray;

  private static String[] aNullStringArray;
  private static String[] anEmptyStringArray = {};
  private static String[] aStringArray = {"hello", "world", "!"};
  private String[] instanceStringArray;

  public boolean[] getInstanceBooleanPrimitiveArray() {
    return instanceBooleanPrimitiveArray;
  }

  public void setInstanceBooleanPrimitiveArray(boolean[] instanceBooleanPrimitiveArray) {
    this.instanceBooleanPrimitiveArray = instanceBooleanPrimitiveArray;
  }

  public Boolean[] getInstanceBooleanWrapperArray() {
    return instanceBooleanWrapperArray;
  }

  public void setInstanceBooleanWrapperArray(Boolean[] instanceBooleanWrapperArray) {
    this.instanceBooleanWrapperArray = instanceBooleanWrapperArray;
  }

  public byte[] getInstanceBytePrimitiveArray() {
    return instanceBytePrimitiveArray;
  }

  public void setInstanceBytePrimitiveArray(byte[] instanceBytePrimitiveArray) {
    this.instanceBytePrimitiveArray = instanceBytePrimitiveArray;
  }

  public Byte[] getInstanceByteWrapperArray() {
    return instanceByteWrapperArray;
  }

  public void setInstanceByteWrapperArray(Byte[] instanceByteWrapperArray) {
    this.instanceByteWrapperArray = instanceByteWrapperArray;
  }

  public Character[] getInstanceCharacterWrapperArray() {
    return instanceCharacterWrapperArray;
  }

  public void setInstanceCharacterWrapperArray(Character[] instanceCharacterWrapperArray) {
    this.instanceCharacterWrapperArray = instanceCharacterWrapperArray;
  }

  public char[] getInstanceCharPrimitiveArray() {
    return instanceCharPrimitiveArray;
  }

  public void setInstanceCharPrimitiveArray(char[] instanceCharPrimitiveArray) {
    this.instanceCharPrimitiveArray = instanceCharPrimitiveArray;
  }

  public double[] getInstanceDoublePrimitiveArray() {
    return instanceDoublePrimitiveArray;
  }

  public void setInstanceDoublePrimitiveArray(double[] instanceDoublePrimitiveArray) {
    this.instanceDoublePrimitiveArray = instanceDoublePrimitiveArray;
  }

  public Double[] getInstanceDoubleWrapperArray() {
    return instanceDoubleWrapperArray;
  }

  public void setInstanceDoubleWrapperArray(Double[] instanceDoubleWrapperArray) {
    this.instanceDoubleWrapperArray = instanceDoubleWrapperArray;
  }

  public float[] getInstanceFloatPrimitiveArray() {
    return instanceFloatPrimitiveArray;
  }

  public void setInstanceFloatPrimitiveArray(float[] instanceFloatPrimitiveArray) {
    this.instanceFloatPrimitiveArray = instanceFloatPrimitiveArray;
  }

  public Float[] getInstanceFloatWrapperArray() {
    return instanceFloatWrapperArray;
  }

  public void setInstanceFloatWrapperArray(Float[] instanceFloatWrapperArray) {
    this.instanceFloatWrapperArray = instanceFloatWrapperArray;
  }

  public Integer[] getInstanceIntegerWrapperArray() {
    return instanceIntegerWrapperArray;
  }

  public void setInstanceIntegerWrapperArray(Integer[] instanceIntegerWrapperArray) {
    this.instanceIntegerWrapperArray = instanceIntegerWrapperArray;
  }

  public int[] getInstanceIntPrimitiveArray() {
    return instanceIntPrimitiveArray;
  }

  public void setInstanceIntPrimitiveArray(int[] instanceIntPrimitiveArray) {
    this.instanceIntPrimitiveArray = instanceIntPrimitiveArray;
  }

  public long[] getInstanceLongPrimitiveArray() {
    return instanceLongPrimitiveArray;
  }

  public void setInstanceLongPrimitiveArray(long[] instanceLongPrimitiveArray) {
    this.instanceLongPrimitiveArray = instanceLongPrimitiveArray;
  }

  public Long[] getInstanceLongWrapperArray() {
    return instanceLongWrapperArray;
  }

  public void setInstanceLongWrapperArray(Long[] instanceLongWrapperArray) {
    this.instanceLongWrapperArray = instanceLongWrapperArray;
  }

  public short[] getInstanceShortPrimitiveArray() {
    return instanceShortPrimitiveArray;
  }

  public void setInstanceShortPrimitiveArray(short[] instanceShortPrimitiveArray) {
    this.instanceShortPrimitiveArray = instanceShortPrimitiveArray;
  }

  public Short[] getInstanceShortWrapperArray() {
    return instanceShortWrapperArray;
  }

  public void setInstanceShortWrapperArray(Short[] instanceShortWrapperArray) {
    this.instanceShortWrapperArray = instanceShortWrapperArray;
  }

  public String[] getInstanceStringArray() {
    return instanceStringArray;
  }

  public void setInstanceStringArray(String[] instanceStringArray) {
    this.instanceStringArray = instanceStringArray;
  }
}
