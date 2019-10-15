package com.ittera.cometa.core.messages;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class OutboundMsgTest extends ZmqEnabledTest {
  private MessageBuilder messageBuilder = new ProtobufMessageBuilder();

  @Test
  public void send() {
    UUID execMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = null;
    byte[] body = "whatever".getBytes();
    List<InternalHeader> headers = null;

    // with null headers and followingUuid
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.ExecMessage, headers, execMessageUuid, followingMessageUuid, body);

    // send
    String socketAddr = "inproc://here";
    assertThat(msg.isEmpty(), is(false));
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msg.send(out);

    // verify destroyed
    assertThat(msg.isEmpty(), is(true));

    out.close();
    zContext.destroy();
  }

  @Test
  public void buildWithNullables() {
    UUID execMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = null;
    byte[] body = "whatever".getBytes();
    List<InternalHeader> headers = null;

    // with null headers and followingUuid
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.ExecMessage, headers, execMessageUuid, followingMessageUuid, body);

    // verify # of frames
    assertThat(msg.size(), is(5));

    // verify getters
    assertThat(msg.getMessageType(), is(MessageType.ExecMessage));
    assertThat(msg.getHeaders(), is(empty()));
    assertThat(msg.getMessageUuid(), is(execMessageUuid));
    assertThat(msg.getFollowingUuid(), is(nullValue()));
    assertThat(msg.getBody(), is(body));
  }

  @Test
  public void build() {
    UUID interceptMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    // with all values filled
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.InterceptRequest,
            headers,
            interceptMessageUuid,
            followingMessageUuid,
            body);

    // verify # of frames
    assertThat(msg.size(), is(5 + headers.size()));

    // verify getters
    assertThat(msg.getMessageType(), is(MessageType.InterceptRequest));
    assertThat(msg.getHeaders().size(), is(1));
    assertThat(msg.getHeaders().get(0), is(writeAhead));
    assertThat(msg.getMessageUuid(), is(interceptMessageUuid));
    assertThat(msg.getFollowingUuid(), is(followingMessageUuid));
    assertThat(msg.getBody(), is(body));
  }

  @Test
  public void from() throws InvalidProtocolBufferException {
    UUID execMessageUuid = UUID.randomUUID();
    UUID followingMessageUuid = UUID.randomUUID();
    byte[] body = "whatever".getBytes();
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    OutboundMsg msg1 =
        new OutboundMsg(
            MessageType.ExecMessage, headers, execMessageUuid, followingMessageUuid, body);
    // construct from inner (duplicate)
    OutboundMsg msg2 = OutboundMsg.from(msg1.getInner());

    // verify equal contents
    assertTrue(msg2 != msg1);
    assertThat(msg2, is(msg1));

    // clean up
    msg1.destroy();
    msg2.destroy();
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
            MessageType.InterceptRequest, headers, messageUuid, followingMessageUuid, body);

    // assert content-only equality
    assertThat(
        new OutboundMsg(
            MessageType.InterceptRequest, headers, messageUuid, followingMessageUuid, body),
        is(msg1));

    // different type
    assertThat(
        new OutboundMsg(MessageType.ExecMessage, headers, messageUuid, UUID.randomUUID(), body),
        is(not(msg1)));

    // different headers
    List<InternalHeader> otherHeaders =
        Arrays.asList(writeAhead, writeAhead); // just duplicate the header
    assertThat(
        new OutboundMsg(
            MessageType.InterceptRequest, otherHeaders, messageUuid, UUID.randomUUID(), body),
        is(not(msg1)));

    // different message UUID
    assertThat(
        new OutboundMsg(
            MessageType.InterceptRequest, headers, UUID.randomUUID(), followingMessageUuid, body),
        is(not(msg1)));

    // different followingUuid
    assertThat(
        new OutboundMsg(
            MessageType.InterceptRequest, headers, messageUuid, UUID.randomUUID(), body),
        is(not(msg1)));

    // different body
    body = "whatevah".getBytes();
    assertThat(
        new OutboundMsg(
            MessageType.InterceptRequest, headers, messageUuid, UUID.randomUUID(), body),
        is(not(msg1)));
  }
}
