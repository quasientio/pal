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
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Unit tests for dispatch counters in AbstractMessageInvokerThread across EXEC/CONTROL/META,
 * exercising both OK and DISPATCH_ERROR paths using a lightweight subclass without threads.
 */
public class AbstractMessageInvokerCountersTest {

  private UUID peerUuid;
  private ZContext ctx;
  private MessageBuilder builder;
  private IncomingMessageDispatcher dispatcher;

  private static class TestInvoker extends AbstractMessageInvokerThread {
    TestInvoker(
        ZContext zmqContext,
        MessageBuilder messageBuilder,
        IncomingMessageDispatcher incomingMessageDispatcher,
        UUID peerUuid) {
      super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    }

    @Override
    public void run() {}

    public Message dispatchMsg(Message m) {
      return dispatch(m, MessageChannelType.ZMQ_SOCKET_RPC);
    }
  }

  @Before
  public void setup() {
    peerUuid = UUID.randomUUID();
    ctx = new ZContext(1);
    builder = new MessageBuilder(peerUuid);
    dispatcher = mock(IncomingMessageDispatcher.class);
  }

  @Test
  public void controlAndMeta_okAndErrorCounters() throws Exception {
    // Prepare control OK path
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Prepare meta OK path
    MetaMessage metaReq =
        builder.buildMetaMessageRequest(
            peerUuid, ctrlReq.getMessageId(), MetaServiceType.FETCH_CLASSES_INFO);
    MetaMessage metaResp =
        builder.buildMetaMessageResponse(
            peerUuid,
            MetaServiceType.FETCH_CLASSES_INFO,
            MetaStatusType.OK,
            null,
            metaReq.getMessageId());
    when(dispatcher.incomingMetaMessage(any(MetaMessage.class))).thenReturn(metaResp);

    TestInvoker inv = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Dispatch control OK
    inv.dispatchMsg(builder.wrap(ctrlReq));
    assertThat(inv.getControlRequestsDispatched(), is(1L));
    assertThat(inv.getControlRequestErrors(), is(0L));

    // Dispatch meta OK
    inv.dispatchMsg(builder.wrap(metaReq));
    assertThat(inv.getMetaRequestsDispatched(), is(1L));
    assertThat(inv.getMetaRequestErrors(), is(0L));

    // Now force errors
    doThrow(new RuntimeException("boom"))
        .when(dispatcher)
        .incomingControlMessage(any(ControlMessage.class));
    doThrow(new RuntimeException("boom2"))
        .when(dispatcher)
        .incomingMetaMessage(any(MetaMessage.class));

    assertThrows(RuntimeException.class, () -> inv.dispatchMsg(builder.wrap(ctrlReq)));
    assertThrows(RuntimeException.class, () -> inv.dispatchMsg(builder.wrap(metaReq)));

    assertThat(inv.getControlRequestErrors(), is(1L));
    assertThat(inv.getMetaRequestErrors(), is(1L));
  }
}
