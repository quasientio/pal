/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.ExecPhase;
import net.ittera.pal.core.messages.InterceptEvtMsg;
import net.ittera.pal.core.messages.InterceptsMsg;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class InterceptsTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  private UUID peerUuid;
  private static final String INTERCEPT_REG_ADDR = "inproc://intercepts.reg";
  private static final String INTERCEPT_MATCH_ADDR = "inproc://intercepts.mtx";
  private ZContext context;
  private ServiceManager manager;
  private Intercepts interceptsService;
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private Socket registerSocket, matchSocket;

  @Before
  public void setup() throws InterruptedException {
    this.peerUuid = UUID.randomUUID();
    this.context = createContext();
    this.interceptsService =
        new Intercepts(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "InterceptsTest-Service",
            INTERCEPT_REG_ADDR,
            INTERCEPT_MATCH_ADDR);
    final Set<Service> services = new HashSet<>(Arrays.asList(this.interceptsService));
    this.manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);

    // create REQ socket to simulate requests (IRL: InterceptNodeListener)
    registerSocket = context.createSocket(SocketType.REQ);
    registerSocket.connect(INTERCEPT_REG_ADDR);

    // create REQ socket to simulate match requests (IRL: DispatcherConnector)
    matchSocket = context.createSocket(SocketType.REQ);
    matchSocket.connect(INTERCEPT_MATCH_ADDR);
  }

  @After
  public void cleanup() throws Exception {
    // shut down services
    manager.stopAsync();

    // close sockets
    if (registerSocket != null) {
      registerSocket.close();
    }

    if (matchSocket != null) {
      matchSocket.close();
    }

    // close local context
    ExecutorService execService = Executors.newCachedThreadPool();
    execService.submit(
        () -> {
          context.close();
          logger.debug("context terminated");
        });

    // stop executor
    execService.shutdownNow();
    execService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test
  public void registerNewIntercept() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));
  }

  @Test
  public void registerDuplicateIntercept() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));

    // now send again
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_DUP_REPLY));
  }

  @Test
  public void registerNewInterceptThenNonMatchingExecMessage() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));

    // send a non-matching ExecMessage
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.HashMap");
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            null,
            UUID.fromString(msg.getMessageUuid()),
            null,
            msgBuilder.wrap(msg).toByteArray());
    outMsg.send(matchSocket);
    logger.debug("Sent exec message: {}", outMsg);

    // verify it doesn't get intercepted
    InterceptsMsg interceptsMsg = InterceptsMsg.recvMsg(matchSocket, true);
    logger.debug("Got intercepted request: {}", interceptsMsg);
    assertThat(interceptsMsg.getIntercepts(), nullValue());
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageWithWrongPhase() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));

    // send a matching ExecMessage with non-matching phase (ExecPhase = AFTER)
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");
    InterceptKeyMessage execKeyMessage = msgBuilder.buildInterceptKey(execMessage);
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.AFTER,
            null,
            UUID.fromString(execMessage.getMessageUuid()),
            null,
            msgBuilder.wrap(execKeyMessage).toByteArray());
    outMsg.send(matchSocket);
    logger.debug("Sent exec message: {}", outMsg);

    // verify it doesn't get intercepted
    InterceptsMsg interceptsMsg = InterceptsMsg.recvMsg(matchSocket, true);
    logger.debug("Got intercepted request: {}", interceptsMsg);
    assertThat(interceptsMsg.getIntercepts(), nullValue());
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageAndPhase() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));

    // now send a matching ExecMessage
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");
    InterceptKeyMessage execKeyMessage = msgBuilder.buildInterceptKey(execMessage);

    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.InterceptKey,
            ExecPhase.BEFORE,
            null,
            UUID.fromString(execMessage.getMessageUuid()),
            null,
            msgBuilder.wrap(execKeyMessage).toByteArray());
    outMsg.send(matchSocket);
    logger.debug("Sent exec message: {}", outMsg);

    // verify that it gets intercepted
    InterceptsMsg interceptsMsg = InterceptsMsg.recvMsg(matchSocket, true);
    logger.debug("Got intercepted request: {}", interceptsMsg);
    assertThat(interceptsMsg.getIntercepts(), notNullValue());
    assertThat(interceptsMsg.getIntercepts().size(), is(1));
    assertThat(interceptsMsg.getIntercepts().get(0), is(interceptMessage));
  }

  @Test
  public void registerNewInterceptThenUnregister() throws Exception {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "new",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    final UUID interceptUuid = UUID.fromString(interceptMessage.getMessageUuid());
    new InterceptEvtMsg(interceptMessage.toByteArray()).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.REG_OK_REPLY));

    // now unregister
    new InterceptEvtMsg(interceptUuid).send(registerSocket);

    // verify reply
    reply = registerSocket.recvStr();
    assertThat(reply, is(Intercepts.UNREG_OK_REPLY));
  }
}
