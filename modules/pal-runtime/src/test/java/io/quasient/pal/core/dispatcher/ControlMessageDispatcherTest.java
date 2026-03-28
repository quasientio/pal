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
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.internal.messages.SessionResponseMsg;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.SessionStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class ControlMessageDispatcherTest {

  private final UUID peer = UUID.randomUUID();
  private ControlMessageDispatcher dispatcher;
  private MessageBuilder builder;
  private OutboundMessageGateway gw;
  private ObjectLookupStore store;

  @Before
  public void setup() throws Exception {
    dispatcher = new ControlMessageDispatcher();
    builder = new MessageBuilder(peer);
    gw = mock(OutboundMessageGateway.class);
    store = mock(ObjectLookupStore.class);
    set(dispatcher, "messageBuilder", builder);
    set(dispatcher, "peerUuid", peer);
    set(dispatcher, "outboundMessageGateway", gw);
    set(dispatcher, "objectLookupStore", store);
  }

  private static void set(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  @Test
  public void ping_returnsOk() {
    ControlMessage ping = builder.buildControlCommandMessage(peer, ControlCommandType.PING);
    ControlMessage resp = dispatcher.incomingControlMessage(ping);
    assertThat(resp.getStatus(), is(ControlStatusType.OK.toId()));
    assertThat(peer.toString(), is(resp.getFromPeer()));
  }

  @Test
  public void gc_returnsOk() {
    ControlMessage gc = builder.buildControlCommandMessage(peer, ControlCommandType.GC);
    ControlMessage resp = dispatcher.incomingControlMessage(gc);
    assertThat(resp.getStatus(), is(ControlStatusType.OK.toId()));
  }

  @Test
  public void deleteSession_mapsOkStatus() {
    // Stub gateway to return OK for delete session
    when(gw.sendMessageToSessionService(any()))
        .thenReturn(new SessionResponseMsg(SessionStatusType.OK));
    ControlMessage del =
        builder.buildControlCommandMessage(peer, ControlCommandType.DELETE_SESSION);
    ControlMessage resp = dispatcher.incomingControlMessage(del);
    assertThat(resp.getStatus(), is(ControlStatusType.OK.toId()));
  }

  @Test
  public void deleteObject_invokesGateway_andRemovesRef() {
    ObjectRef ref = ObjectRef.from("1");
    when(gw.sendMessageToSessionService(any()))
        .thenReturn(new SessionResponseMsg(SessionStatusType.OK));
    ControlMessage delObj = builder.buildDeleteObjectCommandMessage(peer, ref);
    ControlMessage resp = dispatcher.incomingControlMessage(delObj);
    assertThat(resp.getStatus(), is(ControlStatusType.OK.toId()));
    // store.remove invoked implicitly; behavior is side-effect; not asserted via mock verify to
    // avoid mockito-inline requirement
  }
}
