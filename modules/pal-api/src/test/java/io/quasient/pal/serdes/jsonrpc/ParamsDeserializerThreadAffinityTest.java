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

import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.quasient.pal.messages.jsonrpc.Params;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for threadAffinity field deserialization in {@link ParamsDeserializer}.
 *
 * <p>Verifies that the {@code threadAffinity} field in a JSON-RPC params object is correctly parsed
 * into {@link Params#getThreadAffinity()}.
 */
@SuppressWarnings("UnusedVariable")
public class ParamsDeserializerThreadAffinityTest {

  private final Gson gson =
      new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();

  @Test
  @Ignore("Awaiting implementation in #745")
  public void deserializesThreadAffinity() {
    // Given: JSON with {"type":"Foo","method":"bar","threadAffinity":"fx-thread"}
    // When: Deserialized to Params
    // Then: params.getThreadAffinity() == "fx-thread"

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void missingThreadAffinityIsNull() {
    // Given: JSON with {"type":"Foo","method":"bar"} (no threadAffinity field)
    // When: Deserialized to Params
    // Then: params.getThreadAffinity() == null

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void nullThreadAffinityIsNull() {
    // Given: JSON with {"type":"Foo","method":"bar","threadAffinity":null}
    // When: Deserialized to Params
    // Then: params.getThreadAffinity() == null

    // TODO(#745): Implement test logic
    fail("Not yet implemented");
  }
}
