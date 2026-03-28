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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainAllOpsTest {

  private static JsonRpcResponse voidResponse(String id) {
    return JsonRpcResponse.builder()
        .withId(id)
        .withResult(JsonRpcResponseReturnValue.builder().withIsVoid(true).build())
        .build();
  }

  private static JsonRpcResponse valueResponse(String id, String value, String type) {
    ResponseObject ro =
        ResponseObject.builder().withIsNull(false).withValue(value).withType(type).build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build();
    return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  }

  private static JsonRpcResponse refAndValueResponse(
      String id, int ref, String value, String type) {
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue(value)
            .withType(type)
            .withRef(ref)
            .build();
    JsonRpcResponseReturnValue rv =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build();
    return JsonRpcResponse.builder().withId(id).withResult(rv).build();
  }

  private static JsonRpcResponse errorResponse(String id, int code, String message) {
    JsonRpcError err = new JsonRpcError();
    err.setCode(code);
    err.setMessage(message);
    return JsonRpcResponse.builder().withId(id).withError(err).build();
  }

  @Test
  public void endToEnd_allOps_buildRequests_andProcessResponses() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    List<JsonRpcRequest> seen = new ArrayList<>();

    doAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              seen.add(req);
              String id = req.getId();
              String method = req.getMethod();
              return CompletableFuture.completedFuture(
                  switch (method) {
                    case "new" -> refAndValueResponse(id, 10, "abc", "java.lang.String");
                    case "call" -> valueResponse(id, "7", "java.lang.Integer");
                    case "get" -> valueResponse(id, "2147483647", "java.lang.Integer");
                    case "put" -> voidResponse(id);
                    default -> errorResponse(id, -32601, "unsupported");
                  });
            })
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    // Create named instance and use it
    RpcChainInstance inst = chain.create("java.lang.String", "s", new Object[] {"abc"});
    // Instance method call -> returns 7
    inst.call("length", "len");
    // Static method call -> returns 7
    chain.callStatic("java.lang.Integer", "parseInt", new Object[] {"7"});
    // Static method with argument referencing known instance by var name -> exercises varName->ref
    // resolution
    chain.callStatic("java.lang.String", "valueOf", new Object[] {"s"});
    // Static field get -> returns MAX_VALUE
    chain.getStatic("java.lang.Integer", "MAX_VALUE", "max");
    // Static field put -> void
    chain.putStatic("java.lang.System", "out", new Object());

    RpcChainResult result = chain.send();

    // Requests were generated in order and contain expected methods
    List<String> methods = seen.stream().map(JsonRpcRequest::getMethod).toList();
    assertThat(methods, contains("new", "call", "call", "call", "get", "put"));

    // Result maps contain stored values for varNames used
    assertThat(result.getAllValues().size() >= 3, is(true));
    assertThat(result.getValue("len"), is(7));
    assertThat(result.getValue("max"), is(2147483647));

    // Using with(varName) after send should not throw (class name stored)
    RpcChainInstance after = chain.with("s");
    assertNotNull(after);
    // And we can use a new chain with directRef path for instance get/put
    ObjectRef ref = chain.getChainResult().getRef("s");
    assertNotNull(ref);
    RpcChain chain2 = new RpcChain(peer);
    RpcChainInstance inst2 = new RpcChainInstance(chain2, ref, "s", "java.lang.String");
    inst2.get("value", "v");
    inst2.put("value", new Object());
    RpcChainResult r2 = chain2.send();
    assertThat(r2.getAllValues().size() >= 1, is(true));
  }

  @Test
  public void resolveArgument_unknownInstanceVar_throws() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    // Return a benign void response for anything; we won't reach send in this test
    doReturn(CompletableFuture.completedFuture(voidResponse("x")))
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    // Build an op that references a var that will not be created
    RpcChainInstance phantom = new RpcChainInstance(chain, null, "nope", "java.lang.Object");
    chain.callStatic("java.lang.Integer", "valueOf", new Object[] {phantom});

    assertThrows(IllegalStateException.class, chain::send);
  }
}
