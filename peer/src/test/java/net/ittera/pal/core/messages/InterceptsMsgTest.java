package net.ittera.pal.core.messages;

import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptType;
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

    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");

    InterceptsMsg msgOut = new InterceptsMsg(Collections.singletonList(interceptMessage));

    // verify getters
    assertThat(msgOut.getIntercepts().size(), is(1));
    assertThat(msgOut.getIntercepts().get(0), is(interceptMessage));

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
    List<Intercepts.InterceptMessage> interceptMessages = new ArrayList<>();

    for (int i = 0; i < interceptsToSend; i++) {
      interceptMessages.add(
          messageBuilder.buildInterceptMessage(
              UUID.randomUUID(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "println",
              Collections.singletonList("java.lang.String"),
              this.getClass().getName(),
              format("someCallbackMethod_%d", i)));
    }

    InterceptsMsg msgOut = new InterceptsMsg(interceptMessages);

    // verify getters
    assertThat(msgOut.getIntercepts(), is(interceptMessages));

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
