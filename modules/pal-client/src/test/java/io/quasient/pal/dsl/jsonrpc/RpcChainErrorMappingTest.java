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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainErrorMappingTest {

  private static JsonRpcResponse errorResponse(String id, int code, String message) {
    JsonRpcError err = new JsonRpcError();
    err.setCode(code);
    err.setMessage(message);
    return JsonRpcResponse.builder().withId(id).withError(err).build();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void errorIsMappedAndReflectedInValues_thenThrows() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    final String[] capturedId = new String[1];
    doAnswer(
            inv -> {
              JsonRpcRequest req = inv.getArgument(0);
              capturedId[0] = req.getId();
              return CompletableFuture.completedFuture(errorResponse(req.getId(), -32000, "bad"));
            })
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    chain.callStatic("java.lang.Integer", "parseInt", new Object[] {"1"});

    // Ensure an exception is thrown when sending the chain due to error response
    assertThrows(RuntimeException.class, chain::send);

    // requestIdToError should contain one entry for the captured id
    Field f = RpcChain.class.getDeclaredField("requestIdToError");
    f.setAccessible(true);
    Map<String, Object> errMap = (Map<String, Object>) f.get(chain);
    assertThat(errMap.containsKey(capturedId[0]), is(true));

    // Reflectively invoke getResponseValues to ensure the error is present in the list
    Method m = RpcChain.class.getDeclaredMethod("getResponseValues");
    m.setAccessible(true);
    List<Map<String, Object>> values = (List<Map<String, Object>>) m.invoke(chain);
    assertThat(values.size(), is(1));
    Map<String, Object> row = values.get(0);
    assertThat(row.get("requestId"), is(capturedId[0]));
    assertThat(row.get("error") != null, is(true));
  }
}
