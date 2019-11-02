package com.ittera.cometa.core.messages;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.core.exec.ExecPhase;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
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

public class OutboundMsgTest extends ZmqEnabledTest {
  private MessageBuilder messageBuilder = new ProtobufMessageBuilder();
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendWithNullables() throws InvalidProtocolBufferException {
    UUID execMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = null;
    byte[] body = "whatever".getBytes();
    List<InternalHeader> headers = null;
    ExecPhase execPhase = ExecPhase.BEFORE;

    // with null headers and followingUuid
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.ExecMessage,
            execPhase,
            headers,
            execMessageUuid,
            followingMessageUuid,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.ExecMessage));
    assertThat(msgOut.getExecPhase(), is(execPhase));
    assertThat(msgOut.getHeaders(), is(nullValue()));
    assertThat(msgOut.getMessageUuid(), is(execMessageUuid));
    assertThat(msgOut.getFollowingUuid(), is(nullValue()));
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
  public void send() throws InvalidProtocolBufferException {
    UUID interceptMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    // with all values filled
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.InterceptMessage,
            ExecPhase.UNDEFINED,
            headers,
            interceptMessageUuid,
            followingMessageUuid,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.InterceptMessage));
    assertThat(msgOut.getExecPhase(), is(ExecPhase.UNDEFINED));
    assertThat(msgOut.getHeaders().size(), is(1));
    assertThat(msgOut.getHeaders().get(0), is(writeAhead));
    assertThat(msgOut.getMessageUuid(), is(interceptMessageUuid));
    assertThat(msgOut.getFollowingUuid(), is(followingMessageUuid));
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
          MessageType.InterceptMessage, null, headers, messageUuid, UUID.randomUUID(), body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null messageUuid
    try {
      new OutboundMsg(
          MessageType.InterceptMessage,
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
          MessageType.InterceptMessage,
          ExecPhase.UNDEFINED,
          headers,
          messageUuid,
          UUID.randomUUID(),
          null);
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
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            body);

    // equal
    assertThat(
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            body),
        is(msg1));

    // different type
    assertThat(
        new OutboundMsg(
            MessageType.InterceptMessage,
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
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            otherHeaders,
            messageUuid,
            followingMessageUuid,
            body),
        is(not(msg1)));

    // different message UUID
    assertThat(
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            UUID.randomUUID(),
            followingMessageUuid,
            body),
        is(not(msg1)));

    // different followingUuid
    assertThat(
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            UUID.randomUUID(),
            body),
        is(not(msg1)));

    // different body
    assertThat(
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            messageUuid,
            followingMessageUuid,
            "whatevah".getBytes()),
        is(not(msg1)));
  }
}
