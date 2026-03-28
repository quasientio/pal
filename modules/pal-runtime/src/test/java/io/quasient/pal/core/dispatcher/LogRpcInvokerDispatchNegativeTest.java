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

import static org.junit.Assert.assertThrows;

import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.Mockito;
import org.zeromq.ZContext;

public class LogRpcInvokerDispatchNegativeTest {

  @Test
  public void dispatch_throwsOnNonExecMessage() throws Exception {
    final ZContext ctx;
    try {
      ctx = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to ZMQ sandbox", t);
      return;
    }
    MessageBuilder mb = new MessageBuilder();
    LogRpcInvoker inv =
        new LogRpcInvoker(
            new ThreadGroup("svc"),
            "log-inv",
            ctx,
            mb,
            "inproc://dealer",
            Mockito.mock(IncomingMessageDispatcher.class),
            Mockito.mock(OutboundMessageGateway.class),
            UUID.randomUUID());

    // Build a non-exec Message (Meta request)
    var meta =
        mb.buildMetaMessageRequest(UUID.randomUUID(), "mid", MetaServiceType.FETCH_CLASSES_INFO);
    Message m = mb.wrap(meta);

    Method dispatch = LogRpcInvoker.class.getDeclaredMethod("dispatch", Message.class, Long.class);
    dispatch.setAccessible(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          try {
            dispatch.invoke(inv, m, 0L);
          } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getCause();
          }
        });

    ctx.close();
  }
}
