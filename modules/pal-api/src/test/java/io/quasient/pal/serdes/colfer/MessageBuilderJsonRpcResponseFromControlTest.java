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

import static io.quasient.pal.messages.types.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.quasient.pal.messages.types.JsonRpcErrorCode.SERVER_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import io.quasient.pal.messages.types.ControlStatusType;
import java.util.UUID;
import org.junit.Test;

public class MessageBuilderJsonRpcResponseFromControlTest {

  private static ControlMessage controlResponse(
      ControlStatusType status, String responseToId, String body) {
    return new ControlMessage()
        .withFromPeer(UuidUtils.toBytes(UUID.randomUUID()))
        .withMessageId(UUID.randomUUID().toString())
        .withResponseToId(responseToId)
        .withStatus(status.toId())
        .withBody(body);
  }

  @Test
  public void control_OK_emptyBody_returnsVoidResult() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = UUID.randomUUID().toString();
    ControlMessage cm = controlResponse(ControlStatusType.OK, rid, "");

    JsonRpcResponse r = b.jsonRpcResponseFromControlMessageResponse(cm);
    assertEquals(rid, r.getId());
    JsonRpcResponseReturnValue rv = r.getResult();
    assertNotNull(rv);
    assertTrue(rv.getIsVoid());
    assertNull(r.getError());
  }

  @Test
  public void control_OK_body_returnsResultWithBody() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    String rid = UUID.randomUUID().toString();
    String body = "done";
    ControlMessage cm = controlResponse(ControlStatusType.OK, rid, body);

    JsonRpcResponse r = b.jsonRpcResponseFromControlMessageResponse(cm);
    assertEquals(rid, r.getId());
    assertNotNull(r.getResult());
    ResponseObject v = r.getResult().getValue();
    assertNotNull(v);
    assertEquals(body, v.getValue());
  }

  @Test
  public void control_UNSUPPORTED_mapsTo_METHOD_NOT_FOUND() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    ControlMessage cm = controlResponse(ControlStatusType.UNSUPPORTED, "1", null);

    JsonRpcError err = b.jsonRpcResponseFromControlMessageResponse(cm).getError();
    assertNotNull(err);
    assertEquals(METHOD_NOT_FOUND.getCode(), err.getCode());
    assertEquals(METHOD_NOT_FOUND.getMessage(), err.getMessage());
  }

  @Test
  public void control_NO_SUCH_SESSION_mapsTo_SERVER_ERROR_withData() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    ControlMessage cm = controlResponse(ControlStatusType.NO_SUCH_SESSION, "1", null);

    JsonRpcError err = b.jsonRpcResponseFromControlMessageResponse(cm).getError();
    assertNotNull(err);
    assertEquals(SERVER_ERROR.getCode(), err.getCode());
    assertEquals(SERVER_ERROR.getMessage(), err.getMessage());
    assertThat(err.getData().getMessage(), is("No such session"));
  }

  @Test
  public void control_NO_SUCH_OBJECT_mapsTo_SERVER_ERROR_withData() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    ControlMessage cm = controlResponse(ControlStatusType.NO_SUCH_OBJECT, "1", null);

    JsonRpcError err = b.jsonRpcResponseFromControlMessageResponse(cm).getError();
    assertNotNull(err);
    assertEquals(SERVER_ERROR.getCode(), err.getCode());
    assertEquals(SERVER_ERROR.getMessage(), err.getMessage());
    assertThat(err.getData().getMessage(), is("No such object"));
  }

  @Test
  public void control_ERROR_nullBody_setsServerErrorWithNullDataMessage() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    ControlMessage cm = controlResponse(ControlStatusType.ERROR, "1", null);

    JsonRpcError err = b.jsonRpcResponseFromControlMessageResponse(cm).getError();
    assertNotNull(err);
    assertEquals(SERVER_ERROR.getCode(), err.getCode());
    assertEquals(SERVER_ERROR.getMessage(), err.getMessage());
    assertNotNull(err.getData());
    assertNull(err.getData().getMessage());
  }

  @Test
  public void control_wrongMessageType_throwsIAE() {
    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    ControlMessage requestLike =
        new ControlMessage()
            .withFromPeer(UuidUtils.toBytes(UUID.randomUUID()))
            .withMessageId("req-1")
            .withStatus((byte) 0); // request type per ControlMessageUtils

    assertThrows(
        IllegalArgumentException.class,
        () -> b.jsonRpcResponseFromControlMessageResponse(requestLike));
  }
}
