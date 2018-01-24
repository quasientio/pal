package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Coverage:
 * ---------
 * Arrays of Primitives. For each type, there are 3 tests:
 * - a null (non-initialized) array
 * - an empty array
 * - an array initialized with some values of the right type
 * <p>
 * TODO: introduce null values in 3rd test
 */

public class GetArrayVariableMessageIT extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.apps.ArrayVars";

  @Test
  public void getStaticPrivate_booleanArrayNull() throws Exception {

    String fieldName = "aNull_booleanArray";
    String fieldClassName = "[Z";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_booleanArrayEmpty() throws Exception {
    String fieldName = "anEmpty_booleanArray";
    String fieldClassName = "[Z";
    boolean[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(actualArray.length, ((boolean[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_booleanArrayNotNull() throws Exception {
    String fieldName = "a_booleanArray";
    String fieldClassName = "[Z";
    boolean[] actualArray = {false, false, true};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(actualArray.length, ((boolean[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((boolean[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_byteArrayNull() throws Exception {
    String fieldName = "aNull_byteArray";
    String fieldClassName = "[B";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_byteArrayEmpty() throws Exception {
    String fieldName = "anEmpty_byteArray";
    String fieldClassName = "[B";
    byte[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(actualArray.length, ((byte[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_byteArrayNotNull() throws Exception {
    String fieldName = "a_byteArray";
    String fieldClassName = "[B";
    byte[] actualArray = {0, 1, 2, 3, 4, 5, 6};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(actualArray.length, ((byte[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((byte[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_shortArrayNull() throws Exception {
    String fieldName = "aNull_shortArray";
    String fieldClassName = "[S";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_shortArrayEmpty() throws Exception {
    String fieldName = "anEmpty_shortArray";
    String fieldClassName = "[S";
    short[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(actualArray.length, ((short[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_shortArrayNotNull() throws Exception {
    String fieldName = "a_shortArray";
    String fieldClassName = "[S";
    short[] actualArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(actualArray.length, ((short[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((short[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_charArrayNull() throws Exception {
    String fieldName = "aNull_charArray";
    String fieldClassName = "[C";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_charArrayEmpty() throws Exception {
    String fieldName = "anEmpty_charArray";
    String fieldClassName = "[C";
    char[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(actualArray.length, ((char[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_charArrayNotNull() throws Exception {
    String fieldName = "a_charArray";
    String fieldClassName = "[C";
    char[] actualArray = {'a', 'r', 'r', 'a', 'y'};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(actualArray.length, ((char[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((char[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_intArrayNull() throws Exception {
    String fieldName = "aNull_intArray";
    String fieldClassName = "[I";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_intArrayEmpty() throws Exception {
    String fieldName = "anEmpty_intArray";
    String fieldClassName = "[I";
    int[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(actualArray.length, ((int[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_intArrayNotNull() throws Exception {
    String fieldName = "an_intArray";
    String fieldClassName = "[I";
    int[] actualArray = {2333, -2, 0, 892, 9381};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(actualArray.length, ((int[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((int[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_longArrayNull() throws Exception {
    String fieldName = "aNull_longArray";
    String fieldClassName = "[J";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_longArrayEmpty() throws Exception {
    String fieldName = "anEmpty_longArray";
    String fieldClassName = "[J";
    long[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(actualArray.length, ((long[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_longArrayNotNull() throws Exception {
    String fieldName = "a_longArray";
    String fieldClassName = "[J";
    long[] actualArray = {23230233L, -8929381L, 0L};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(actualArray.length, ((long[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((long[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_floatArrayNull() throws Exception {
    String fieldName = "aNull_floatArray";
    String fieldClassName = "[F";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_floatArrayEmpty() throws Exception {
    String fieldName = "anEmpty_floatArray";
    String fieldClassName = "[F";
    float[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(actualArray.length, ((float[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_floatArrayNotNull() throws Exception {
    String fieldName = "a_floatArray";
    String fieldClassName = "[F";
    float[] actualArray = {23.3f, 0f, -763.03f, 892.938f};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(actualArray.length, ((float[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((float[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivate_doubleArrayNull() throws Exception {
    String fieldName = "aNull_doubleArray";
    String fieldClassName = "[D";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_doubleArrayEmpty() throws Exception {
    String fieldName = "anEmpty_doubleArray";
    String fieldClassName = "[D";
    double[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(actualArray.length, ((double[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_doubleArrayNotNull() throws Exception {
    String fieldName = "a_doubleArray";
    String fieldClassName = "[D";
    double[] actualArray = {383239.3d, 0d, -239923.4038d};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(actualArray.length, ((double[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((double[]) rawObj)[i], 0);
    }
  }

  //WRAPPERS
  @Test
  public void getStaticPrivateBooleanArrayNull() throws Exception {

    String fieldName = "aNullBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateBooleanArrayEmpty() throws Exception {
    String fieldName = "anEmptyBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";
    Boolean[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean[]);
    assertEquals(actualArray.length, ((Boolean[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateBooleanArrayNotNull() throws Exception {
    String fieldName = "aBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";
    Boolean[] actualArray = {false, false, true};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean[]);
    assertEquals(actualArray.length, ((Boolean[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Boolean[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateByteArrayNull() throws Exception {
    String fieldName = "aNullByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateByteArrayEmpty() throws Exception {
    String fieldName = "anEmptyByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";
    byte[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Byte[]);
    assertEquals(actualArray.length, ((Byte[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateByteArrayNotNull() throws Exception {
    String fieldName = "aByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";
    Byte[] actualArray = {0, 1, 2, 3, 4, 5, 6};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Byte[]);
    assertEquals(actualArray.length, ((Byte[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Byte[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateShortArrayNull() throws Exception {
    String fieldName = "aNullShortArray";
    String fieldClassName = "[Ljava.lang.Short;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateShortArrayEmpty() throws Exception {
    String fieldName = "anEmptyShortArray";
    String fieldClassName = "[Ljava.lang.Short;";
    Short[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short[]);
    assertEquals(actualArray.length, ((Short[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateShortArrayNotNull() throws Exception {
    String fieldName = "aShortArray";
    String fieldClassName = "[Ljava.lang.Short;";
    Short[] actualArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short[]);
    assertEquals(actualArray.length, ((Short[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Short[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateCharacterArrayNull() throws Exception {
    String fieldName = "aNullCharArray";
    String fieldClassName = "[Ljava.lang.Character;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateCharacterArrayEmpty() throws Exception {
    String fieldName = "anEmptyCharArray";
    String fieldClassName = "[Ljava.lang.Character;";
    Character[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Character[]);
    assertEquals(actualArray.length, ((Character[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateCharacterArrayNotNull() throws Exception {
    String fieldName = "aCharArray";
    String fieldClassName = "[Ljava.lang.Character;";
    Character[] actualArray = {'a', 'r', 'r', 'a', 'y'};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Character[]);
    assertEquals(actualArray.length, ((Character[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Character[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateIntegerArrayNull() throws Exception {
    String fieldName = "aNullIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateIntegerArrayEmpty() throws Exception {
    String fieldName = "anEmptyIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";
    Integer[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer[]);
    assertEquals(actualArray.length, ((Integer[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateIntegerArrayNotNull() throws Exception {
    String fieldName = "anIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";
    Integer[] actualArray = {2333, -2, 0, 892, 9381};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer[]);
    assertEquals(actualArray.length, ((Integer[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Integer[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateLongArrayNull() throws Exception {
    String fieldName = "aNullLongArray";
    String fieldClassName = "[Ljava.lang.Long;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateLongArrayEmpty() throws Exception {
    String fieldName = "anEmptyLongArray";
    String fieldClassName = "[Ljava.lang.Long;";
    Long[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Long[]);
    assertEquals(actualArray.length, ((Long[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateLongArrayNotNull() throws Exception {
    String fieldName = "aLongArray";
    String fieldClassName = "[Ljava.lang.Long;";
    Long[] actualArray = {23230233L, -8929381L, 0L};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Long[]);
    assertEquals(actualArray.length, ((Long[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Long[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateFloatArrayNull() throws Exception {
    String fieldName = "aNullFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateFloatArrayEmpty() throws Exception {
    String fieldName = "anEmptyFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";
    Float[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Float[]);
    assertEquals(actualArray.length, ((Float[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateFloatArrayNotNull() throws Exception {
    String fieldName = "aFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";
    Float[] actualArray = {23.3f, 0f, -763.03f, 892.938f};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Float[]);
    assertEquals(actualArray.length, ((Float[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Float[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivateDoubleArrayNull() throws Exception {
    String fieldName = "aNullDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateDoubleArrayEmpty() throws Exception {
    String fieldName = "anEmptyDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";
    Double[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Double[]);
    assertEquals(actualArray.length, ((Double[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateDoubleArrayNotNull() throws Exception {
    String fieldName = "aDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";
    Double[] actualArray = {383239.3d, 0d, -239923.4038d};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Double[]);
    assertEquals(actualArray.length, ((Double[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Double[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivateStringArrayNull() throws Exception {
    String fieldName = "aNullStringArray";
    String fieldClassName = "[Ljava.lang.String;";

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateStringArrayEmpty() throws Exception {
    String fieldName = "anEmptyStringArray";
    String fieldClassName = "[Ljava.lang.String;";
    String[] actualArray = {};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String[]);
    assertEquals(actualArray.length, ((String[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateStringArrayNotNull() throws Exception {
    String fieldName = "aStringArray";
    String fieldClassName = "[Ljava.lang.String;";
    String[] actualArray = {"hello", "world", "!"};

    DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String[]);
    assertEquals(actualArray.length, ((String[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((String[]) rawObj)[i]);
    }
  }
}
