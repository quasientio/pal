/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.junit.Assert.fail;

import java.util.UUID;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for threadAffinity propagation through {@link MessageBuilder#jsonRpcRequestToExecMessage}.
 *
 * <p>Verifies that the {@code threadAffinity} field on JSON-RPC {@code Params} is correctly copied
 * to the resulting {@code ExecMessage} when converting from JSON-RPC request format to the internal
 * Colfer message format.
 */
@SuppressWarnings("UnusedVariable")
public class MessageBuilderJsonRpcThreadAffinityTest {

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder builder;

  @Before
  public void setUp() {
    builder = new MessageBuilder(peerId);
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void jsonRpcRequestToExecMessagePreservesThreadAffinity() {
    // Given: JsonRpcRequest with Params containing threadAffinity="fx-thread" (class method call)
    // When: jsonRpcRequestToExecMessage() called
    // Then: Resulting ExecMessage has threadAffinity == "fx-thread"

    // TODO(#745): Implement test logic
    // Build a class method call JsonRpcRequest via JsonRpcMessageFactory,
    // set threadAffinity="fx-thread" on the Params,
    // call builder.jsonRpcRequestToExecMessage(req, peerId),
    // assert em.getThreadAffinity() equals "fx-thread"
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void jsonRpcRequestToExecMessageNullThreadAffinity() {
    // Given: JsonRpcRequest with Params where threadAffinity is null
    // When: jsonRpcRequestToExecMessage() called
    // Then: Resulting ExecMessage has threadAffinity == null (or empty, per Colfer default)

    // TODO(#745): Implement test logic
    // Build a class method call JsonRpcRequest via JsonRpcMessageFactory,
    // do NOT set threadAffinity on the Params (leave as null),
    // call builder.jsonRpcRequestToExecMessage(req, peerId),
    // assert em.getThreadAffinity() is null or empty
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #745")
  public void jsonRpcRequestToExecMessageEmptyThreadAffinity() {
    // Given: JsonRpcRequest with Params where threadAffinity is ""
    // When: jsonRpcRequestToExecMessage() called
    // Then: Resulting ExecMessage has threadAffinity == null (empty treated as absent)

    // TODO(#745): Implement test logic
    // Build a class method call JsonRpcRequest via JsonRpcMessageFactory,
    // set threadAffinity="" on the Params,
    // call builder.jsonRpcRequestToExecMessage(req, peerId),
    // assert em.getThreadAffinity() is null or empty (empty treated as absent)
    fail("Not yet implemented");
  }
}
