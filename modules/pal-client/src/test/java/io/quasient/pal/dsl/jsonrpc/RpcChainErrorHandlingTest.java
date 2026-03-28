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
package io.quasient.pal.dsl.jsonrpc;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
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
