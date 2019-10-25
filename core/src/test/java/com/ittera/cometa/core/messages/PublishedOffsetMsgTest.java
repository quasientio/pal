package com.ittera.cometa.core.messages;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.core.ZmqEnabledTest;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class PublishedOffsetMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 472;
    UUID messageUuid = UUID.randomUUID();

    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageUuid);

    // verify getters
    assertThat(msgOut.getOffset(), is(offset));
    assertThat(msgOut.getMessageUuid(), is(messageUuid));

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
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
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
