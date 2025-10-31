/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.Params;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;

public class ParamsDeserializerTest {

  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();

  // Common test arguments used in both testArgumentInValue and testArgumentInArgs
  private static final String[] VALID_ARGS = {
    "{}",
    "4", // 4 as int
    "4.3", // 4 as double
    "\"4\"", // 4 as string
    "\"Hello\"",
    "null",
    "{\"value\": -10}",
    "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}",
    // arrays
    "[false,false,true]", // no type => Boolean[]
    "[\"Hello\",\"world\"]", // no type => String[]
    "[]", // no type => empty Object[]
    "{\"value\":[false,true],\"type\":\"[Z\"}", // typed boolean array => boolean[]
    "{\"value\":[\"Hello\",\"world\",\"!\"],\"type\":\"[Ljava.lang.String;\"}", // typed String[]
    "{\"value\":[1,2,3],\"type\":\"[I\"}", // typed int[] {1,2,3}
    "{\"value\":[],\"type\":\"[I\"}", // typed empty int[]
    "{\"value\":null,\"type\":\"[I\"}", // typed null int[]
    "{\"value\":[\"Hello\",null,\"world\"],\"type\":\"[Ljava.lang.String;\"}", // typed String[]
    // arrays with suffixed numbers
    "[239823d, 0.5f, 9999l]", // inferred type -> Double[]
    "{\"value\":[239823d,38723d,2323d],\"type\":\"[D\"}", // with type
    "{\"value\":[239823d,38723d,2323d]}", // without type
    "{\"value\":[\"239823d\",\"0.5\",\"9999d\"],\"type\":\"[Ljava.lang.Double;\"}",
    "{\"value\":[\"239823d\",\"0.5d\",\"9999d\"]}", // as strings, and without type
    "{\"value\":[23f,1f,3f],\"type\":\"[F\"}",
    "{\"value\":[\"23\",\"1f\",\"3f\"],\"type\":\"[Ljava.lang.Float;\"}",
    "{\"value\":[2398239l,-23L],\"type\":\"[J\"}",
    "{\"value\":[\"2398239\",\"-23l\"],\"type\":\"[Ljava.lang.Long;\"}",
  };

  @Test
  public void testArgumentWithTypeNameAndValue() {
    String json =
        """
            {
              "type": "example.Type",
              "method": "exampleMethod",
              "args": [{"value": "Hello", "type": "java.lang.String", "name": "StringParam"}]
            }
            """;
    Params params = gson.fromJson(json, Params.class);

    // Assertions
    assertNotNull(params);
    assertEquals("example.Type", params.getType());
    assertEquals("exampleMethod", params.getMethod());

    List<Argument> args = params.getArgs();
    assertNotNull(args);
    assertEquals(1, args.size());

    Argument argument = args.get(0);
    assertNotNull(argument);
    assertEquals("Hello", argument.getValue());
    assertEquals("java.lang.String", argument.getType());
    assertEquals("StringParam", argument.getName());
  }

  @Test
  public void testArgumentWithMapArgument() {
    String json =
        """
                {
                  "type": "example.Type",
                  "method": "exampleMethod",
                  "args": [{"value": {"uno":1,"two":2}, "type": "java.util.HashMap"}]
                }
                """;
    Params params = gson.fromJson(json, Params.class);

    // Assertions
    assertNotNull(params);
    assertEquals("example.Type", params.getType());
    assertEquals("exampleMethod", params.getMethod());

    List<Argument> args = params.getArgs();
    assertNotNull(args);
    assertEquals(1, args.size());

    Argument argument = args.get(0);
    assertNotNull(argument);
    assertNotNull(argument.getValue());
    assertEquals(HashMap.class, argument.getValue().getClass());
    assertEquals(2d, ((HashMap<?, ?>) argument.getValue()).get("two"));
  }

  @Test
  public void testArgumentWithTypeAndNullValue() {
    String json =
        """
                {
                  "type": "example.Type",
                  "method": "exampleMethod",
                  "args": [{"value": null, "type": "java.lang.Integer"}]
                }
                """;
    Params params = gson.fromJson(json, Params.class);

    // Assertions
    assertNotNull(params);
    assertEquals("example.Type", params.getType());
    assertEquals("exampleMethod", params.getMethod());

    List<Argument> args = params.getArgs();
    assertNotNull(args);
    assertEquals(1, args.size());

    Argument argument = args.get(0);
    assertNotNull(argument);
    assertTrue(argument.isNull());
    assertEquals("java.lang.Integer", argument.getType());
  }

  @Test
  public void testArgumentWithTypeAndNoValue() {
    String json =
        """
                    {
                      "type": "example.Type",
                      "method": "exampleMethod",
                      "args": [{"type": "java.lang.Integer"}]
                    }
                    """;
    Params params = gson.fromJson(json, Params.class);

    // Assertions
    assertNotNull(params);
    assertEquals("example.Type", params.getType());
    assertEquals("exampleMethod", params.getMethod());

    List<Argument> args = params.getArgs();
    assertNotNull(args);
    assertEquals(1, args.size());

    Argument argument = args.get(0);
    assertNotNull(argument);
    assertTrue(argument.isNull());
    assertEquals("java.lang.Integer", argument.getType());
  }

  @Test
  public void testArrayListArgument() {
    String json =
        """
                {
                  "type": "example.Type",
                  "method": "exampleMethod",
                  "args": [{"value": ["Hello", "here"], "type": "java.util.ArrayList"}]
                }
                """;
    Params params = gson.fromJson(json, Params.class);

    // Assertions
    assertNotNull(params);
    assertEquals("example.Type", params.getType());
    assertEquals("exampleMethod", params.getMethod());

    List<Argument> args = params.getArgs();
    assertNotNull(args);
    assertEquals(1, args.size());

    Argument argument = args.get(0);
    assertNotNull(argument);
    assertNotNull(argument.getValue());
    assertEquals(ArrayList.class, argument.getValue().getClass());
    assertEquals(2, ((ArrayList<?>) argument.getValue()).size());
  }

  @Test
  public void testArgumentInValue() {
    String paramsJson =
        "{"
            + "\"type\": \"example.Type\","
            + "\"method\": \"exampleMethod\","
            + "\"value\": %s"
            + "}";

    for (String arg : VALID_ARGS) {
      String json = String.format(paramsJson, arg);
      Params params = gson.fromJson(json, Params.class);

      // Assertions
      assertNotNull(params); // Ensure params is not null
      assertEquals("example.Type", params.getType());
      assertEquals("exampleMethod", params.getMethod());

      Argument value = params.getValue();
      assertNotNull(value);
      Argument empty = new Argument();
      assertTrue(empty.isNull());

      switch (arg) {
        case "{}", "null" -> assertEquals(empty, value);
        case "4" -> assertEquals(4, value.getValue()); // Integer value
        case "4.3" -> assertEquals(4.3, value.getValue()); // Double value
        case "\"4\"" -> assertEquals("4", value.getValue()); // String value
        case "\"Hello\"" -> assertEquals("Hello", value.getValue()); // String value
        case "{\"value\": -10}" -> assertEquals(-10, value.getValue()); // Object with "value" field
        case "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}" -> {
          assertEquals("Hello", value.getValue());
          assertEquals("java.lang.String", value.getType());
        }
          // arrays:
        case "[false,false,true]" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Boolean[]);
          assertArrayEquals(new Boolean[] {false, false, true}, (Boolean[]) val);
          assertEquals("[Ljava.lang.Boolean;", value.getType());
        }
        case "[\"Hello\",\"world\"]" -> {
          Object val = value.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", "world"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", value.getType());
        }
        case "[]" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Object[]);
          assertEquals(0, ((Object[]) val).length);
          assertEquals("[Ljava.lang.Object;", value.getType());
        }
        case "{\"value\":[false,true],\"type\":\"[Z\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof boolean[]);
          assertArrayEquals(new boolean[] {false, true}, (boolean[]) val);
          assertEquals("[Z", value.getType());
        }
        case "{\"value\":[\"Hello\",\"world\",\"!\"],\"type\":\"[Ljava.lang.String;\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", "world", "!"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", value.getType());
        }
        case "{\"value\":[1,2,3],\"type\":\"[I\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof int[]);
          assertArrayEquals(new int[] {1, 2, 3}, (int[]) val);
          assertEquals("[I", value.getType());
        }
        case "{\"value\":[],\"type\":\"[I\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof int[]);
          assertEquals(0, ((int[]) val).length);
          assertEquals("[I", value.getType());
        }
        case "{\"value\":null,\"type\":\"[I\"}" -> {
          // null typed array
          assertNull(value.getValue());
          assertEquals("[I", value.getType());
        }
        case "{\"value\":[\"Hello\",null,\"world\"],\"type\":\"[Ljava.lang.String;\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", null, "world"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", value.getType());
        }
        case "[239823d, 0.5f, 9999l]" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Double[]);
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", value.getType());
        }
        case "{\"value\":[239823d,38723d,2323d],\"type\":\"[D\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof double[]);
          assertArrayEquals(new double[] {239823.0, 38723.0, 2323.0}, (double[]) val, 0.0);
          assertEquals("[D", value.getType());
        }
        case "{\"value\":[239823d,38723d,2323d]}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Double[]);
          assertArrayEquals(new Double[] {239823.0, 38723.0, 2323.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", value.getType());
        }
        case "{\"value\":[\"239823d\",\"0.5\",\"9999d\"],\"type\":\"[Ljava.lang.Double;\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Double[]);
          // With strings, suffix parsing leads all to double
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", value.getType());
        }
        case "{\"value\":[\"239823d\",\"0.5d\",\"9999d\"]}" -> {
          // no type given
          Object val = value.getValue();
          assertTrue(val instanceof Double[]);
          // With strings, suffix parsing leads all to double
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", value.getType());
        }
        case "{\"value\":[23f,1f,3f],\"type\":\"[F\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof float[]);
          assertArrayEquals(new float[] {23f, 1f, 3f}, (float[]) val, 0f);
          assertEquals("[F", value.getType());
        }
        case "{\"value\":[\"23\",\"1f\",\"3f\"],\"type\":\"[Ljava.lang.Float;\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Float[]);
          assertArrayEquals(new Float[] {23f, 1f, 3f}, (Float[]) val);
          assertEquals("[Ljava.lang.Float;", value.getType());
        }
        case "{\"value\":[2398239l,-23L],\"type\":\"[J\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof long[]);
          assertArrayEquals(new long[] {2398239L, -23L}, (long[]) val);
          assertEquals("[J", value.getType());
        }
        case "{\"value\":[\"2398239\",\"-23l\"],\"type\":\"[Ljava.lang.Long;\"}" -> {
          Object val = value.getValue();
          assertTrue(val instanceof Long[]);
          assertArrayEquals(new Long[] {2398239L, -23L}, (Long[]) val);
          assertEquals("[Ljava.lang.Long;", value.getType());
        }
        default -> fail("Unexpected argument: " + arg);
      }
    }
  }

  @Test
  public void testArgumentInArgs() {
    String paramsJson =
        "{"
            + "\"type\": \"example.Type\","
            + "\"method\": \"exampleMethod\","
            + "\"args\": [%s]"
            + "}";

    for (String arg : VALID_ARGS) {
      String json = String.format(paramsJson, arg);
      Params params = gson.fromJson(json, Params.class);

      // Assertions
      assertNotNull(params); // Ensure params is not null
      assertEquals("example.Type", params.getType());
      assertEquals("exampleMethod", params.getMethod());

      List<Argument> args = params.getArgs();
      assertNotNull(args); // Args list should not be null
      assertEquals(1, args.size()); // There should be exactly one argument in the list

      Argument argument = args.get(0);
      assertNotNull(argument); // Argument should not be null
      switch (arg) {
        case "{}" -> assertNull(argument.getValue()); // Empty object results in null value
        case "4" -> assertEquals(4, argument.getValue()); // Integer value
        case "4.3" -> assertEquals(4.3, argument.getValue()); // Double value
        case "\"4\"" -> assertEquals("4", argument.getValue()); // String value
        case "\"Hello\"" -> assertEquals("Hello", argument.getValue()); // String value
        case "null" -> assertNull(argument.getValue()); // Null input results in null value
        case "{\"value\": -10}" ->
            assertEquals(-10, argument.getValue()); // Object with "value" field
        case "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}" -> {
          assertEquals("Hello", argument.getValue());
          assertEquals("java.lang.String", argument.getType());
        }
          // arrays:
        case "[false,false,true]" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Boolean[]);
          assertArrayEquals(new Boolean[] {false, false, true}, (Boolean[]) val);
          assertEquals("[Ljava.lang.Boolean;", argument.getType());
        }
        case "[\"Hello\",\"world\"]" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", "world"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", argument.getType());
        }
        case "[]" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Object[]);
          assertEquals(0, ((Object[]) val).length);
          assertEquals("[Ljava.lang.Object;", argument.getType());
        }
        case "{\"value\":[false,true],\"type\":\"[Z\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof boolean[]);
          assertArrayEquals(new boolean[] {false, true}, (boolean[]) val);
          assertEquals("[Z", argument.getType());
        }
        case "{\"value\":[\"Hello\",\"world\",\"!\"],\"type\":\"[Ljava.lang.String;\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", "world", "!"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", argument.getType());
        }
        case "{\"value\":[1,2,3],\"type\":\"[I\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof int[]);
          assertArrayEquals(new int[] {1, 2, 3}, (int[]) val);
          assertEquals("[I", argument.getType());
        }
        case "{\"value\":[],\"type\":\"[I\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof int[]);
          assertEquals(0, ((int[]) val).length);
          assertEquals("[I", argument.getType());
        }
        case "{\"value\":null,\"type\":\"[I\"}" -> {
          // null typed array
          assertNull(argument.getValue());
          assertEquals("[I", argument.getType());
        }
        case "{\"value\":[\"Hello\",null,\"world\"],\"type\":\"[Ljava.lang.String;\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof String[]);
          assertArrayEquals(new String[] {"Hello", null, "world"}, (String[]) val);
          assertEquals("[Ljava.lang.String;", argument.getType());
        }
        case "[239823d, 0.5f, 9999l]" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Double[]);
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", argument.getType());
        }
        case "{\"value\":[239823d,38723d,2323d],\"type\":\"[D\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof double[]);
          assertArrayEquals(new double[] {239823.0, 38723.0, 2323.0}, (double[]) val, 0.0);
          assertEquals("[D", argument.getType());
        }
        case "{\"value\":[239823d,38723d,2323d]}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Double[]);
          assertArrayEquals(new Double[] {239823.0, 38723.0, 2323.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", argument.getType());
        }
        case "{\"value\":[\"239823d\",\"0.5\",\"9999d\"],\"type\":\"[Ljava.lang.Double;\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Double[]);
          // With strings, suffix parsing leads all to double
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", argument.getType());
        }
        case "{\"value\":[\"239823d\",\"0.5d\",\"9999d\"]}" -> {
          // no type given
          Object val = argument.getValue();
          assertTrue(val instanceof Double[]);
          // With strings, suffix parsing leads all to double
          assertArrayEquals(new Double[] {239823.0, 0.5, 9999.0}, (Double[]) val);
          assertEquals("[Ljava.lang.Double;", argument.getType());
        }
        case "{\"value\":[23f,1f,3f],\"type\":\"[F\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof float[]);
          assertArrayEquals(new float[] {23f, 1f, 3f}, (float[]) val, 0f);
          assertEquals("[F", argument.getType());
        }
        case "{\"value\":[\"23\",\"1f\",\"3f\"],\"type\":\"[Ljava.lang.Float;\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Float[]);
          assertArrayEquals(new Float[] {23f, 1f, 3f}, (Float[]) val);
          assertEquals("[Ljava.lang.Float;", argument.getType());
        }
        case "{\"value\":[2398239l,-23L],\"type\":\"[J\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof long[]);
          assertArrayEquals(new long[] {2398239L, -23L}, (long[]) val);
          assertEquals("[J", argument.getType());
        }
        case "{\"value\":[\"2398239\",\"-23l\"],\"type\":\"[Ljava.lang.Long;\"}" -> {
          Object val = argument.getValue();
          assertTrue(val instanceof Long[]);
          assertArrayEquals(new Long[] {2398239L, -23L}, (Long[]) val);
          assertEquals("[Ljava.lang.Long;", argument.getType());
        }
        default -> fail("Unexpected argument: " + arg);
      }
    }
  }
}
