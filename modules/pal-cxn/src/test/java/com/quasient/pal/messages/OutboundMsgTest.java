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

package com.quasient.pal.messages;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// TODO: call constructor with marshallable instead of body (byte[])
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
    String execMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    ExecPhase execPhase = ExecPhase.BEFORE;

    // with null headers and responseToId
    OutboundMsg msgOut =
        new OutboundMsg(MessageType.EXEC_CONSTRUCTOR, execPhase, null, execMessageId, null, body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.EXEC_CONSTRUCTOR));
    assertThat(msgOut.getExecPhase(), is(execPhase));
    assertThat(msgOut.getHeaders(), is(nullValue()));
    assertThat(msgOut.getMessageId(), is(execMessageId));
    assertThat(msgOut.getResponseToId(), is(nullValue()));
    assertThat(msgOut.getBody(), is(body));

    // send
    String zmqEndpoint = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(zmqEndpoint);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(zmqEndpoint);
    msgOut.send(out);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void send() throws UnsupportedEncodingException {
    String interceptMessageId = UUID.randomUUID().toString();
    String followingMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    // with all values filled
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.UNDEFINED,
            headers,
            interceptMessageId,
            followingMessageId,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.INTERCEPT_MESSAGE));
    assertThat(msgOut.getExecPhase(), is(ExecPhase.UNDEFINED));
    assertThat(msgOut.getHeaders(), is(notNullValue()));
    assertThat(msgOut.getHeaders().size(), is(1));
    assertThat(msgOut.getHeaders().get(0), is(writeAhead));
    assertThat(msgOut.getMessageId(), is(interceptMessageId));
    assertThat(msgOut.getResponseToId(), is(followingMessageId));
    assertThat(msgOut.getBody(), is(body));

    // send
    String zmqEndpoint = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(zmqEndpoint);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(zmqEndpoint);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testNullPointerException() {
    String messageId = UUID.randomUUID().toString();
    String responseToId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    List<InternalHeader> headers = Collections.emptyList();

    // null messageType
    try {
      new OutboundMsg(null, ExecPhase.UNDEFINED, headers, messageId, responseToId, body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null execPhase
    try {
      new OutboundMsg(MessageType.INTERCEPT_MESSAGE, null, headers, messageId, responseToId, body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null messageId
    try {
      new OutboundMsg(
          MessageType.INTERCEPT_MESSAGE, ExecPhase.UNDEFINED, headers, null, responseToId, body);
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
          messageId,
          responseToId,
          (byte[]) null);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    String messageId = UUID.randomUUID().toString();
    String responseToMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    OutboundMsg msg1 =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body);

    // equal
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body),
        is(msg1));

    // different type
    assertThat(
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body),
        is(not(msg1)));

    // different headers
    List<InternalHeader> otherHeaders =
        Arrays.asList(writeAhead, writeAhead); // just duplicate the header
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            otherHeaders,
            messageId,
            responseToMessageId,
            body),
        is(not(msg1)));

    // different message ID
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            UUID.randomUUID().toString(),
            responseToMessageId,
            body),
        is(not(msg1)));

    // different responseToId
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            UUID.randomUUID().toString(),
            body),
        is(not(msg1)));

    // different body
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            "whatevah".getBytes(UTF_8)),
        is(not(msg1)));
  }
}
