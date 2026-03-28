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
package io.quasient.pal.serdes.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
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
    JsonRpcError err = new JsonRpcError();
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
