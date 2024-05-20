package net.ittera.pal.messages.jsonrpc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ittera.pal.serdes.jsonrpc.JsonRpcParameterDeserializer;
import org.junit.Before;
import org.junit.Test;

public class JsonRpcParameterTest {

  private Gson gson;

  @Before
  public void setUp() throws Exception {
    GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(JsonRpcParameter.class, new JsonRpcParameterDeserializer());
    gson = builder.create();
  }

  @Test
  public void testString() {
    String stringValue = "test";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                {"value": "%s"}
                """
                .formatted(stringValue),
            JsonRpcParameter.class);
    assertEquals(stringValue, jsonRpcParameter.getValue());
    assertEquals(java.lang.String.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testBoolean() {
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
            """
                {"value": true}
                """,
            JsonRpcParameter.class);
    assertEquals(true, jsonRpcParameter.getValue());
    assertEquals(java.lang.Boolean.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testInt() {
    Integer intValue = Integer.MAX_VALUE;
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
        {"value": %d}
        """
                .formatted(intValue),
            JsonRpcParameter.class);
    assertEquals(intValue, jsonRpcParameter.getValue());
    assertEquals(java.lang.Integer.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testLong() {
    Long longValue = (long) Integer.MAX_VALUE + 4;
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                {"value": %d}
                """
                .formatted(longValue),
            JsonRpcParameter.class);
    assertEquals(longValue, jsonRpcParameter.getValue());
    assertEquals(java.lang.Long.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testChar() {
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
            """
            {"value": 'a'}
            """,
            JsonRpcParameter.class);
    assertEquals('a', jsonRpcParameter.getValue());
    assertEquals(java.lang.Character.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testNullValue() {
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
            """
              {"value": null}
              """,
            JsonRpcParameter.class);
    assertNull(jsonRpcParameter.getValue());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testEmpty() {
    JsonRpcParameter jsonRpcParameter = gson.fromJson("{}", JsonRpcParameter.class);
    assertNull(jsonRpcParameter.getValue());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testStringArray() {
    String[] stringArray = new String[] {"test1", "test2"};
    String arrayType = "[Ljava.lang.String;";
    String jsonInput =
            """
                    {
                        "value": ["%s", "%s"],
                        "type":"%s"
                    }
                    """
            .formatted(stringArray[0], stringArray[1], arrayType);
    JsonRpcParameter jsonRpcParameter = gson.fromJson(jsonInput, JsonRpcParameter.class);
    assertArrayEquals(stringArray, (String[]) jsonRpcParameter.getValue());
    assertEquals(stringArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testIntArray() {
    int[] intArray = new int[] {1, 2};
    String arrayType = "[I";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(intArray[0], intArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(intArray, (int[]) jsonRpcParameter.getValue());
    assertEquals(intArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testIntegerArray() {
    Integer[] integerArray = new Integer[] {1, 2};
    String arrayType = integerArray.getClass().getName();
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(integerArray[0], integerArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(integerArray, (Integer[]) jsonRpcParameter.getValue());
    assertEquals(integerArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testFloatArray() {
    float[] floatArray = new float[] {1, 2};
    String arrayType = "[F";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(floatArray[0], floatArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(floatArray, (float[]) jsonRpcParameter.getValue(), 0.0f);
    assertEquals(floatArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testDoubleArray() {
    double[] doubleArray = new double[] {1, 2};
    String arrayType = "[D";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(doubleArray[0], doubleArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(doubleArray, (double[]) jsonRpcParameter.getValue(), 0.0);
    assertEquals(doubleArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testLongArray() {
    long[] longArray = new long[] {1, 2};
    String arrayType = "[J";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(longArray[0], longArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(longArray, (long[]) jsonRpcParameter.getValue());
    assertEquals(longArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testCharArray() {
    char[] charArray = new char[] {'a', 'b'};
    String arrayType = "[C";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(charArray[0], charArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(charArray, (char[]) jsonRpcParameter.getValue());
    assertEquals(charArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testBooleanArray() {
    boolean[] booleanArray = new boolean[] {true, false};
    String arrayType = "[Z";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                            {
                                "value": ["%s", "%s"],
                                "type":"%s"
                            }
                            """
                .formatted(true, false, arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(booleanArray, (boolean[]) jsonRpcParameter.getValue());
    assertEquals(booleanArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testShortArray() {
    short[] shortArray = new short[] {1, 2};
    String arrayType = "[S";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
        {
          "value": ["%s", "%s"],
          "type":"%s"
        }
        """
                .formatted(shortArray[0], shortArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(shortArray, (short[]) jsonRpcParameter.getValue());
    assertEquals(shortArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testByteArray() {
    byte[] byteArray = new byte[] {1, 2};
    String arrayType = "[B";
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
        {
          "value": ["%s", "%s"],
          "type":"%s"
        }
        """
                .formatted(byteArray[0], byteArray[1], arrayType),
            JsonRpcParameter.class);
    assertArrayEquals(byteArray, (byte[]) jsonRpcParameter.getValue());
    assertEquals(byteArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertEquals(arrayType, jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testStringArray_withoutType() {
    String[] stringArray = new String[] {"test1", "test2"};
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
            {
                "value": ["%s", "%s"]
            }
            """
                .formatted(stringArray[0], stringArray[1]),
            JsonRpcParameter.class);
    assertArrayEquals(stringArray, (String[]) jsonRpcParameter.getValue());
    assertEquals(stringArray.getClass(), jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testIntArray_withoutType() {
    int[] intArray = new int[] {1, 2};
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                        {"value": [%d, %d]}
                """
                .formatted(intArray[0], intArray[1]),
            JsonRpcParameter.class);
    assertEquals(intArray[0], (int) ((Integer[]) jsonRpcParameter.getValue())[0]);
    assertEquals(intArray[1], (int) ((Integer[]) jsonRpcParameter.getValue())[1]);
    assertEquals(Integer[].class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }

  @Test
  public void testDoubleArray_withoutType() {
    double[] doubleArray = new double[] {1.4, 2.5};
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(
                """
                        {"value": [%s, %s]}
                """
                .formatted(doubleArray[0], doubleArray[1]),
            JsonRpcParameter.class);
    assertEquals(doubleArray[0], ((Double[]) jsonRpcParameter.getValue())[0], 0.0);
    assertEquals(doubleArray[1], ((Double[]) jsonRpcParameter.getValue())[1], 0.0);
    assertEquals(Double[].class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
    assertFalse(jsonRpcParameter.isRef());
  }
}
