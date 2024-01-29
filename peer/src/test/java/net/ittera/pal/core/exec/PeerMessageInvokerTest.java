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

package net.ittera.pal.core.exec;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class PeerMessageInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private final String DEALER_ADDR = "inproc://deal";
  private ZContext context;
  private Socket dealerSocket;
  private ExecutorService execService;
  private PeerMessageInvoker peerMessageInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate RPCRequestDispatcher's DEALER socket
    this.dealerSocket = context.createSocket(SocketType.DEALER);
    dealerSocket.bind(DEALER_ADDR);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall to return a message which seems valid reply
    when(incomingMessageDispatcher.incomingCall(any(), anyBoolean()))
        .thenAnswer(
            (Answer)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  ExecMessage incomingMsg = (ExecMessage) args[0];
                  Constructor constructor = null;
                  try {
                    constructor = String.class.getConstructor();
                  } catch (NoSuchMethodException e) {
                    logger.error("Error getting constructor", e);
                  }
                  ExecMessage reply =
                      msgBuilder.buildReturnValue(
                          peerUuid,
                          new String(),
                          constructor,
                          null,
                          false,
                          incomingMsg.getMessageUuid());
                  return reply;
                });

    this.peerMessageInvoker =
        new PeerMessageInvoker(
            context, msgBuilder, DEALER_ADDR, incomingMessageDispatcher, peerUuid);
  }

  @After
  public void cleanup() throws Exception {
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.debug("execService shut down");
  }

  @Test
  public void invokeOneMessage() throws Exception {

    // start invoker thread
    execService.submit(peerMessageInvoker);

    // deal msg
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message wrapper = msgBuilder.wrap(invokable);
    dealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
    dealerSocket.send(ColferUtils.toBytes(wrapper), 0);
    // get reply
    dealerSocket.recv(); // 1st frame empty to emulate REP envelope
    Message msg = new Message();
    msg.unmarshal(dealerSocket.recv(), 0);
    ExecMessage reply = msg.getExecMessage();

    assertThat(peerMessageInvoker.getRequestsDispatched().get(), is(Long.valueOf(1)));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

    // assert reply msg followsUuid of original
    assertThat(reply.getResponseToUuid(), is(invokable.getMessageUuid()));
  }

  @Test
  public void invokeManyMessages() throws Exception {

    // start invoker thread
    execService.submit(peerMessageInvoker);

    // deal msgs
    int msgCount = 10;
    List<ExecMessage> msgsToInvoke = new ArrayList<>();
    List<ExecMessage> replyMessages = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      // deal msg
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      Message wrapper = msgBuilder.wrap(invokable);
      dealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
      dealerSocket.send(ColferUtils.toBytes(wrapper), 0);
      msgsToInvoke.add(invokable);
      // get reply
      dealerSocket.recv(); // 1st frame empty to emulate REP envelope
      Message msg = new Message();
      msg.unmarshal(dealerSocket.recv(), 0);
      ExecMessage reply = msg.getExecMessage();
      replyMessages.add(reply);
    }

    // assert number of calls
    assertThat(peerMessageInvoker.getRequestsDispatched().get(), is(Long.valueOf(msgCount)));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());

    // assert reply msg followsUuid of original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          replyMessages.get(i).getResponseToUuid(), is(msgsToInvoke.get(i).getMessageUuid()));
    }
  }
}
