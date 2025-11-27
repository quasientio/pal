/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.dsl.jsonrpc;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainErrorHandlingTest {

  @Test
  public void missingResultAndError_throwsISE() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    // Return a response with neither error nor result → chain should throw
    JsonRpcResponse bad = JsonRpcResponse.builder().withId("x").build();
    doReturn(CompletableFuture.completedFuture(bad))
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    chain.callStatic("java.lang.Integer", "noop", new Object[] {});

    assertThrows(IllegalStateException.class, chain::send);
  }

  @Test
  public void resolveArgument_existingArgument_passthrough() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    // Return a simple OK response
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue("11")
            .withType("java.lang.Integer")
            .build();
    JsonRpcResponse ok =
        JsonRpcResponse.builder()
            .withId("y")
            .withResult(
                JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build())
            .build();
    doReturn(CompletableFuture.completedFuture(ok))
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    // Pass an existing Argument; resolveArgument should return it unchanged (no exceptions)
    chain.callStatic(
        "java.lang.Integer",
        "valueOf",
        new Object[] {Argument.builder().withValue(11).withType("java.lang.Integer").build()});
    chain.send();
  }
}
