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

package net.ittera.pal.rpc.binary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.serdes.Unwrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior.
 *
 * <pre>
 *   Coverage: Arrays of Primitives & Wrappers.
 *   For each type, there are 3 tests:
 *   - a null array
 *   - an empty array
 *   - an array populated with some values
 * </pre>
 *
 * <p>TODO: introduce null values
 */
@RunWith(Parameterized.class)
public class GetArrayMessageIT extends AbstractBinaryRpcMessageIT {

  protected final String className = "net.ittera.pal.apps.rpc.StaticArrayVars";

  public GetArrayMessageIT(TargetType targetType) {
    super(targetType);
  }

  @Parameterized.Parameters(name = "{index}: channel={0}")
  public static Collection<Object[]> data() {
    return getSendTargetParameters();
  }

  // <editor-fold defaultstate="collapsed" desc="primitive array tests">
  @Test
  public void getStatic_booleanArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_booleanArray");
    assertValueIsNullArrayOfType(retValue, "[Z");
  }

  @Test
  public void getStatic_booleanArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_booleanArray");
    assertValueIsArrayOfType(retValue, "[Z");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(0, ((boolean[]) rawObj).length);
  }

  @Test
  public void getStatic_booleanArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_booleanArray");
    assertValueIsArrayOfType(retValue, "[Z");

    boolean[] actualArray = {false, false, true};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(actualArray.length, ((boolean[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((boolean[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_byteArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_byteArray");
    assertValueIsNullArrayOfType(retValue, "[B");
  }

  @Test
  public void getStatic_byteArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_byteArray");
    assertValueIsArrayOfType(retValue, "[B");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(0, ((byte[]) rawObj).length);
  }

  @Test
  public void getStatic_byteArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_byteArray");
    assertValueIsArrayOfType(retValue, "[B");

    byte[] actualArray = {0, 1, 2, 3, 4, 5, 6};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(actualArray.length, ((byte[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((byte[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_shortArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_shortArray");
    assertValueIsNullArrayOfType(retValue, "[S");
  }

  @Test
  public void getStatic_shortArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_shortArray");
    assertValueIsArrayOfType(retValue, "[S");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(0, ((short[]) rawObj).length);
  }

  @Test
  public void getStatic_shortArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_shortArray");
    assertValueIsArrayOfType(retValue, "[S");

    short[] actualArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(actualArray.length, ((short[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((short[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_charArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_charArray");
    assertValueIsNullArrayOfType(retValue, "[C");
  }

  @Test
  public void getStatic_charArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_charArray");
    assertValueIsArrayOfType(retValue, "[C");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(0, ((char[]) rawObj).length);
  }

  @Test
  public void getStatic_charArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_charArray");
    assertValueIsArrayOfType(retValue, "[C");

    char[] actualArray = {'a', 'r', 'r', 'a', 'y'};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(actualArray.length, ((char[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((char[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_intArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_intArray");
    assertValueIsNullArrayOfType(retValue, "[I");
  }

  @Test
  public void getStatic_intArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_intArray");
    assertValueIsArrayOfType(retValue, "[I");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(0, ((int[]) rawObj).length);
  }

  @Test
  public void getStatic_intArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_intArray");
    assertValueIsArrayOfType(retValue, "[I");

    int[] actualArray = {2333, -2, 0, 892, 9381};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(actualArray.length, ((int[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((int[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_longArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_longArray");
    assertValueIsNullArrayOfType(retValue, "[J");
  }

  @Test
  public void getStatic_longArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_longArray");
    assertValueIsArrayOfType(retValue, "[J");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(0, ((long[]) rawObj).length);
  }

  @Test
  public void getStatic_longArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_longArray");
    assertValueIsArrayOfType(retValue, "[J");

    long[] actualArray = {23230233L, -8929381L, 0L};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(actualArray.length, ((long[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((long[]) rawObj)[i]);
    }
  }

  @Test
  public void getStatic_floatArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_floatArray");
    assertValueIsNullArrayOfType(retValue, "[F");
  }

  @Test
  public void getStatic_floatArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_floatArray");
    assertValueIsArrayOfType(retValue, "[F");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(0, ((float[]) rawObj).length);
  }

  @Test
  public void getStatic_floatArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_floatArray");
    assertValueIsArrayOfType(retValue, "[F");

    float[] actualArray = {23.3f, 0f, -763.03f, 892.938f};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(actualArray.length, ((float[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((float[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStatic_doubleArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNull_doubleArray");
    assertValueIsNullArrayOfType(retValue, "[D");
  }

  @Test
  public void getStatic_doubleArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmpty_doubleArray");
    assertValueIsArrayOfType(retValue, "[D");

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(0, ((double[]) rawObj).length);
  }

  @Test
  public void getStatic_doubleArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "a_doubleArray");
    assertValueIsArrayOfType(retValue, "[D");

    double[] actualArray = {383239.3d, 0d, -239923.4038d};
    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(actualArray.length, ((double[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((double[]) rawObj)[i], 0);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="primitive wrapper array tests">
  @Test
  public void getStatic_BooleanArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullBooleanArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Boolean;");
  }

  @Test
  public void getStatic_BooleanArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyBooleanArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Boolean;");
    assertValueEqualsArray(new Boolean[0], retValue);
  }

  @Test
  public void getStatic_BooleanArrayNotNull() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aBooleanArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Boolean;");
    assertValueEqualsArray(new Boolean[] {false, false, true}, retValue);
  }

  @Test
  public void getStatic_ByteArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullByteArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Byte;");
  }

  @Test
  public void getStatic_ByteArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyByteArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Byte;");
    assertValueEqualsArray(new Byte[0], retValue);
  }

  @Test
  public void getStatic_ByteArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aByteArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Byte;");
    assertValueEqualsArray(new Byte[] {0, 1, 2, 3, 4, 5, 6}, retValue);
  }

  @Test
  public void getStatic_ShortArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullShortArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Short;");
  }

  @Test
  public void getStatic_ShortArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyShortArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Short;");
    assertValueEqualsArray(new Short[0], retValue);
  }

  @Test
  public void getStatic_ShortArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aShortArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Short;");
    assertValueEqualsArray(new Short[] {-200, -100, 0, 100, 200, 300, 400, 500, 600}, retValue);
  }

  @Test
  public void getStatic_CharacterArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullCharacterArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Character;");
  }

  @Test
  public void getStatic_CharacterArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyCharacterArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Character;");
    assertValueEqualsArray(new Character[0], retValue);
  }

  @Test
  public void getStatic_CharacterArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aCharacterArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Character;");
    assertValueEqualsArray(new Character[] {'a', 'r', 'r', 'a', 'y'}, retValue);
  }

  @Test
  public void getStatic_IntegerArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullIntegerArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Integer;");
  }

  @Test
  public void getStatic_IntegerArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyIntegerArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Integer;");
    assertValueEqualsArray(new Integer[0], retValue);
  }

  @Test
  public void getStatic_IntegerArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aIntegerArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Integer;");
    assertValueEqualsArray(new Integer[] {2333, -2, 0, 892, 9381}, retValue);
  }

  @Test
  public void getStatic_LongArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullLongArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Long;");
  }

  @Test
  public void getStatic_LongArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyLongArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Long;");
    assertValueEqualsArray(new Long[0], retValue);
  }

  @Test
  public void getStatic_LongArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aLongArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Long;");
    assertValueEqualsArray(new Long[] {23230233L, -8929381L, 0L}, retValue);
  }

  @Test
  public void getStatic_FloatArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullFloatArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Float;");
  }

  @Test
  public void getStatic_FloatArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyFloatArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Float;");
    assertValueEqualsArray(new Float[0], retValue);
  }

  @Test
  public void getStatic_FloatArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aFloatArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Float;");
    assertValueEqualsArray(new Float[] {23.3f, 0f, -763.03f, 892.938f}, retValue);
  }

  @Test
  public void getStatic_DoubleArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullDoubleArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Double;");
  }

  @Test
  public void getStatic_DoubleArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyDoubleArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Double;");
    assertValueEqualsArray(new Double[0], retValue);
  }

  @Test
  public void getStatic_DoubleArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aDoubleArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.Double;");
    assertValueEqualsArray(new Double[] {383239.3d, 0d, -239923.4038d}, retValue);
  }

  @Test
  public void getStatic_StringArrayNull_nullArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aNullStringArray");
    assertValueIsNullArrayOfType(retValue, "[Ljava.lang.String;");
  }

  @Test
  public void getStatic_StringArrayEmpty_emptyArray() throws Exception {

    ReturnValue retValue = callGetStatic(className, "anEmptyStringArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.String;");
    assertValueEqualsArray(new String[0], retValue);
  }

  @Test
  public void getStatic_StringArrayNotNull_array() throws Exception {

    ReturnValue retValue = callGetStatic(className, "aStringArray");
    assertValueIsArrayOfType(retValue, "[Ljava.lang.String;");
    assertValueEqualsArray(new String[] {"hello", "world", "!"}, retValue);
  }
  // </editor-fold>
}
