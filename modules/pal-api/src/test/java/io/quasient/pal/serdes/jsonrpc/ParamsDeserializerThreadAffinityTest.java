/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.quasient.pal.messages.jsonrpc.Params;
import org.junit.Test;

/**
 * Tests for threadAffinity field deserialization in {@link ParamsDeserializer}.
 *
 * <p>Verifies that the {@code threadAffinity} field in a JSON-RPC params object is correctly parsed
 * into {@link Params#getThreadAffinity()}.
 */
public class ParamsDeserializerThreadAffinityTest {

  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();

  @Test
  public void deserializesThreadAffinity() {
    String json = "{\"type\":\"Foo\",\"method\":\"bar\",\"threadAffinity\":\"fx-thread\"}";
    Params params = gson.fromJson(json, Params.class);
    assertThat(params.getThreadAffinity(), is("fx-thread"));
  }

  @Test
  public void missingThreadAffinityIsNull() {
    String json = "{\"type\":\"Foo\",\"method\":\"bar\"}";
    Params params = gson.fromJson(json, Params.class);
    assertThat(params.getThreadAffinity(), is(nullValue()));
  }

  @Test
  public void nullThreadAffinityIsNull() {
    String json = "{\"type\":\"Foo\",\"method\":\"bar\",\"threadAffinity\":null}";
    Params params = gson.fromJson(json, Params.class);
    assertThat(params.getThreadAffinity(), is(nullValue()));
  }
}
