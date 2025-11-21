/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
import org.junit.Test;

public class JsonRpcSerializerTest {

  @Test
  public void request_toJson_fromJson_roundTrip() throws Exception {
    JsonRpcRequest req =
        JsonRpcRequest.builder()
            .withId("1")
            .withMethod("call")
            .withParams(
                Params.builder().withType("java.lang.Integer").withMethod("valueOf").build())
            .build();

    String json = JsonRpcSerializer.toJson(req);
    assertThat(json, containsString("\"jsonrpc\""));

    JsonRpcRequest parsed = JsonRpcSerializer.fromJson(json, JsonRpcRequest.class);
    assertThat(parsed, is(req));

    String pretty = JsonRpcSerializer.toPrettyJson(req);
    assertThat(pretty, containsString("\n"));
  }

  @Test
  public void response_toJson_fromJson_roundTrip_result() throws Exception {
    ResponseObject ro =
        ResponseObject.builder()
            .withIsNull(false)
            .withValue("7")
            .withType("java.lang.Integer")
            .build();
    JsonRpcResponse resp =
        JsonRpcResponse.builder()
            .withId("x")
            .withResult(
                JsonRpcResponseReturnValue.builder().withIsVoid(false).withValue(ro).build())
            .build();

    String json = JsonRpcSerializer.toJson(resp);
    JsonRpcResponse parsed = JsonRpcSerializer.fromJson(json, JsonRpcResponse.class);
    assertThat(parsed, is(resp));
  }

  @Test
  public void response_toJson_fromJson_roundTrip_error() throws Exception {
    com.quasient.pal.messages.jsonrpc.JsonRpcError err =
        new com.quasient.pal.messages.jsonrpc.JsonRpcError();
    err.setCode(-32000);
    err.setMessage("bad");
    JsonRpcResponse resp = JsonRpcResponse.builder().withId("y").withError(err).build();

    String json = JsonRpcSerializer.toJson(resp);
    JsonRpcResponse parsed = JsonRpcSerializer.fromJson(json, JsonRpcResponse.class);
    assertThat(parsed, is(resp));
  }

  @Test
  public void toJson_nullMessage_serializesAsNull() throws Exception {
    String json = JsonRpcSerializer.toJson(null);
    assertThat(json, is("null"));
  }

  @Test
  public void fromJson_throwsOnInvalid() {
    assertThrows(
        JsonSerializationException.class,
        () -> JsonRpcSerializer.fromJson("{not-valid-json:", JsonRpcRequest.class));
  }
}
