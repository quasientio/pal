package com.ittera.cometa.core.messages;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageType;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InboundLogMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 199;
    byte[] body = "whatever".getBytes();

    InboundLogMsg msgOut = new InboundLogMsg(MessageType.ExecMessage, offset, body);

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket out = zContext.createSocket(SocketType.DEALER);
    out.bind(socketAddr);
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.connect(socketAddr);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    InboundLogMsg msgIn = InboundLogMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 199;
    byte[] body = "whatever".getBytes();

    InboundLogMsg msg1 = new InboundLogMsg(MessageType.ExecMessage, offset, body);

    // assert content equality
    assertThat(new InboundLogMsg(MessageType.ExecMessage, offset, body), is(msg1));

    // different type
    assertThat(new InboundLogMsg(MessageType.InterceptRequest, offset, body), is(not(msg1)));

    // different offset
    assertThat(new InboundLogMsg(MessageType.ExecMessage, offset + 1, body), is(not(msg1)));

    // different body
    body = "whatevah".getBytes();
    assertThat(new InboundLogMsg(MessageType.ExecMessage, offset, body), is(not(msg1)));
  }
}
