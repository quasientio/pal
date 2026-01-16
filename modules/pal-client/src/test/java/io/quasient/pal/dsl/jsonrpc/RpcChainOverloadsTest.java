/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainOverloadsTest {

  private static JsonRpcResponse voidResponse(String id) {
    return JsonRpcResponse.builder()
        .withId(id)
        .withResult(JsonRpcResponseReturnValue.builder().withIsVoid(true).build())
        .build();
  }

  private static JsonRpcResponse refResponse(String id) {
    return JsonRpcResponse.builder()
        .withId(id)
        .withResult(
            JsonRpcResponseReturnValue.builder()
                .withIsVoid(false)
                .withValue(
                    ResponseObject.builder()
                        .withIsNull(false)
                        .withRef(1)
                        .withType("java.lang.String")
                        .withValue("x")
                        .build())
                .build())
        .build();
  }

  @Test
  public void overloads_buildRequests_andSendCompletes() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    List<JsonRpcRequest> seen = new ArrayList<>();
    doAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              seen.add(req);
              String id = req.getId();
              String method = req.getMethod();
              return CompletableFuture.completedFuture(
                  "new".equals(method) ? refResponse(id) : voidResponse(id));
            })
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    // Create instance via various overloads
    chain.create("java.lang.String");
    chain.create("java.lang.String", new Object[] {"abc"});
    chain.create("java.lang.String", "named");

    RpcChainInstance inst = chain.with("named");
    // Instance call overloads
    inst.call("length");
    inst.call("substring", new Object[] {0});
    inst.call("toString", "s2");

    // Static get overloads and put
    chain.getStatic("java.lang.Integer", "MAX_VALUE");
    chain.getStatic("java.lang.Integer", "MAX_VALUE", "max");
    chain.putStatic("java.lang.System", "out", new Object());

    RpcChainResult result = chain.send();
    // No exception and requests were built
    assertThat(seen.size(), is(9));
    // chainResult exists even if many responses were void
    assertThat(result.getAllValues() != null, is(true));
  }
}
