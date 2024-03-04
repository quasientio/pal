package net.ittera.pal.messages.jsonrpc;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
  public void testBoolean() {
    JsonRpcParameter jsonRpcParameter = gson.fromJson("{\"value\": true}", JsonRpcParameter.class);
    assertEquals(true, jsonRpcParameter.getValue());
    assertEquals(java.lang.Boolean.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
  }

  @Test
  public void testInt() {
    Integer intValue = Integer.MAX_VALUE;
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(String.format("{\"value\": %d}", intValue), JsonRpcParameter.class);
    assertEquals(intValue, jsonRpcParameter.getValue());
    assertEquals(java.lang.Integer.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
  }

  @Test
  public void testLong() {
    Long longValue = (long) Integer.MAX_VALUE + 4;
    JsonRpcParameter jsonRpcParameter =
        gson.fromJson(String.format("{\"value\": %d}", longValue), JsonRpcParameter.class);
    assertEquals(longValue, jsonRpcParameter.getValue());
    assertEquals(java.lang.Long.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
  }

  @Test
  public void testChar() {
    JsonRpcParameter jsonRpcParameter = gson.fromJson("{\"value\": 'a'}", JsonRpcParameter.class);
    assertEquals('a', jsonRpcParameter.getValue());
    assertEquals(java.lang.Character.class, jsonRpcParameter.getValue().getClass());
    assertNull(jsonRpcParameter.getType());
  }

  @Test
  public void testNullValue() {
    JsonRpcParameter jsonRpcParameter = gson.fromJson("{\"value\": null}", JsonRpcParameter.class);
    assertNull(jsonRpcParameter.getValue());
    assertNull(jsonRpcParameter.getType());
  }

  @Test
  public void testEmpty() {
    JsonRpcParameter jsonRpcParameter = gson.fromJson("{}", JsonRpcParameter.class);
    assertNull(jsonRpcParameter.getValue());
    assertNull(jsonRpcParameter.getType());
  }
}
