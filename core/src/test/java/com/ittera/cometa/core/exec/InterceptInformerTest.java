package com.ittera.cometa.core.exec;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.common.lang.intercept.InterceptType;
import com.ittera.cometa.common.lang.intercept.InterceptableMethodCall;
import com.ittera.cometa.common.znodes.InterceptEvent;
import com.ittera.cometa.common.znodes.InterceptEvent.Type;
import com.ittera.cometa.common.znodes.InterceptRequest;
import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class InterceptInformerTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private ExecutorService execService;
  private InterceptInformer interceptInformer;
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private PALDirectory palDirectory;
  private Socket repSocket;
  private List<InterceptMessage> messagesReceived;
  private static final String INTERCEPT_REG_ADDR = "inproc://intercepts.reg";

  private class InterceptsStub implements Runnable {
    @Override
    public void run() {
      repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(INTERCEPT_REG_ADDR);
      while (!Thread.interrupted()) {
        final byte[] msg;
        msg = repSocket.recv();
        try {
          InterceptMessage incomingInterceptMessage = InterceptMessage.parseFrom(msg);
          messagesReceived.add(incomingInterceptMessage);
          repSocket.send("0");
        } catch (InvalidProtocolBufferException e) {
          e.printStackTrace();
          break;
        }
      }
    }
  }

  @Before
  public void setup() {
    context = createContext();
    execService = Executors.newCachedThreadPool();
    messagesReceived = new ArrayList<>();
  }

  @After
  public void cleanup() throws Exception {
    messagesReceived.clear();

    // close local context
    execService.submit(
        () -> {
          context.close();
          logger.debug("context terminated");
        });

    // stop executor
    execService.shutdownNow();
    execService.awaitTermination(2, TimeUnit.SECONDS);

    palDirectory.close();
  }

  @Test
  public void interceptEventFromRemotePeer() throws Exception {
    palDirectory = mock(PALDirectory.class);

    // stub getInterceptRequest call
    when(palDirectory.getInterceptRequest(any()))
        .thenAnswer(
            (Answer)
                invocation ->
                    new InterceptRequest<>(
                        UUID.randomUUID(),
                        UUID.randomUUID(), // remote peer
                        InterceptType.BEFORE,
                        "java.io.PrintStream",
                        "org.package.Callback",
                        "callMe",
                        new InterceptableMethodCall("println", null)));

    // simulate Intercepts registration endpoint
    execService.submit(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final UUID interceptUuid = UUID.randomUUID();
    interceptInformer =
        new InterceptInformer(context, msgBuilder, palDirectory, peerUuid, INTERCEPT_REG_ADDR);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            remotePeerUuid,
            interceptUuid);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that palDirectory.getInterceptRequest is invoked
    verify(palDirectory, times(1)).getInterceptRequest(any());

    // verify that intercept messages were sent
    assertThat(messagesReceived.size(), is(1));
  }

  @Test
  public void interceptEventFromThisPeer() throws Exception {
    palDirectory = mock(PALDirectory.class);

    // stub getInterceptRequest call
    when(palDirectory.getInterceptRequest(any()))
        .thenAnswer(
            (Answer)
                invocation ->
                    new InterceptRequest<>(
                        UUID.randomUUID(),
                        peerUuid, // this peer (self)
                        InterceptType.BEFORE,
                        "java.io.PrintStream",
                        "org.package.Callback",
                        "callMe",
                        new InterceptableMethodCall("println", null)));

    // simulate Intercepts registration endpoint
    execService.submit(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID interceptUuid = UUID.randomUUID();
    interceptInformer =
        new InterceptInformer(context, msgBuilder, palDirectory, peerUuid, INTERCEPT_REG_ADDR);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            peerUuid,
            interceptUuid);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that palDirectory.getInterceptRequest is NOT invoked
    verify(palDirectory, times(0)).getInterceptRequest(any());

    // verify that NO intercept messages were sent
    assertThat(messagesReceived.size(), is(0));
  }
}
