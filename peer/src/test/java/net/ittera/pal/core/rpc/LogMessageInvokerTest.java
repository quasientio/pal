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

package net.ittera.pal.core.rpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.messages.InboundLogMsg;
import net.ittera.pal.core.rpc.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
  private static final String IN_LOG_ADDRESS = "inproc://in_log";
  private ZContext context;
  private Socket dealerSocket;
  private ExecutorService execService;
  private LogMessageInvoker logMessageInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final List<ExecMessage> execMessageReplies = new ArrayList<>();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate LogReader's DEALER socket
    this.dealerSocket = context.createSocket(SocketType.DEALER);
    dealerSocket.bind(IN_LOG_ADDRESS);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall for ExecMessage
    when(incomingMessageDispatcher.incomingCall(any(), any(), anyBoolean()))
        .thenAnswer(
            (Answer<?>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  ExecMessage incomingMsg = (ExecMessage) args[0];
                  Constructor<?> constructor = null;
                  try {
                    constructor = String.class.getConstructor();
                  } catch (NoSuchMethodException e) {
                    logger.error("Error getting constructor", e);
                  }
                  ExecMessage reply =
                      msgBuilder.buildReturnValue(
                          peerUuid, "", constructor, null, false, incomingMsg.getMessageId());
                  execMessageReplies.add(reply);
                  return reply;
                });

    this.logMessageInvoker =
        new LogMessageInvoker(
            context, msgBuilder, IN_LOG_ADDRESS, incomingMessageDispatcher, peerUuid);
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
    execService.execute(logMessageInvoker);

    // create new ExecMessage
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // create a CountDownLatch with a count of 1
    CountDownLatch latch = new CountDownLatch(1);

    // create a MessageDispatchListener that counts down the latch when a message is dispatched
    MessageDispatchListener listener = message -> latch.countDown();

    // register the listener with the logMessageInvoker
    logMessageInvoker.addMessageDispatchListener(listener);

    // send request message to DEALER socket
    int fakeOffset = 0;
    Headers emptyHeaders = new RecordHeaders();
    InboundLogMsg msg =
        new InboundLogMsg(
            fakeOffset,
            MessageFormatType.BINARY_RPC,
            emptyHeaders,
            ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    msg.send(dealerSocket);

    // wait for the message to be dispatched
    latch.await();

    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), anyBoolean());

    assertThat(execMessageReplies.size(), is(1));
    assertThat(logMessageInvoker.getRequestsDispatched().get(), is((long) 1));

    // assert reply msg is response to original
    assertThat(execMessageReplies.get(0).getResponseToId(), is(invokable.getMessageId()));
  }

  @Test
  public void invokeManyMessages() throws Exception {

    // start invoker thread
    execService.execute(logMessageInvoker);

    // create messages
    int fakeOffset = 0;
    int msgCount = 10;
    List<ExecMessage> messagesToInvoke = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      messagesToInvoke.add(invokable);
    }

    // create a CountDownLatch with a count of msgCount
    CountDownLatch latch = new CountDownLatch(msgCount);

    // create a MessageDispatchListener that counts down the latch when a message is dispatched
    MessageDispatchListener listener = message -> latch.countDown();

    // register the listener with the logMessageInvoker
    logMessageInvoker.addMessageDispatchListener(listener);

    // send log messages to DEALER socket
    Headers emptyHeaders = new RecordHeaders();
    messagesToInvoke.forEach(
        invokable -> {
          InboundLogMsg msg =
              new InboundLogMsg(
                  fakeOffset,
                  MessageFormatType.BINARY_RPC,
                  emptyHeaders,
                  ColferUtils.toBytes(msgBuilder.wrap(invokable)));
          msg.send(dealerSocket);
        });

    // wait for msg to be received
    latch.await();

    // assert number of calls
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), anyBoolean());
    assertThat(logMessageInvoker.getRequestsDispatched().get(), is((long) msgCount));
    assertThat(execMessageReplies.size(), is(msgCount));

    // assert reply msg is response to original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          execMessageReplies.get(i).getResponseToId(), is(messagesToInvoke.get(i).getMessageId()));
    }
  }
}
