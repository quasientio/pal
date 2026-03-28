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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class RpcChainVarTypeMapTest {

  @Test
  public void getClassForVarName_returnsStoredType() throws Exception {
    ThinPeer peer = mock(ThinPeer.class);
    // Return a constructor response with ref so var 's' is created and type stored from Params.type
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withRef(1)
            .withType("java.lang.String")
            .withValue("x")
            .build();
    JsonRpcResponse ok =
        JsonRpcResponse.builder()
            .withId("id-1")
            .withResult(
                JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build())
            .build();
    doReturn(CompletableFuture.completedFuture(ok))
        .when(peer)
        .sendJsonRpcRequestToPeer(any(JsonRpcRequest.class), any());

    RpcChain chain = new RpcChain(peer);
    chain.create("java.lang.String", "s", new Object[] {"abc"}).send();

    // Reflectively call protected getClassForVarName
    Method m = RpcChain.class.getDeclaredMethod("getClassForVarName", String.class);
    m.setAccessible(true);
    String clazz = (String) m.invoke(chain, "s");
    assertThat(clazz, is("java.lang.String"));
  }
}
