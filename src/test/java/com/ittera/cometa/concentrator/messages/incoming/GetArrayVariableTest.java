package com.ittera.cometa.concentrator.messages.incoming;

import com.ittera.cometa.concentrator.AbstractConcentratorTest;
import com.ittera.cometa.concentrator.messages.data.DataMessageFactory;
import com.ittera.cometa.concentrator.messages.data.ProtobufUtils;
import com.ittera.cometa.concentrator.messages.data.Values;
import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

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

public class GetArrayVariableTest extends AbstractConcentratorTest {

  protected final String className = "com.ittera.cometa.demos.ArrayVars";

  @Test
  public void getStaticPrivate_booleanArrayNull() throws ClassNotFoundException {

    String fieldName = "aNull_booleanArray";
    String fieldClassName = "[Z";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_booleanArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_booleanArray";
    String fieldClassName = "[Z";
    boolean[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(actualArray.length, ((boolean[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_booleanArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_booleanArray";
    String fieldClassName = "[Z";
    boolean[] actualArray = {false, false, true};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof boolean[]);
    assertEquals(actualArray.length, ((boolean[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((boolean[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_byteArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_byteArray";
    String fieldClassName = "[B";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_byteArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_byteArray";
    String fieldClassName = "[B";
    byte[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(actualArray.length, ((byte[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_byteArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_byteArray";
    String fieldClassName = "[B";
    byte[] actualArray = {0, 1, 2, 3, 4, 5, 6};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof byte[]);
    assertEquals(actualArray.length, ((byte[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((byte[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_shortArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_shortArray";
    String fieldClassName = "[S";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_shortArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_shortArray";
    String fieldClassName = "[S";
    short[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(actualArray.length, ((short[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_shortArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_shortArray";
    String fieldClassName = "[S";
    short[] actualArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof short[]);
    assertEquals(actualArray.length, ((short[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((short[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_charArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_charArray";
    String fieldClassName = "[C";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_charArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_charArray";
    String fieldClassName = "[C";
    char[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(actualArray.length, ((char[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_charArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_charArray";
    String fieldClassName = "[C";
    char[] actualArray = {'a', 'r', 'r', 'a', 'y'};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof char[]);
    assertEquals(actualArray.length, ((char[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((char[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_intArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_intArray";
    String fieldClassName = "[I";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_intArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_intArray";
    String fieldClassName = "[I";
    int[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(actualArray.length, ((int[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_intArrayNotNull() throws ClassNotFoundException {
    String fieldName = "an_intArray";
    String fieldClassName = "[I";
    int[] actualArray = {2333, -2, 0, 892, 9381};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof int[]);
    assertEquals(actualArray.length, ((int[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((int[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_longArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_longArray";
    String fieldClassName = "[J";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_longArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_longArray";
    String fieldClassName = "[J";
    long[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(actualArray.length, ((long[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_longArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_longArray";
    String fieldClassName = "[J";
    long[] actualArray = {23230233L, -8929381L, 0L};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof long[]);
    assertEquals(actualArray.length, ((long[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((long[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivate_floatArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_floatArray";
    String fieldClassName = "[F";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_floatArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_floatArray";
    String fieldClassName = "[F";
    float[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(actualArray.length, ((float[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_floatArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_floatArray";
    String fieldClassName = "[F";
    float[] actualArray = {23.3f, 0f, -763.03f, 892.938f};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof float[]);
    assertEquals(actualArray.length, ((float[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((float[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivate_doubleArrayNull() throws ClassNotFoundException {
    String fieldName = "aNull_doubleArray";
    String fieldClassName = "[D";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivate_doubleArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmpty_doubleArray";
    String fieldClassName = "[D";
    double[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(actualArray.length, ((double[]) rawObj).length);
  }

  @Test
  public void getStaticPrivate_doubleArrayNotNull() throws ClassNotFoundException {
    String fieldName = "a_doubleArray";
    String fieldClassName = "[D";
    double[] actualArray = {383239.3d, 0d, -239923.4038d};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof double[]);
    assertEquals(actualArray.length, ((double[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((double[]) rawObj)[i], 0);
    }
  }

  //WRAPPERS
  @Test
  public void getStaticPrivateBooleanArrayNull() throws ClassNotFoundException {

    String fieldName = "aNullBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateBooleanArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";
    Boolean[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean[]);
    assertEquals(actualArray.length, ((Boolean[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateBooleanArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aBooleanArray";
    String fieldClassName = "[Ljava.lang.Boolean;";
    Boolean[] actualArray = {false, false, true};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Boolean[]);
    assertEquals(actualArray.length, ((Boolean[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Boolean[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateByteArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateByteArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";
    byte[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Byte[]);
    assertEquals(actualArray.length, ((Byte[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateByteArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aByteArray";
    String fieldClassName = "[Ljava.lang.Byte;";
    Byte[] actualArray = {0, 1, 2, 3, 4, 5, 6};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Byte[]);
    assertEquals(actualArray.length, ((Byte[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Byte[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateShortArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullShortArray";
    String fieldClassName = "[Ljava.lang.Short;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateShortArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyShortArray";
    String fieldClassName = "[Ljava.lang.Short;";
    Short[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short[]);
    assertEquals(actualArray.length, ((Short[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateShortArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aShortArray";
    String fieldClassName = "[Ljava.lang.Short;";
    Short[] actualArray = {-200, -100, 0, 100, 200, 300, 400, 500, 600};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Short[]);
    assertEquals(actualArray.length, ((Short[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Short[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateCharacterArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullCharArray";
    String fieldClassName = "[Ljava.lang.Character;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateCharacterArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyCharArray";
    String fieldClassName = "[Ljava.lang.Character;";
    Character[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Character[]);
    assertEquals(actualArray.length, ((Character[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateCharacterArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aCharArray";
    String fieldClassName = "[Ljava.lang.Character;";
    Character[] actualArray = {'a', 'r', 'r', 'a', 'y'};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Character[]);
    assertEquals(actualArray.length, ((Character[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Character[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateIntegerArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateIntegerArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";
    Integer[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer[]);
    assertEquals(actualArray.length, ((Integer[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateIntegerArrayNotNull() throws ClassNotFoundException {
    String fieldName = "anIntArray";
    String fieldClassName = "[Ljava.lang.Integer;";
    Integer[] actualArray = {2333, -2, 0, 892, 9381};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Integer[]);
    assertEquals(actualArray.length, ((Integer[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Integer[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateLongArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullLongArray";
    String fieldClassName = "[Ljava.lang.Long;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateLongArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyLongArray";
    String fieldClassName = "[Ljava.lang.Long;";
    Long[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Long[]);
    assertEquals(actualArray.length, ((Long[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateLongArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aLongArray";
    String fieldClassName = "[Ljava.lang.Long;";
    Long[] actualArray = {23230233L, -8929381L, 0L};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Long[]);
    assertEquals(actualArray.length, ((Long[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Long[]) rawObj)[i]);
    }
  }

  @Test
  public void getStaticPrivateFloatArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateFloatArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";
    Float[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Float[]);
    assertEquals(actualArray.length, ((Float[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateFloatArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aFloatArray";
    String fieldClassName = "[Ljava.lang.Float;";
    Float[] actualArray = {23.3f, 0f, -763.03f, 892.938f};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Float[]);
    assertEquals(actualArray.length, ((Float[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Float[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivateDoubleArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateDoubleArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";
    Double[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Double[]);
    assertEquals(actualArray.length, ((Double[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateDoubleArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aDoubleArray";
    String fieldClassName = "[Ljava.lang.Double;";
    Double[] actualArray = {383239.3d, 0d, -239923.4038d};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof Double[]);
    assertEquals(actualArray.length, ((Double[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((Double[]) rawObj)[i], 0);
    }
  }

  @Test
  public void getStaticPrivateStringArrayNull() throws ClassNotFoundException {
    String fieldName = "aNullStringArray";
    String fieldClassName = "[Ljava.lang.String;";

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsNullArrayOfRightType(retValue, fieldClassName);
  }

  @Test
  public void getStaticPrivateStringArrayEmpty() throws ClassNotFoundException {
    String fieldName = "anEmptyStringArray";
    String fieldClassName = "[Ljava.lang.String;";
    String[] actualArray = {};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String[]);
    assertEquals(actualArray.length, ((String[]) rawObj).length);
  }

  @Test
  public void getStaticPrivateStringArrayNotNull() throws ClassNotFoundException {
    String fieldName = "aStringArray";
    String fieldClassName = "[Ljava.lang.String;";
    String[] actualArray = {"hello", "world", "!"};

    DataMessage requestMsg = DataMessageFactory.buildGetStaticMessage(clientId, className, fieldName);
    DataMessage replyMsg = sendAndReceive(requestMsg);
    assertTrue(replyMsg.hasReturnValue());
    Values.ReturnValue retValue = replyMsg.getReturnValue();
    assertValueIsArrayOfRightType(retValue, fieldClassName);

    Object rawObj = ProtobufUtils.unwrapObject(retValue.getObject());
    assertTrue(rawObj instanceof String[]);
    assertEquals(actualArray.length, ((String[]) rawObj).length);
    for (int i = 0; i < actualArray.length; i++) {
      assertEquals(actualArray[i], ((String[]) rawObj)[i]);
    }
  }
}
