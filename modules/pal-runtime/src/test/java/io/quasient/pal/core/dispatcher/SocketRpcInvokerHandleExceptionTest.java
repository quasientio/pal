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
