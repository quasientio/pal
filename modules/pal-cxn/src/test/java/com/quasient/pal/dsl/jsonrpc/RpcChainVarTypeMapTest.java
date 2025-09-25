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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
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
