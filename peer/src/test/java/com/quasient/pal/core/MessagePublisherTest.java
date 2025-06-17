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

package com.quasient.pal.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.InternalHeaderType;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class MessagePublisherTest extends ZmqEnabledTest {
  private final UUID peerUuid = UUID.randomUUID();
  private static final String OUT_REP_ADDRESS = "inproc://cell";
  private static final String OUT_PUB_ADDRESS = "inproc://pub";
  private ZContext context;
  private ServiceManager manager;
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private InternalHeader writeAheadHeader;
  private Socket reqSocket;
  private Socket subSocket;

  @Before
  public void setup() throws InterruptedException {
    this.writeAheadHeader = msgBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
    MessagePublisher messagePublisher =
        new MessagePublisher(
            UUID.randomUUID(),
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "MessagePublisherTest-Service",
            OUT_REP_ADDRESS,
            OUT_PUB_ADDRESS);
    final Set<Service> services = new HashSet<>(List.of(messagePublisher));
    this.manager = new ServiceManager(services);

    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
    assertThat(messagePublisher.isRunning(), is(true));

    // create REQ socket to simulate requests (IRL: DispatcherConnector)
    reqSocket = context.createSocket(SocketType.REQ);
    reqSocket.connect(OUT_REP_ADDRESS);

    // create SUB socket to simulate LogWriter
    subSocket = context.createSocket(SocketType.SUB);
    subSocket.connect(OUT_PUB_ADDRESS);
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

    closeContext(context);
  }

  @Test
  public void sendExecMessage() {
    // send 1 message request
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            null,
            msg.getMessageId(),
            null,
            msgBuilder.wrap(msg));
    outMsg.send(reqSocket);

    // expect a 0-response
    String response = reqSocket.recvStr();
    assertThat(response, is("0"));

    // check if it was published
    OutboundMsg publishedOutMsg = OutboundMsg.receive(subSocket, true);
    assertThat(publishedOutMsg, is(notNullValue()));
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = new Message();
    publishedMsg.unmarshal(publishedOutMsg.getBody(), 0);
    assertThat(publishedMsg.getExecMessage(), is(msg));
  }

  @Test
  public void sendExecMessageWithHeaders() {
    // send 1 message request
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    List<InternalHeader> headers = Collections.singletonList(this.writeAheadHeader);
    OutboundMsg outMsg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            msg.getMessageId(),
            null,
            msgBuilder.wrap(msg));
    outMsg.send(reqSocket);

    // expect a 0-response
    String response = reqSocket.recvStr();
    assertThat(response, is("0"));

    // get what was published
    OutboundMsg publishedOutMsg = OutboundMsg.receive(subSocket, true);
    assertThat(publishedOutMsg, is(notNullValue()));
    assertThat(publishedOutMsg, is(outMsg));

    // verify exec message is what we sent
    Message publishedMsg = new Message();
    publishedMsg.unmarshal(publishedOutMsg.getBody(), 0);
    // verify header and msg as expected
    assertThat(publishedOutMsg.getHeaders(), is(notNullValue()));
    assertThat(
        publishedOutMsg.getHeaders().get(0).getHeaderType(),
        is(InternalHeaderType.WRITE_AHEAD.toByte()));
    assertThat(publishedOutMsg.getHeaders().get(0).getValue(), is(peerUuid.toString()));
    assertThat(publishedMsg.getExecMessage(), is(msg));
  }

  @Test
  public void sendManyExecMessages() {
    int messagesToSend = 15;
    List<ExecMessage> messagesSent = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
      // send 1 message request
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      OutboundMsg outMsg =
          new OutboundMsg(
              MessageType.EXEC_CONSTRUCTOR,
              ExecPhase.BEFORE,
              null,
              msg.getMessageId(),
              null,
              msgBuilder.wrap(msg));
      outMsg.send(reqSocket);
      messagesSent.add(msg);

      // expect a 0-response
      String response = reqSocket.recvStr();
      assertThat(response, is("0"));
    }

    // get what was published
    List<ExecMessage> messagesPublished = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
      OutboundMsg publishedOutMsg = OutboundMsg.receive(subSocket, true);
      assertThat(publishedOutMsg, is(notNullValue()));
      Message receivedMsg = new Message();
      receivedMsg.unmarshal(publishedOutMsg.getBody(), 0);
      messagesPublished.add(receivedMsg.getExecMessage());
    }

    // compare sent and published lists
    assertThat(messagesPublished, is(messagesSent));
  }
}
