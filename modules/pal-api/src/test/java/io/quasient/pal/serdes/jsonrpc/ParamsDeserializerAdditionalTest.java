/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.quasient.pal.messages.jsonrpc.Params;
import org.junit.Before;
import org.junit.Test;

public class ParamsDeserializerAdditionalTest {

  private Gson gson;

  @Before
  public void setUp() {
    gson = new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();
  }

  private Params parse(String json) {
    return gson.fromJson(json, Params.class);
  }

  @Test
  public void typed_array_valueNotArray_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"int[]\", \"value\": 1}\n}"));
  }

  @Test
  public void typed_boolean_array_with_stringElements_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"boolean[]\", \"value\": [\"true\"]}\n}"));
  }

  @Test
  public void typed_string_array_with_numbers_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"String[]\", \"value\": [1]}\n}"));
  }

  @Test
  public void typed_Integer_array_empty_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"Integer[]\", \"value\": []}\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Integer[]);
    assertEquals(0, ((Integer[]) v).length);
  }

  @Test
  public void typed_int_from_string_with_spaces_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"int\", \"value\": \" 17 \"}\n}");
    assertThat(p.getValue().getValue(), is(17));
  }

  @Test
  public void typed_double_from_number_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"double\", \"value\": 2.75}\n}");
    assertThat(p.getValue().getValue(), is(2.75));
  }

  @Test
  public void typed_short_from_string_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"short\", \"value\": \"123\"}\n}");
    assertThat(((Number) p.getValue().getValue()).intValue(), is(123));
  }

  @Test
  public void typed_unknown_singleType_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"Foo\", \"value\": 1}\n}"));
  }

  @Test
  public void bare_array_mixed_string_and_number_typeReflectsFirstNonNull() {
    Params p = parse("{\n  \"value\": [\"a\", 1]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Object[]); // mixed -> Object[]
    assertThat(p.getValue().getType(), is("[Ljava.lang.String;")); // first non-null is String
  }

  @Test
  public void maps_basic_topLevel_fields() {
    Params p =
        parse(
            "{\n  \"type\": \"T\", \n  \"method\": \"m\", \n  \"field\": \"f\", \n  \"instance\": 3, \n  \"args\": []\n}");
    assertThat(p.getType(), is("T"));
    assertThat(p.getMethod(), is("m"));
    assertThat(p.getField(), is("f"));
    assertThat(p.getInstance(), is(3));
    assertNotNull(p.getArgs());
    assertTrue(p.getArgs().isEmpty());
  }

  @Test
  public void typed_double_array_from_strings_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"double[]\", \"value\": [\"1d\", \"2.5\"]}\n}");
    Object v = p.getValue().getValue();
    assertEquals(double.class, v.getClass().getComponentType());
    assertEquals(1.0, java.lang.reflect.Array.getDouble(v, 0), 0.0);
    assertEquals(2.5, java.lang.reflect.Array.getDouble(v, 1), 0.0);
  }

  @Test
  public void typed_long_array_mixed_string_and_number_ok() {
    Params p = parse("{\n  \"value\": {\"type\": \"long[]\", \"value\": [\"1l\", 2]}\n}");
    Object v = p.getValue().getValue();
    assertEquals(long.class, v.getClass().getComponentType());
    assertEquals(1L, java.lang.reflect.Array.getLong(v, 0));
    assertEquals(2L, java.lang.reflect.Array.getLong(v, 1));
  }

  @Test
  public void typed_int_array_fraction_element_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"int[]\", \"value\": [1.2]}\n}"));
  }

  @Test
  public void typed_short_array_out_of_range_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"short[]\", \"value\": [40000]}\n}"));
  }

  @Test
  public void typed_byte_array_out_of_range_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"byte[]\", \"value\": [200]}\n}"));
  }

  @Test
  public void bare_array_all_nulls_infers_object_array() {
    Params p = parse("{\n  \"value\": [null, null]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Object[]);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Object;"));
  }

  @Test
  public void single_boolean_invalid_string_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"boolean\", \"value\": \"maybe\"}\n}"));
  }

  @Test
  public void single_double_invalid_string_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"double\", \"value\": \"x\"}\n}"));
  }

  @Test
  public void single_float_empty_string_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"float\", \"value\": \"\"}\n}"));
  }

  @Test
  public void single_long_empty_string_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"long\", \"value\": \"\"}\n}"));
  }

  @Test
  public void single_char_from_number_throws() {
    assertThrows(
        JsonParseException.class,
        () -> parse("{\n  \"value\": {\"type\": \"char\", \"value\": 1}\n}"));
  }

  @Test
  public void top_level_not_object_throws() {
    assertThrows(JsonParseException.class, () -> parse("[]"));
  }

  @Test
  public void value_empty_object_yields_null_argument() {
    Params p = parse("{\n  \"value\": {}\n}");
    assertNotNull(p.getValue());
    assertTrue(p.getValue().isNull());
  }

  @Test
  public void bare_mixed_first_boolean_infers_boolean_type() {
    Params p = parse("{\n  \"value\": [true, \"s\"]\n}");
    Object v = p.getValue().getValue();
    assertTrue(v instanceof Object[]);
    assertThat(p.getValue().getType(), is("[Ljava.lang.Boolean;"));
  }
}
