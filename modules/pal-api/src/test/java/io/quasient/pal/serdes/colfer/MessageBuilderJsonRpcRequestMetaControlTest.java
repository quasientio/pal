/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.common.objects.ObjectRef;
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
    assertEquals(peerId.toString(), m.getFromPeer());
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
    assertEquals(peerId.toString(), cm.getFromPeer());
    assertEquals(delObj.getId(), cm.getMessageId());
    assertThat(ControlMessageUtils.getMessageTypeOf(cm), is(MessageType.CONTROL_MESSAGE_REQUEST));

    // delete session
    JsonRpcRequest delSess = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();
    Message delSessMsg = builder.jsonRpcRequestToControlMessage(delSess, peerId);
    ControlMessage cm2 = delSessMsg.getControlMessage();
    assertNotNull(cm2);
    assertEquals(peerId.toString(), cm2.getFromPeer());
    assertEquals(delSess.getId(), cm2.getMessageId());
    assertThat(ControlMessageUtils.getMessageTypeOf(cm2), is(MessageType.CONTROL_MESSAGE_REQUEST));
  }
}
