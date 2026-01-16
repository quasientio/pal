/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class OutboundMsgAdditionalTest {

  private static ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  @Test
  public void toString_showsUnknownSize_beforeSend_thenSizeAfterSend() {
    String id = UUID.randomUUID().toString();
    byte[] body = "abc".getBytes(UTF_8);
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.EXEC_INSTANCE_METHOD, ExecPhase.BEFORE, /*headers*/ null, id, null, body);

    String s = msg.toString();
    assertThat(s, containsString("size=<unknown>"));

    // Send with flags (non-blocking path), then check size known
    ZContext ctx = createContext();
    String ep = "inproc://flags." + UUID.randomUUID();
    ZMQ.Socket rep = ctx.createSocket(SocketType.REP);
    rep.bind(ep);
    ZMQ.Socket req = ctx.createSocket(SocketType.REQ);
    req.connect(ep);

    boolean ok = msg.send(req, ZMQ.DONTWAIT);
    assertThat(ok, is(true));
    // Drain
    OutboundMsg.receive(rep, true);

    String s2 = msg.toString();
    assertThat(s2, is(not(containsString("size=<unknown>"))));

    req.close();
    rep.close();
    ctx.destroy();
  }

  @Test
  public void sendWithFlags_throwsOnNullSocket_andNullExecPhase() {
    String id = UUID.randomUUID().toString();
    byte[] body = "xyz".getBytes(UTF_8);
    List<InternalHeader> headers = Collections.emptyList();
    OutboundMsg msg =
        new OutboundMsg(MessageType.EXEC_GET_FIELD, ExecPhase.BEFORE, headers, id, null, body);

    assertThrows(IllegalArgumentException.class, () -> msg.send((ZMQ.Socket) null, 0));

    // Build message with null execPhase to trigger NPE inside send(flags)
    OutboundMsg msgNoPhase =
        new OutboundMsg(MessageType.EXEC_GET_FIELD, /*execPhase*/ null, headers, id, null, body);
    assertThrows(
        NullPointerException.class,
        () -> msgNoPhase.send(createContext().createSocket(SocketType.REQ), 0));
  }

  @Test
  public void receiveNonBlocking_returnsNull_whenNoFramesAvailable() {
    ZContext ctx = createContext();
    String ep = "inproc://nb." + UUID.randomUUID();
    ZMQ.Socket rep = ctx.createSocket(SocketType.REP);
    rep.bind(ep);
    // No messages sent, so non-blocking receive returns null
    OutboundMsg none = OutboundMsg.receive(rep);
    assertThat(none == null, is(true));
    rep.close();
    ctx.destroy();
  }
}
