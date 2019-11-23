package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.ittera.cometa.common.ExecPhase;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.OutboundMsg;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import java.util.ArrayList;
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
  private ZContext context;
  private ServiceManager manager;
  private OutgoingMessageDispatcher outgoingMessageDispatcher;
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private InternalHeader WRITE_AHEAD_HEADER;
  private Socket reqSocket, subSocket;

  @Before
  public void setup() throws InterruptedException {
    this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
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
    this.manager = new ServiceManager(services);

    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
    assertThat(outgoingMessageDispatcher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    reqSocket = context.createSocket(SocketType.REQ);
    reqSocket.connect(OUTCELL_ADDR);

    // create SUB socket to simulate LogWriter
    subSocket = context.createSocket(SocketType.SUB);
    subSocket.connect(OUTPUB_ADDR);
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
  }

  @After
  public void cleanup() throws Exception {
    // close sockets
    if (reqSocket != null) {
      reqSocket.close();
    }
    if (subSocket != null) {
      subSocket.close();
    }
    // shut down services
    manager.stopAsync();

    // close local context
    ExecutorService execService = Executors.newCachedThreadPool();
    execService.submit(
        () -> {
          context.close();
          logger.debug("context terminated");
        });

    // stop executor
    execService.shutdown();
    execService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test
  public void sendExecMessage() throws Exception {
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
    outMsg.send(reqSocket);

    // expect a 0-reply
    String reply = reqSocket.recvStr();
    assertThat(reply, is("0"));

    // check if it was published
    OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(subSocket, true);
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = Message.parseFrom(publishedOutMsg.getBody());
    assertThat(publishedMsg.getExecMessage(), is(msg));
  }

  @Test
  public void sendExecMessageWithHeaders() throws Exception {
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
    outMsg.send(reqSocket);

    // expect a 0-reply
    String reply = reqSocket.recvStr();
    assertThat(reply, is("0"));

    // get what was published
    OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(subSocket, true);
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = Message.parseFrom(publishedOutMsg.getBody());
    // verify header and msg as expected
    assertThat(
        publishedOutMsg.getHeaders().get(0).getHeaderType(), is(InternalHeaderType.WRITE_AHEAD));
    assertThat(publishedOutMsg.getHeaders().get(0).getValue(), is(peerUuid.toString()));
    assertThat(publishedMsg.getExecMessage(), is(msg));
  }

  @Test
  public void sendManyExecMessages() throws Exception {
    int messagesToSend = 15;
    List<ExecMessage> messagesSent = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
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
      outMsg.send(reqSocket);
      messagesSent.add(msg);

      // expect a 0-reply
      String reply = reqSocket.recvStr();
      assertThat(reply, is("0"));
    }

    // get what was published
    List<ExecMessage> messagesPublished = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
      OutboundMsg publishedOutMsg = OutboundMsg.recvMsg(subSocket, true);
      messagesPublished.add(Message.parseFrom(publishedOutMsg.getBody()).getExecMessage());
    }

    // compare sent and published lists
    assertThat(messagesPublished, is(messagesSent));
  }
}
