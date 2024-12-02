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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.messages.InterceptEventMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class InterceptsTest extends ZmqEnabledTest {

  private UUID peerUuid;
  private static final String INTERCEPT_REG_ADDRESS = "inproc://intercepts.reg";
  private ZContext context;
  private ServiceManager manager;
  private InterceptMatcher interceptMatcher;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private Socket registerSocket;

  @Before
  public void setup() throws InterruptedException {
    this.peerUuid = UUID.randomUUID();
    this.context = createContext();
    this.interceptMatcher =
        new InterceptMatcher(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "InterceptsTest-Service",
            INTERCEPT_REG_ADDRESS);
    final Set<Service> services = new HashSet<>(List.of(this.interceptMatcher));
    this.manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);

    // create REQ socket to simulate requests (IRL: InterceptNodeListener)
    registerSocket = context.createSocket(SocketType.REQ);
    registerSocket.connect(INTERCEPT_REG_ADDRESS);
  }

  @After
  public void cleanup() throws Exception {
    // shut down services
    manager.stopAsync();

    // close sockets
    if (registerSocket != null) {
      registerSocket.close();
    }

    // close zmq context
    closeContext(context);
  }

  @Test
  public void registerNewIntercept() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    byte[] buf = new byte[interceptMessage.marshalFit()];
    interceptMessage.marshal(buf, 0);
    new InterceptEventMsg(buf).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));
  }

  @Test
  public void registerDuplicateIntercept() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));

    // now send again
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_DUP_REPLY));
  }

  @Test
  public void registerNewInterceptThenNonMatchingExecMessage() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));

    // create a non-matching ExecMessage
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.HashMap");

    // verify it doesn't get intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(execMessage, ExecPhase.BEFORE);
    assertThat(matchingIntercepts, is(empty()));
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageWithWrongPhase() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));

    // create a matching ExecMessage with non-matching phase (ExecPhase = AFTER)
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");

    // verify it doesn't get intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(execMessage, ExecPhase.AFTER);
    assertThat(matchingIntercepts, is(empty()));
  }

  @Test
  public void registerNewInterceptThenMatchingKeyMessageAndPhase() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));

    // now send a matching ExecMessage
    ExecMessage execMessage = msgBuilder.buildEmptyConstructor(peerUuid, "java.util.ArrayList");

    // verify that it gets intercepted
    List<InterceptMessage> matchingIntercepts =
        interceptMatcher.getMatchingIntercepts(execMessage, ExecPhase.BEFORE);
    assertThat(matchingIntercepts, is(not(empty())));
    assertThat(matchingIntercepts.size(), is(1));
    assertThat(matchingIntercepts.get(0), is(interceptMessage));
  }

  @Test
  public void registerNewInterceptThenUnregister() {
    // create and send intercept request
    InterceptMessage interceptMessage =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.util.ArrayList",
            "<init>",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    final String interceptId = interceptMessage.getMessageId();
    new InterceptEventMsg(interceptMessage).send(registerSocket);

    // verify reply
    String reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.REGISTER_OK_REPLY));

    // now unregister
    new InterceptEventMsg(interceptId).send(registerSocket);

    // verify reply
    reply = registerSocket.recvStr();
    assertThat(reply, is(InterceptMatcher.UNREGISTER_OK_REPLY));
  }
}
