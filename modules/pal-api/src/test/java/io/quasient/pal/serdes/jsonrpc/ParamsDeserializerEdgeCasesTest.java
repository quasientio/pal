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
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.Params;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class ParamsDeserializerEdgeCasesTest {

  private Gson gson;

  @Before
  public void setUp() {
    gson = new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();
  }

  private Params parse(String json) {
    return gson.fromJson(json, Params.class);
  }

  @Test
  public void single_double_string_with_d_suffix_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"double\", \"value\": \"1.25d\"}\n}");
    assertThat(p.getValue().getValue(), is(1.25d));
    assertThat(p.getValue().getType(), is("double"));
  }

  @Test
  public void single_long_string_with_l_suffix_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"long\", \"value\": \"123l\"}\n}");
    assertThat(p.getValue().getValue(), is(123L));
    assertThat(p.getValue().getType(), is("long"));
  }

  @Test
  public void single_float_string_with_f_suffix_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"float\", \"value\": \"2.5f\"}\n}");
    assertThat(((Number) p.getValue().getValue()).floatValue(), is(2.5f));
    assertThat(p.getValue().getType(), is("float"));
  }

  @Test
  public void single_int_non_integer_number_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"int\", \"value\": 1.5}\n}"));
  }

  @Test
  public void single_char_length_two_string_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"char\", \"value\": \"AB\"}\n}"));
  }

  @Test
  public void single_boolean_string_case_insensitive_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"boolean\", \"value\": \"TRUE\"}\n}");
    assertThat(p.getValue().getValue(), is(true));
  }

  @Test
  public void single_short_out_of_range_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"short\", \"value\": 40000}\n}"));
  }

  @Test
  public void single_byte_out_of_range_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"byte\", \"value\": 200}\n}"));
  }

  @Test
  public void typed_array_int_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"int[]\", \"value\": [1,2]}\n}");
    Object v = p.getValue().getValue();
    assertTrue(v.getClass().isArray());
    assertEquals(int.class, v.getClass().getComponentType());
    assertEquals(2, java.lang.reflect.Array.getLength(v));
    assertEquals(1, java.lang.reflect.Array.getInt(v, 0));
    assertEquals(2, java.lang.reflect.Array.getInt(v, 1));
  }

  @Test
  public void typed_array_int_with_null_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"int[]\", \"value\": [1,null]}\n}"));
  }

  @Test
  public void typed_array_int_with_object_element_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"int[]\", \"value\": [{\"x\":1}]}\n}"));
  }

  @Test
  public void typed_array_Integer_allows_nulls() {
    Params p = parse("{\n  \"value\": {\"type\": \"Integer[]\", \"value\": [1,null,3]}\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Integer[]);
    Integer[] arr = (Integer[]) v;
    assertArrayEquals(new Integer[] {1, null, 3}, arr);
  }

  @Test
  public void typed_array_char_from_strings_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"char[]\", \"value\": [\"A\",\"B\"]}\n}");
    Object v = p.getValue().getValue();
    assertEquals(char.class, v.getClass().getComponentType());
    assertEquals('A', java.lang.reflect.Array.getChar(v, 0));
    assertEquals('B', java.lang.reflect.Array.getChar(v, 1));
  }

  @Test
  public void bare_array_empty_infers_object_array() {
    Params p = parse("{\n  \"value\": []\n}");
    Object[] arr = (Object[]) p.getValue().getValue();
    assertEquals(0, arr.length);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Object;"));
  }

  @Test
  public void bare_array_all_strings_infers_string_array() {
    Params p = parse("{\n  \"value\": [\"a\",\"b\"]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof String[]);
    assertThat(p.getValue().getType(), is("[Ljava.lang.String;"));
  }

  @Test
  public void bare_array_float_suffix_f_infers_float_typeName_and_object_array_value() {
    Params p = parse("{\n  \"value\": [\"1.0f\",\"2.5f\"]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Object[]);
    Object[] arr = (Object[]) v;
    assertEquals(2, arr.length);
    assertTrue(arr[0] instanceof Float);
    assertTrue(arr[1] instanceof Float);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Float;"));
  }

  @Test
  public void bare_array_nested_array_throws() {
    assertThrows(JsonParseException.class, () -> parse("{\n  \"value\": [[1,2]]\n}"));
  }

  @Test
  public void object_with_type_reflection_fallback_hashmap() {
    Params p = parse("{\n  \"value\": {\"type\": \"java.util.HashMap\", \"value\": {\"a\":1}}\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Map);
    Object num = ((Map<?, ?>) v).get("a");
    assertTrue(num instanceof Number);
    assertEquals(1, ((Number) num).intValue());
  }

  @Test
  public void bare_array_booleans_infers_boolean_array() {
    Params p = parse("{\n  \"value\": [true,false]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Boolean[]);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Boolean;"));
  }

  @Test
  public void bare_array_integers_infers_integer_array() {
    Params p = parse("{\n  \"value\": [1,2,3]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v.getClass().isArray());
    assertThat(
        p.getValue().getType(), anyOf(is("[Ljava.lang.Integer;"), is("[Ljava.lang.Double;")));
    assertEquals(3, java.lang.reflect.Array.getLength(v));
    assertEquals(1, ((Number) java.lang.reflect.Array.get(v, 0)).intValue());
  }

  @Test
  public void bare_array_anyDouble_infers_double_array() {
    Params p = parse("{\n  \"value\": [1.5,2]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Double[]);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Double;"));
  }

  @Test
  public void bare_primitive_number_sets_integer_value() {
    Params p = parse("{\n  \"value\": 5\n}");
    assertThat(p.getValue().getValue(), is(5));
    assertNull(p.getValue().getType());
  }

  @Test
  public void object_with_value_non_array_copies_inner_argument() {
    Params p = parse("{\n  \"value\": {\"value\": {\"type\": \"int\", \"value\": 10}}\n}");
    assertThat(p.getValue().getValue(), is(10));
    assertThat(p.getValue().getType(), is("int"));
  }

  @Test
  public void typed_string_array_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"String[]\", \"value\": [\"a\",\"b\"]}\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof String[]);
    assertArrayEquals(new String[] {"a", "b"}, (String[]) v);
  }

  @Test
  public void typed_array_unknown_component_type_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"Foo[]\", \"value\": [1]}\n}"));
  }

  @Test
  public void typed_char_array_element_length_not_one_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"char[]\", \"value\": [\"AB\"]}\n}"));
  }

  @Test
  public void args_field_not_array_throws() {
    assertThrows(JsonParseException.class, () -> parse("{\n  \"args\": {\"x\":1}\n}"));
  }

  @Test
  public void missing_value_and_missing_args_defaults() {
    Params p = parse("{\n  \"type\": \"T\"\n}");
    assertNull(p.getValue());
    assertNotNull(p.getArgs());
    assertTrue(p.getArgs().isEmpty());
  }

  @Test
  public void object_argument_with_name_and_ref_only() {
    Params p = parse("{\n  \"value\": {\"name\": \"arg\", \"ref\": 7}\n}");
    Argument a = p.getValue();
    assertEquals("arg", a.getName());
    assertEquals(Integer.valueOf(7), a.getRef());
    assertNull(a.getValue());
  }
}
