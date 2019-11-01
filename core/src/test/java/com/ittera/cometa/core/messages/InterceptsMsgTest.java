package com.ittera.cometa.core.messages;

import static java.lang.String.format;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import java.util.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InterceptsMsgTest extends ZmqEnabledTest {
  private MessageBuilder messageBuilder = new ProtobufMessageBuilder();
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendNoIntercepts() throws InvalidProtocolBufferException {
    InterceptsMsg msgOut = new InterceptsMsg(null);

    // verify getters
    assertThat(msgOut.getIntercepts(), is(nullValue()));

    // send
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket in = zContext.createSocket(SocketType.REP);
    in.bind(socketAddr);
    ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
    out.connect(socketAddr);
    msgOut.send(out);

    // receive and compare
    InterceptsMsg msgIn = InterceptsMsg.recvMsg(in, true);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void sendOneIntercept() throws InvalidProtocolBufferException {

    InterceptRequest interceptRequest =
        messageBuilder.buildInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptsMsg msgOut = new InterceptsMsg(Collections.singletonList(interceptRequest));

    // verify getters
    assertThat(msgOut.getIntercepts().size(), is(1));
    assertThat(msgOut.getIntercepts().get(0), is(interceptRequest));

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
    InterceptsMsg msgIn = InterceptsMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }

  @Test
  public void sendManyIntercepts() throws Exception {

    int interceptsToSend = 5;
    List<InterceptRequest> interceptRequests = new ArrayList<>();

    for (int i = 0; i < interceptsToSend; i++) {
      interceptRequests.add(
          messageBuilder.buildInterceptRequest(
              UUID.randomUUID(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "println",
              Collections.singletonList("java.lang.String"),
              this.getClass().getName(),
              format("someCallbackMethod_%d", i)));
    }

    InterceptsMsg msgOut = new InterceptsMsg(interceptRequests);

    // verify getters
    assertThat(msgOut.getIntercepts(), is(interceptRequests));

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
    InterceptsMsg msgIn = InterceptsMsg.recvMsg(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zContext.destroy();
  }
}
