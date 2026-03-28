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
