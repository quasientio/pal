package net.ittera.pal.serdes.jsonrpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.Params;
import org.junit.Test;

public class ParamsDeserializerTest {

  @Test
  public void testArgumentInValue() {

    Gson gson =
        new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();

    String paramsJson =
        "{"
            + "\"type\": \"example.Type\","
            + "\"method\": \"exampleMethod\","
            + "\"value\": %s"
            + "}";

    // Valid JSON arguments for the "value" field
    String[] validArgs = {
      "{}",
      "4", // 4 as int
      "4.3", // 4 as double
      "\"4\"", // 4 as string
      "\"Hello\"",
      "null",
      "{\"value\": -10}",
      "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}"
    };

    for (String arg : validArgs) {
      String json = String.format(paramsJson, arg);
      Params params = gson.fromJson(json, Params.class);

      // Assertions
      assertNotNull(params); // Ensure params is not null
      assertEquals("example.Type", params.getType());
      assertEquals("exampleMethod", params.getMethod());

      Argument value = params.getValue();

      switch (arg) {
        case "{}", "null" -> assertNull(value); // Now value should be null
        case "4" -> assertEquals(4, value.getValue()); // Integer value
        case "4.3" -> assertEquals(4.3, value.getValue()); // Double value
        case "\"4\"" -> assertEquals("4", value.getValue()); // String value
        case "\"Hello\"" -> assertEquals("Hello", value.getValue()); // String value
        case "{\"value\": -10}" -> assertEquals(-10, value.getValue()); // Object with "value" field
        case "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}" -> {
          assertEquals("Hello", value.getValue());
          assertEquals("java.lang.String", value.getType());
        }
        default -> fail("Unexpected argument: " + arg);
      }
    }
  }

  @Test
  public void testArgumentInArgs() {

    Gson gson =
        new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();

    String paramsJson =
        "{"
            + "\"type\": \"example.Type\","
            + "\"method\": \"exampleMethod\","
            + "\"args\": [%s]"
            + "}";

    // Valid JSON arguments for the "args" field
    String[] validArgs = {
      "{}",
      "4", // 4 as int
      "4.3", // 4 as double
      "\"4\"", // 4 as string
      "\"Hello\"",
      "null",
      "{\"value\": -10}",
      "{\"value\": \"Hello\", \"type\": \"java.lang.String\"}"
    };

    for (String arg : validArgs) {
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
        default -> fail("Unexpected argument: " + arg);
      }
    }
  }
}
