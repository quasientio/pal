/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import static org.junit.Assert.assertThrows;

import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MetaServiceType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;
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
            org.mockito.Mockito.mock(IncomingMessageDispatcher.class),
            org.mockito.Mockito.mock(
                com.quasient.pal.core.transport.gateway.OutboundMessageGateway.class),
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
