/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

public class SocketRpcInvokerHandleExceptionTest {

  private ZContext ctx;

  @Before
  public void setUp() {
    ctx = new ZContext(1);
  }

  @After
  public void tearDown() {
    ctx.close();
  }

  @Test
  public void handleSocketException_eterm_eintr_returnsTrue() throws Exception {
    SocketRpcInvoker invoker =
        new SocketRpcInvoker(
            ctx,
            new MessageBuilder(),
            Collections.emptySet(),
            "inproc://rpc",
            "inproc://json",
            mock(IncomingMessageDispatcher.class),
            UUID.randomUUID());

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod("handleSocketException", ZMQException.class);
    m.setAccessible(true);
    boolean eterm = (boolean) m.invoke(invoker, new ZMQException("x", ZError.ETERM));
    boolean eintr = (boolean) m.invoke(invoker, new ZMQException("x", ZError.EINTR));
    assertThat(eterm, is(true));
    assertThat(eintr, is(true));
  }

  @Test(expected = ZMQException.class)
  public void handleSocketException_other_throws() throws Throwable {
    SocketRpcInvoker invoker =
        new SocketRpcInvoker(
            ctx,
            new MessageBuilder(),
            Collections.emptySet(),
            "inproc://rpc",
            "inproc://json",
            mock(IncomingMessageDispatcher.class),
            UUID.randomUUID());

    Method m =
        SocketRpcInvoker.class.getDeclaredMethod("handleSocketException", ZMQException.class);
    m.setAccessible(true);
    try {
      m.invoke(invoker, new ZMQException("x", ZError.EFAULT));
    } catch (java.lang.reflect.InvocationTargetException ite) {
      throw ite.getCause();
    }
  }
}
