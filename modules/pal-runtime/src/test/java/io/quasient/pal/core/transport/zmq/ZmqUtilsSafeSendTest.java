/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.zmq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Assume;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ZmqUtilsSafeSendTest {

  @Test
  public void nullSocketOrMessage_returnsFalse() {
    assertThat(ZmqUtils.safeSend((ZMQ.Socket) null, "msg"), is(false));
    assertThat(ZmqUtils.safeSend((ZMQ.Socket) null, (byte[]) null), is(false));
  }

  @Test
  public void closedContext_sendReturnsFalse_doesNotThrow() {
    final ZContext ctx;
    try {
      ctx = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to ZMQ sandbox", t);
      return;
    }
    ZMQ.Socket s = ctx.createSocket(SocketType.REQ);
    // Close context to trigger ETERM on subsequent socket operations
    ctx.close();
    boolean ok1 = ZmqUtils.safeSend(s, "hello");
    boolean ok2 = ZmqUtils.safeSend(s, new byte[] {1, 2, 3});
    assertThat(ok1, is(false));
    assertThat(ok2, is(false));
  }
}
