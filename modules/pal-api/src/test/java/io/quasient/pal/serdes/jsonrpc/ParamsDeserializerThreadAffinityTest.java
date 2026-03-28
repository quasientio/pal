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
