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

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class MessageBuilderJsonRpcRequestMetaControlTest {

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder builder;

  @Before
  public void setUp() {
    builder = new MessageBuilder(peerId);
  }

  @Test
  public void jsonRpc_toMetaMessage_fetchClassesInfo_mapsToMetaRequest() {
    String[] include = new String[] {"java.lang.String"};
    String[] exclude = new String[] {"sun."};
    JsonRpcRequest req =
        JsonRpcMessageFactory.buildFetchClassesInfoMetaMessage(include, exclude, true, false);

    Message msg = builder.jsonRpcRequestToMetaMessage(req, peerId);
    assertNotNull(msg);

    MetaMessage m = msg.getMetaMessage();
    assertNotNull(m);
    assertEquals(peerId.toString(), UuidUtils.toString(m.getFromPeer()));
    assertEquals(req.getId(), m.getMessageId());
    assertThat(MetaMessageUtils.getMessageTypeOf(m), is(MessageType.META_MESSAGE_REQUEST));
  }

  @Test
  public void jsonRpc_toControlMessage_deleteObject_and_deleteSession() {
    // delete object
    ObjectRef ref = ObjectRef.randomRef();
    JsonRpcRequest delObj = JsonRpcMessageFactory.buildDeleteObjectCommandMessage(ref);
    Message delObjMsg = builder.jsonRpcRequestToControlMessage(delObj, peerId);
    ControlMessage cm = delObjMsg.getControlMessage();
    assertNotNull(cm);
    assertEquals(peerId.toString(), UuidUtils.toString(cm.getFromPeer()));
    assertEquals(delObj.getId(), cm.getMessageId());
    assertThat(ControlMessageUtils.getMessageTypeOf(cm), is(MessageType.CONTROL_MESSAGE_REQUEST));

    // delete session
    JsonRpcRequest delSess = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();
    Message delSessMsg = builder.jsonRpcRequestToControlMessage(delSess, peerId);
    ControlMessage cm2 = delSessMsg.getControlMessage();
    assertNotNull(cm2);
    assertEquals(peerId.toString(), UuidUtils.toString(cm2.getFromPeer()));
    assertEquals(delSess.getId(), cm2.getMessageId());
    assertThat(ControlMessageUtils.getMessageTypeOf(cm2), is(MessageType.CONTROL_MESSAGE_REQUEST));
  }
}
