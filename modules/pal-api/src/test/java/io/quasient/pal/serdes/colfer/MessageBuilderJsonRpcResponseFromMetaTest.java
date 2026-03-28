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
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import java.util.UUID;
import org.junit.Test;

public class MessageBuilderJsonRpcResponseFromMetaTest {

  private final MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));

  private static MetaMessage metaResponse(MetaStatusType status, String responseToId, String body) {
    return new MetaMessage()
        .withFromPeer(UUID.randomUUID().toString())
        .withMessageId(UUID.randomUUID().toString())
        .withResponseToId(responseToId)
        .withService(MetaServiceType.FETCH_CLASSES_INFO.getId())
        .withStatus(status.getId())
        .withBody(body);
  }

  @Test
  public void meta_OK_mapsTo_resultWithBody() {
    String rid = UUID.randomUUID().toString();
    String body = "some-json";
    MetaMessage mm = metaResponse(MetaStatusType.OK, rid, body);

    JsonRpcResponse r = b.jsonRpcResponseFromMetaMessageResponse(mm);
    assertEquals(rid, r.getId());
    JsonRpcResponseReturnValue rv = r.getResult();
    assertNotNull(rv);
    ResponseObject v = rv.getValue();
    assertNotNull(v);
    assertEquals(body, v.getValue());
  }

  @Test
  public void meta_UNSUPPORTED_mapsTo_METHOD_NOT_FOUND() {
    MetaMessage mm = metaResponse(MetaStatusType.UNSUPPORTED, "1", null);
    JsonRpcError err = b.jsonRpcResponseFromMetaMessageResponse(mm).getError();
    assertNotNull(err);
    assertThat(err.getCode(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getCode()));
    assertThat(err.getMessage(), is(JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));
  }

  @Test
  public void meta_ERROR_mapsTo_SERVER_ERROR_withDataMessage() {
    String body = "bad things";
    MetaMessage mm = metaResponse(MetaStatusType.ERROR, "1", body);
    JsonRpcError err = b.jsonRpcResponseFromMetaMessageResponse(mm).getError();
    assertNotNull(err);
    assertThat(err.getCode(), is(JsonRpcErrorCode.SERVER_ERROR.getCode()));
    assertThat(err.getMessage(), is(JsonRpcErrorCode.SERVER_ERROR.getMessage()));
    assertEquals(body, err.getData().getMessage());
  }

  @Test
  public void meta_UNAUTHORIZED_throws_IllegalArgumentException() {
    MetaMessage mm = metaResponse(MetaStatusType.UNAUTHORIZED, "1", null);
    assertThrows(
        IllegalArgumentException.class, () -> b.jsonRpcResponseFromMetaMessageResponse(mm));
  }
}
