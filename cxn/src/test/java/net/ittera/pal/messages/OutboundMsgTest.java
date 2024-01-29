/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.messages;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** TODO: call constructor with marshallable instead of body (byte[]) */
public class OutboundMsgTest {
  private final MessageBuilder messageBuilder = new MessageBuilder();
  private static final Logger logger = LoggerFactory.getLogger("tests");

  protected ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  @Test
  public void sendWithNullables() {
    UUID execMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = null;
    byte[] body = "whatever".getBytes();
    List<InternalHeader> headers = null;
    ExecPhase execPhase = ExecPhase.BEFORE;

    // with null headers and responseToUuid
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            execPhase,
            headers,
            execMessageUuid,
            followingMessageUuid,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.EXEC_MESSAGE));
    assertThat(msgOut.getExecPhase(), is(execPhase));
    assertThat(msgOut.getHeaders(), is(nullValue()));
    assertThat(msgOut.getMessageUuid(), is(execMessageUuid));
    assertThat(msgOut.getResponseToUuid(), is(nullValue()));
    assertThat(msgOut.getBody(), is(body));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msgOut.send(out);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.recvMsg(in, true);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void send() {
    UUID interceptMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    // with all values filled
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.UNDEFINED,
            headers,
            interceptMessageUuid,
            followingMessageUuid,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.INTERCEPT_MESSAGE));
    assertThat(msgOut.getExecPhase(), is(ExecPhase.UNDEFINED));
    assertThat(msgOut.getHeaders().size(), is(1));
    assertThat(msgOut.getHeaders().get(0), is(writeAhead));
    assertThat(msgOut.getMessageUuid(), is(interceptMessageUuid));
    assertThat(msgOut.getResponseToUuid(), is(followingMessageUuid));
    assertThat(msgOut.getBody(), is(body));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void testNPE() {
    UUID messageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    List<InternalHeader> headers = Collections.emptyList();

    // null messageType
    try {
      new OutboundMsg(null, ExecPhase.UNDEFINED, headers, messageUuid, UUID.randomUUID(), body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null execPhase
    try {
      new OutboundMsg(
          MessageType.INTERCEPT_MESSAGE, null, headers, messageUuid, UUID.randomUUID(), body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null messageUuid
    try {
      new OutboundMsg(
          MessageType.INTERCEPT_MESSAGE,
          ExecPhase.UNDEFINED,
          headers,
          null,
          UUID.randomUUID(),
          body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null body
    try {
      new OutboundMsg(
          MessageType.INTERCEPT_MESSAGE,
          ExecPhase.UNDEFINED,
          headers,
          messageUuid,
          UUID.randomUUID(),
          (byte[]) null);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    UUID messageUuid = UUID.randomUUID();
    UUID followingMessageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    OutboundMsg msg1 =
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            body);

    // equal
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            body),
        is(msg1));

    // different type
    assertThat(
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            body),
        is(not(msg1)));

    // different headers
    List<InternalHeader> otherHeaders =
        Arrays.asList(writeAhead, writeAhead); // just duplicate the header
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            otherHeaders,
            messageUuid,
            followingMessageUuid,
            body),
        is(not(msg1)));

    // different message UUID
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            UUID.randomUUID(),
            followingMessageUuid,
            body),
        is(not(msg1)));

    // different responseToUuid
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            UUID.randomUUID(),
            body),
        is(not(msg1)));

    // different body
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            "whatevah".getBytes()),
        is(not(msg1)));
  }
}
