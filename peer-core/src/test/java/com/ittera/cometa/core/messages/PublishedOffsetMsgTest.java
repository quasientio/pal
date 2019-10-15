package com.ittera.cometa.core.messages;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.ZmqEnabledTest;
import java.util.UUID;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class PublishedOffsetMsgTest extends ZmqEnabledTest {

  @Test
  public void send() {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msg = new PublishedOffsetMsg(offset, messageUuid);

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
  public void build() {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msg = new PublishedOffsetMsg(offset, messageUuid);

    // verify # of frames
    assertThat(msg.size(), is(2));

    // verify getters
    assertThat(msg.getOffset(), is(offset));
    assertThat(msg.getMessageUuid(), is(messageUuid));
  }

  @Test
  public void from() throws InvalidProtocolBufferException {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageUuid);
    // construct from inner (duplicate)
    PublishedOffsetMsg msg2 = PublishedOffsetMsg.from(msg1.getInner());

    // verify equal contents
    assertTrue(msg2 != msg1);
    assertThat(msg2, is(msg1));

    // clean up
    msg1.destroy();
    msg2.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageUuid);

    // assert content equality
    assertThat(new PublishedOffsetMsg(offset, messageUuid), is(msg1));

    // different offset
    assertThat(new PublishedOffsetMsg(offset + 1, messageUuid), is(not(msg1)));

    // different messageUuid
    assertThat(new PublishedOffsetMsg(offset, UUID.randomUUID()), is(not(msg1)));
  }
}
