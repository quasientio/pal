package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.ittera.cometa.core.exec.ExecPhase;
import com.ittera.cometa.core.messages.InterceptsMsg;
import com.ittera.cometa.core.messages.OutboundMsg;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class OutgoingMessageDispatcherTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final UUID peerUuid = UUID.randomUUID();
  private final String OUTCELL_ADDR = "inproc://cell";
  private final String OUTPUB_ADDR = "inproc://pub";
  private final String SYNC_SOCKET_ADDRESS = "inproc://sync_socket";
  private ZContext context;
  private ServiceManager manager;
  private ExecutorService execService;
  private OutgoingMessageDispatcher outgoingMessageDispatcher;
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private InternalHeader WRITE_AHEAD_HEADER;
  private List<InternalHeader> INCOMING_INTERCEPT_REQ_HEADERS;

  @Before
  public void setup() {
    this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    this.outgoingMessageDispatcher =
        new OutgoingMessageDispatcher(
            UUID.randomUUID(),
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "OutgoingMessageDispatcherTest-Service",
            OUTCELL_ADDR,
            OUTPUB_ADDR);
    final Set<Service> services = new HashSet<>(Arrays.asList(this.outgoingMessageDispatcher));
    this.INCOMING_INTERCEPT_REQ_HEADERS =
        Collections.singletonList(msgBuilder.buildIncomingInterceptRequestHeader());
    this.manager = new ServiceManager(services);
  }

  @After
  public void cleanup() throws Exception {
    // close local context
    execService.submit(
        () -> {
          context.close();
          logger.debug("context terminated");
        });

    // stop executor
    execService.shutdown();
    execService.awaitTermination(3, TimeUnit.SECONDS);
  }

  @Test
  public void sendOneReq() throws Exception {
    assertThat(outgoingMessageDispatcher.isRunning(), is(false));

    // start service
    manager.startAsync();
    Thread.sleep(300);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    Socket req = context.createSocket(SocketType.REQ);
    req.connect(OUTCELL_ADDR);

    // create SUB socket to simulate LogWriter
    Socket sub = context.createSocket(SocketType.SUB);
    sub.connect(OUTPUB_ADDR);
    sub.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // send 1 message request
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            null,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);

    // expect a 0-reply
    String reply = req.recvStr();
    assertThat(reply, is("0"));

    // check if it was published
    OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(sub, true);
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = Message.parseFrom(publishedOutMsg.getBody());
    assertThat(publishedMsg.getExecMessage(), is(msg));

    // close local sockets
    req.close();
    sub.close();

    // shut down
    manager.stopAsync();
  }

  @Test
  public void registerInterceptRequest() throws Exception {
    assertThat(outgoingMessageDispatcher.isRunning(), is(false));

    // start service
    manager.startAsync();
    Thread.sleep(300);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    Socket req = context.createSocket(SocketType.REQ);
    req.connect(OUTCELL_ADDR);

    // send 1 message request
    InterceptMessage msg =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.InterceptMessage,
            ExecPhase.UNDEFINED,
            INCOMING_INTERCEPT_REQ_HEADERS,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);

    // expect a 0-reply
    String reply = req.recvStr();
    assertThat(reply, is("0"));

    // close local sockets
    req.close();

    // shut down
    manager.stopAsync();
  }

  @Test
  public void sendOutInterceptRequest() throws Exception {
    assertThat(outgoingMessageDispatcher.isRunning(), is(false));

    // start service
    manager.startAsync();
    Thread.sleep(300);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    Socket req = context.createSocket(SocketType.REQ);
    req.connect(OUTCELL_ADDR);

    // create SUB socket to simulate LogWriter
    Socket sub = context.createSocket(SocketType.SUB);
    sub.connect(OUTPUB_ADDR);
    sub.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // send 1 message request
    InterceptMessage msg =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.singletonList("java.lang.String"),
            this.getClass().getName(),
            "someCallbackMethod");
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.InterceptMessage,
            ExecPhase.UNDEFINED,
            null,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);

    // expect a 0-reply
    String reply = req.recvStr();
    assertThat(reply, is("0"));

    // check if it was published
    OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(sub, true);
    assertThat(publishedOutMsg, is(outMsg));

    // verify message is what we sent
    Message publishedMsg = Message.parseFrom(publishedOutMsg.getBody());
    assertThat(publishedMsg.getInterceptMessage(), is(msg));

    // close local sockets
    req.close();
    sub.close();

    // shut down
    manager.stopAsync();
  }

  @Test
  public void sendExecMessageWithHeaders() throws Exception {
    assertThat(outgoingMessageDispatcher.isRunning(), is(false));

    // start service
    manager.startAsync();
    Thread.sleep(300);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    Socket req = context.createSocket(SocketType.REQ);
    req.connect(OUTCELL_ADDR);

    // create SUB socket to simulate LogWriter
    Socket sub = context.createSocket(SocketType.SUB);
    sub.connect(OUTPUB_ADDR);
    sub.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // send 1 message request
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    List<InternalHeader> headers = Collections.singletonList(this.WRITE_AHEAD_HEADER);
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            headers,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);

    // expect a 0-reply
    String reply = req.recvStr();
    assertThat(reply, is("0"));

    // get what was published
    OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(sub, true);
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = Message.parseFrom(publishedOutMsg.getBody());
    // verify header and msg as expected
    assertThat(
        publishedOutMsg.getHeaders().get(0).getHeaderType(), is(InternalHeaderType.WRITE_AHEAD));
    assertThat(publishedOutMsg.getHeaders().get(0).getValue(), is(peerUuid.toString()));
    assertThat(publishedMsg.getExecMessage(), is(msg));

    // close local sockets
    req.close();
    sub.close();

    // shut down
    manager.stopAsync();
  }

  @Test
  public void sendInterceptRequestAndMatchingExecMessage() throws Exception {
    assertThat(outgoingMessageDispatcher.isRunning(), is(false));

    // start service
    manager.startAsync();
    Thread.sleep(300);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    Socket req = context.createSocket(SocketType.REQ);
    req.connect(OUTCELL_ADDR);

    // send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");

    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.InterceptMessage,
            ExecPhase.UNDEFINED,
            INCOMING_INTERCEPT_REQ_HEADERS,
            UUID.fromString(interceptMessage.getMessageUuid()),
            null,
            msgBuilder.wrap(interceptMessage).toByteArray());
    outMsg.send(req);
    logger.debug("Sent intercept req: {}", outMsg);

    // expect a 0-reply
    String reply = req.recvStr();
    assertThat(reply, is("0"));

    // send a matching ExecMessage with non-matching phase (ExecPhase = AFTER)
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");
    outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.AFTER,
            null,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);
    logger.debug("Sent exec message: {}", outMsg);

    // verify if it gets intercepted
    InterceptsMsg interceptsMsg = InterceptsMsg.recvMsg(req, true);
    logger.debug("Got intercepted request: {}", interceptsMsg);
    assertThat(interceptsMsg.getIntercepts(), nullValue());

    // now send a matching ExecMessage
    msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");
    outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            null,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(req);
    logger.debug("Sent exec message: {}", outMsg);

    // verify if it gets intercepted
    interceptsMsg = InterceptsMsg.recvMsg(req, true);
    logger.debug("Got intercepted request: {}", interceptsMsg);
    assertThat(interceptsMsg.getIntercepts(), notNullValue());
    assertThat(interceptsMsg.getIntercepts().size(), is(1));
    assertThat(interceptsMsg.getIntercepts().get(0), is(interceptMessage));

    // close local sockets
    req.close();
    // shut down
    manager.stopAsync();
  }
}
