/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageFormatType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class LogRpcInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private static final String SOURCE_LOG_ADDRESS = "inproc://source_log";
  private ZContext context;
  private Socket dealerSocket;
  private ExecutorService execService;
  private LogRpcInvoker logRpcInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder(peerUuid);
  private final List<ExecMessage> execMessageReplies = new ArrayList<>();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate LogReader's DEALER socket
    this.dealerSocket = context.createSocket(SocketType.DEALER);
    dealerSocket.bind(SOURCE_LOG_ADDRESS);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall for ExecMessage
    when(incomingMessageDispatcher.incomingCall(any(), any(), any()))
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
                  ExecMessage response =
                      msgBuilder.buildReturnValue(
                          "", constructor, null, false, incomingMsg.getMessageId());
                  execMessageReplies.add(response);
                  return response;
                });

    this.logRpcInvoker =
        new LogRpcInvoker(
            context, msgBuilder, SOURCE_LOG_ADDRESS, incomingMessageDispatcher, peerUuid);
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
    execService.execute(logRpcInvoker);

    // create new ExecMessage
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // create a CountDownLatch with a count of 1
    CountDownLatch latch = new CountDownLatch(1);

    // create a MessageDispatchListener that counts down the latch when a message is dispatched
    MessageDispatchListener listener = message -> latch.countDown();

    // register the listener with the logRpcInvoker
    logRpcInvoker.addMessageDispatchListener(listener);

    // send request message to DEALER socket
    int fakeOffset = 0;
    Headers emptyHeaders = new RecordHeaders();
    InboundLogMsg msg =
        new InboundLogMsg(
            fakeOffset,
            MessageFormatType.BINARY,
            emptyHeaders,
            ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    msg.send(dealerSocket);

    // wait for the message to be dispatched
    latch.await();

    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());

    assertThat(execMessageReplies.size(), is(1));
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(logRpcInvoker.getRequestsDispatched(), is(1L));

    // assert response msg is response to original
    assertThat(execMessageReplies.get(0).getResponseToId(), is(invokable.getMessageId()));
  }

  @Test
  public void invokeManyMessages() throws Exception {

    // start invoker thread
    execService.execute(logRpcInvoker);

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

    // register the listener with the logRpcInvoker
    logRpcInvoker.addMessageDispatchListener(listener);

    // send log messages to DEALER socket
    Headers emptyHeaders = new RecordHeaders();
    messagesToInvoke.forEach(
        invokable -> {
          InboundLogMsg msg =
              new InboundLogMsg(
                  fakeOffset,
                  MessageFormatType.BINARY,
                  emptyHeaders,
                  ColferUtils.toBytes(msgBuilder.wrap(invokable)));
          msg.send(dealerSocket);
        });

    // wait for msg to be received
    latch.await();

    // assert number of calls
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), any());
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is((long) msgCount));
    assertThat(logRpcInvoker.getRequestsDispatched(), is((long) msgCount));
    assertThat(execMessageReplies.size(), is(msgCount));

    // assert response msg is response to original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          execMessageReplies.get(i).getResponseToId(), is(messagesToInvoke.get(i).getMessageId()));
    }
  }

  // ===== Error Handling Test Specifications =====
  // The following tests are specifications awaiting implementation in #459.
  // They focus on error handling paths that have lower coverage.

  /**
   * Tests that run() continues processing after receiving an invalid JSON-RPC message.
   *
   * <p>This verifies that malformed JSON-RPC messages are logged and skipped without terminating
   * the invoker thread, allowing it to continue processing subsequent valid messages.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void run_invalidJsonRpcMessage_logsErrorAndContinues() {
    // Given: LogRpcInvoker connected to dealer socket
    // When: Malformed JSON-RPC message received (e.g., truncated JSON, missing required fields)
    // Then: Error logged; invoker continues processing next message

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Start invoker in executor
    // - Send malformed JSON-RPC message via dealer socket
    // - Send valid message after the malformed one
    // - Verify invoker processed the valid message (continues processing)
    // - Verify dispatch was NOT called for malformed message
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that run() continues processing after receiving a corrupted binary message.
   *
   * <p>This verifies that binary messages that fail Colfer unmarshalling are logged and skipped
   * without terminating the invoker thread.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void run_binaryParseException_logsErrorAndContinues() {
    // Given: LogRpcInvoker in binary mode
    // When: Corrupted binary message (invalid Colfer format) received
    // Then: Error logged; invoker continues processing

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Start invoker in executor
    // - Send corrupted binary message (random bytes that fail Colfer unmarshal)
    // - Send valid binary message after the corrupted one
    // - Verify invoker processed the valid message (continues processing)
    // - Verify dispatch was NOT called for corrupted message
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that run() handles missing producer-id header gracefully.
   *
   * <p>This verifies that when the producer-id header is missing from a JSON-RPC message, the
   * invoker logs the error but continues processing with a null fromPeerUuid.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void run_missingProducerId_usesDefaultValue() {
    // Given: Message without producer-id header
    // When: Message processed
    // Then: Default producer ID (null) used; no exception; processing continues

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Start invoker in executor
    // - Create valid JSON-RPC InboundLogMsg WITHOUT producer-id header
    // - Send message via dealer socket
    // - Verify dispatch was called (message processed despite missing header)
    // - Verify the fromPeerUuid passed to messageBuilder.jsonRpcRequestToExecMessage is null
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that run() logs a warning and skips messages with unknown format types.
   *
   * <p>This verifies that the default branch in the message format switch statement is handled
   * correctly by logging an error and skipping the message.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void run_unknownMessageFormat_logsWarning() {
    // Given: Message with unrecognized format byte (not JSON or BINARY)
    // When: Message processed
    // Then: Warning logged; message skipped; invoker continues

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Start invoker in executor
    // - Create InboundLogMsg with unknown MessageFormatType (may need reflection/mocking)
    // - Send message via dealer socket
    // - Send valid message after to verify invoker continues
    // - Verify dispatch was NOT called for unknown format message
    // - Verify invoker processed subsequent valid message
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that dispatch() handles exceptions from IncomingMessageDispatcher gracefully.
   *
   * <p>This verifies that when the IncomingMessageDispatcher throws an exception during JSON-RPC
   * message dispatch, the error is logged and the invoker continues processing.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void dispatch_throwsException_handledGracefully() {
    // Given: IncomingMessageDispatcher mock that throws RuntimeException on incomingCall
    // When: dispatch called via valid JSON-RPC message
    // Then: Exception caught, logged; invoker continues processing next message

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Configure mock to throw RuntimeException on incomingCall
    // - Start invoker in executor
    // - Send valid JSON-RPC message that will trigger dispatch
    // - Verify exception was thrown (via mock verification or log capture)
    // - Send another message to verify invoker continues
    // - Reconfigure mock to succeed, verify second message processed
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that closeConnections() handles already-closed socket without throwing exception.
   *
   * <p>This verifies that calling closeConnections() when the socket is already closed (e.g., from
   * ZContext termination) does not throw an exception.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void closeConnections_socketAlreadyClosed_noException() {
    // Given: Socket already closed externally (e.g., via context termination)
    // When: closeConnections called
    // Then: No exception; cleanup completes normally

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker
    // - Start invoker briefly to initialize socket
    // - Close the ZContext (which closes all sockets)
    // - Call closeConnections() directly via reflection or by interrupting the invoker
    // - Verify no exception is thrown
    // - Verify cleanup completes (super.closeConnections is called)
    Assert.fail("Not yet implemented");
  }

  /**
   * Tests that run() exits gracefully when the thread is interrupted.
   *
   * <p>This verifies that the invoker thread responds to interruption by breaking out of its main
   * loop and cleaning up resources.
   */
  @Test
  @Ignore("Awaiting implementation in #459")
  public void run_interrupted_exitsGracefully() {
    // Given: Running LogRpcInvoker thread
    // When: Thread interrupted via Thread.interrupt()
    // Then: Invoker exits run loop gracefully; closeConnections called

    // TODO(#459): Implement test logic
    // - Create LogRpcInvoker with mocked IncomingMessageDispatcher
    // - Start invoker in a separate thread
    // - Wait briefly for invoker to enter blocking receive
    // - Call thread.interrupt()
    // - Wait for thread to terminate (with timeout)
    // - Verify thread is no longer alive
    // - Verify closeConnections was called (socket closed, metrics logged)
    Assert.fail("Not yet implemented");
  }
}
