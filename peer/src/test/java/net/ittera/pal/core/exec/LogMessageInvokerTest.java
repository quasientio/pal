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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.core.messages.InboundLogMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
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
import org.zeromq.ZMQ.Socket;

public class LogMessageInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private final String INLOG_ADDR = "inproc://inlog";
  private ZContext context;
  private Socket dealerSocket;
  private ExecutorService execService;
  private LogMessageInvoker logMessageInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private List<ExecMessage> execMessageReplies = new ArrayList<>();
  private List<Boolean> interceptReqMessageResults = new ArrayList<>();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate LogReader's DEALER socket
    this.dealerSocket = context.createSocket(SocketType.DEALER);
    dealerSocket.bind(INLOG_ADDR);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall for ExecMessage
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
                  execMessageReplies.add(reply);
                  return reply;
                });

    // stub incomingCall for InterceptMessage
    when(incomingMessageDispatcher.incomingIntercept(any(), anyBoolean()))
        .thenAnswer(
            (Answer<Boolean>)
                invocationOnMock -> {
                  interceptReqMessageResults.add(true);
                  return true;
                });

    this.logMessageInvoker =
        new LogMessageInvoker(context, msgBuilder, INLOG_ADDR, incomingMessageDispatcher, peerUuid);
  }

  @After
  public void cleanup() throws Exception {
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    execMessageReplies.clear();
  }

  @Test
  public void invokeExecMessage() throws Exception {

    // start invoker thread
    execService.submit(logMessageInvoker);

    // deal msg
    int fakeOffset = 0;
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // send request to DEALER socket
    InboundLogMsg msg =
        new InboundLogMsg(fakeOffset, ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    msg.send(dealerSocket);

    // wait for msg to be rcvd
    while (logMessageInvoker.getRequestsDispatched().get() < 1) {
      Thread.sleep(100);
    }
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

    assertThat(execMessageReplies.size(), is(1));

    // assert reply msg followsUuid of original
    assertThat(execMessageReplies.get(0).getFollowingUuid(), is(invokable.getMessageUuid()));
  }

  @Test
  public void invokeInterceptRequestMessage() throws Exception {

    // start invoker thread
    execService.submit(logMessageInvoker);

    // deal msg
    int fakeOffset = 0;
    InterceptMessage invokable =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.emptyList(),
            this.getClass().getName(),
            "someCallbackMethod");
    InboundLogMsg msg =
        new InboundLogMsg(fakeOffset, ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    msg.send(dealerSocket);

    // wait for msg to be rcvd
    while (logMessageInvoker.getRequestsDispatched().get() < 1) {
      Thread.sleep(100);
    }
    verify(incomingMessageDispatcher, times(1)).incomingIntercept(any(), anyBoolean());

    assertThat(interceptReqMessageResults.size(), is(1));
  }

  @Test
  public void invokeManyMessages() throws Exception {

    // start invoker thread
    execService.submit(logMessageInvoker);

    // deal msg
    int fakeOffset = 0;
    int msgCount = 10;
    List<ExecMessage> msgsToInvoke = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      msgsToInvoke.add(invokable);
      InboundLogMsg msg =
          new InboundLogMsg(fakeOffset, ColferUtils.toBytes(msgBuilder.wrap(invokable)));
      msg.send(dealerSocket);
    }

    // wait for msg to be rcvd
    while (logMessageInvoker.getRequestsDispatched().get() < msgCount) {
      Thread.sleep(100);
    }

    // assert number of calls
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());
    assertThat(execMessageReplies.size(), is(msgCount));

    // assert reply msg followsUuid of original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          execMessageReplies.get(i).getFollowingUuid(), is(msgsToInvoke.get(i).getMessageUuid()));
    }
  }
}
