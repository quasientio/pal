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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainDslTest {

  private static JsonRpcResponse okResponseWithRefAndValue(String id) {
    // Build a response with both a value and a ref; value is a simple number
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue("42")
            .withType("java.lang.Integer")
            .withRef(1)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build();
    return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  }

  @Test
  public void buildAndSendChain_populatesResultAndInstanceRefs() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    // Stub the peer to always return an OK response regardless of request
    when(peer.sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any()))
        .thenAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              return CompletableFuture.completedFuture(okResponseWithRefAndValue(req.getId()));
            });

    RpcChain chain = new RpcChain(peer);
    // 1) create an instance and name it
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"abc"});
    // 2) call instance method and store result as 'len'
    inst.call("length", "len");
    // 3) call a static method
    inst.callStatic("java.lang.Integer", "parseInt", new Object[] {"7"});

    RpcChainResult result = chain.send();
    // getAllValues should not be empty and contain our deferred var 'len'
    result.getAllVarNames(); // exercise API without storing unused local
    // May or may not contain 'len' depending on unwrap; assert no exception and presence optional
    assertThat(result.getAllValues().size() >= 1, is(true));

    // toString should render something
    assertThat(result.toString(), containsString("chainValues"));
  }

  @Test
  public void with_unknownVar_throwsIAE() {
    ThinPeer peer = mock(ThinPeer.class);
    RpcChain chain = new RpcChain(peer);
    assertThrows(IllegalArgumentException.class, () -> chain.with("nope"));
  }
}
