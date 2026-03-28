/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.dispatcher;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.util.UuidUtils;
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
import org.junit.Before;
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

  // ===== Error Handling Tests =====
  // Tests for error handling paths in LogRpcInvoker.

  /**
   * Tests that run() continues processing after receiving an invalid JSON-RPC message.
   *
   * <p>This verifies that malformed JSON-RPC messages are logged and skipped without terminating
   * the invoker thread, allowing it to continue processing subsequent valid messages.
   */
  @Test
  public void run_invalidJsonRpcMessage_logsErrorAndContinues() throws Exception {
    // Start invoker thread
    execService.execute(logRpcInvoker);

    // Create latch for the valid message
    CountDownLatch latch = new CountDownLatch(1);
    MessageDispatchListener listener = message -> latch.countDown();
    logRpcInvoker.addMessageDispatchListener(listener);

    // Send malformed JSON-RPC message (truncated JSON with missing required fields)
    Headers emptyHeaders = new RecordHeaders();
    byte[] invalidJson = "{\"jsonrpc\":\"2.0\",\"id\":\"x\"".getBytes(UTF_8);
    InboundLogMsg invalidMsg =
        new InboundLogMsg(0, MessageFormatType.JSON, emptyHeaders, invalidJson);
    invalidMsg.send(dealerSocket);

    // Wait a bit for the invalid message to be processed
    TimeUnit.MILLISECONDS.sleep(50);

    // Send valid binary message
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    InboundLogMsg validMsg =
        new InboundLogMsg(
            1,
            MessageFormatType.BINARY,
            emptyHeaders,
            ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    validMsg.send(dealerSocket);

    // Wait for valid message to be dispatched
    boolean dispatched = latch.await(5, TimeUnit.SECONDS);

    // Verify the invoker continued and processed the valid message
    assertThat("Valid message should have been dispatched", dispatched, is(true));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(1L));
  }

  /**
   * Tests that run() continues processing after receiving a corrupted binary message.
   *
   * <p>This verifies that binary messages that fail Colfer unmarshalling are logged and skipped
   * without terminating the invoker thread.
   */
  @Test
  public void run_binaryParseException_logsErrorAndContinues() throws Exception {
    // Start invoker thread
    execService.execute(logRpcInvoker);

    // Create latch for the valid message
    CountDownLatch latch = new CountDownLatch(1);
    MessageDispatchListener listener = message -> latch.countDown();
    logRpcInvoker.addMessageDispatchListener(listener);

    // Send corrupted binary message (random bytes that fail Colfer unmarshal)
    Headers emptyHeaders = new RecordHeaders();
    byte[] corruptedBinary = new byte[] {(byte) 0xFF, 0x01, 0x02, 0x03, 0x04, 0x05};
    InboundLogMsg corruptedMsg =
        new InboundLogMsg(0, MessageFormatType.BINARY, emptyHeaders, corruptedBinary);
    corruptedMsg.send(dealerSocket);

    // Wait a bit for the corrupted message to be processed
    TimeUnit.MILLISECONDS.sleep(50);

    // Send valid binary message
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    InboundLogMsg validMsg =
        new InboundLogMsg(
            1,
            MessageFormatType.BINARY,
            emptyHeaders,
            ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    validMsg.send(dealerSocket);

    // Wait for valid message to be dispatched
    boolean dispatched = latch.await(5, TimeUnit.SECONDS);

    // Verify the invoker continued and processed the valid message
    assertThat("Valid message should have been dispatched", dispatched, is(true));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(1L));
  }

  /**
   * Tests that run() handles missing producer-id header gracefully.
   *
   * <p>This verifies that when the producer-id header is missing from a JSON-RPC message, the
   * invoker logs the error but continues processing with a null fromPeerUuid. The message still
   * gets dispatched despite the missing header.
   */
  @Test
  public void run_missingProducerId_usesDefaultValue() throws Exception {
    // Start invoker thread
    execService.execute(logRpcInvoker);

    // Create latch for the message
    CountDownLatch latch = new CountDownLatch(1);
    MessageDispatchListener listener = message -> latch.countDown();
    logRpcInvoker.addMessageDispatchListener(listener);

    // Create a valid JSON-RPC message WITHOUT producer-id header
    // This is a valid JSON-RPC constructor call
    Headers emptyHeaders = new RecordHeaders(); // No producer-id header
    String validJsonRpc =
        "{\"jsonrpc\":\"2.0\",\"id\":\"test-1\",\"method\":\"new\","
            + "\"params\":{\"type\":\"java.lang.String\",\"args\":[]}}";
    byte[] jsonBody = validJsonRpc.getBytes(UTF_8);
    InboundLogMsg msgWithoutProducerId =
        new InboundLogMsg(0, MessageFormatType.JSON, emptyHeaders, jsonBody);
    msgWithoutProducerId.send(dealerSocket);

    // Wait for message to be dispatched
    boolean dispatched = latch.await(5, TimeUnit.SECONDS);

    // Verify message was dispatched despite missing producer-id header
    assertThat("Message should have been dispatched", dispatched, is(true));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(1L));
  }

  /**
   * Tests that dispatch() handles exceptions from IncomingMessageDispatcher gracefully.
   *
   * <p>This verifies that when the IncomingMessageDispatcher throws an exception during JSON-RPC
   * message dispatch, the error is logged and the invoker continues processing.
   */
  @Test
  public void dispatch_throwsException_handledGracefully() throws Exception {
    // Create separate context and invoker with a different mock
    ZContext testContext = createContext();
    String testAddress = "inproc://test_dispatch_ex_" + UUID.randomUUID();
    Socket testDealerSocket = testContext.createSocket(SocketType.DEALER);
    testDealerSocket.bind(testAddress);
    MessageBuilder testMsgBuilder = new MessageBuilder(peerUuid);

    // Create mock that throws on first call, succeeds on second
    IncomingMessageDispatcher testDispatcher = mock(IncomingMessageDispatcher.class);
    RuntimeException dispatchException = new RuntimeException("Dispatch failed");
    when(testDispatcher.incomingCall(any(), any(), any()))
        .thenThrow(dispatchException)
        .thenAnswer(
            invocation -> {
              ExecMessage incomingMsg = (ExecMessage) invocation.getArguments()[0];
              return testMsgBuilder.buildReturnValue(
                  "", String.class.getConstructor(), null, false, incomingMsg.getMessageId());
            });

    LogRpcInvoker testInvoker =
        new LogRpcInvoker(testContext, testMsgBuilder, testAddress, testDispatcher, peerUuid);

    // Start invoker thread
    Thread invokerThread = new Thread(testInvoker);
    invokerThread.start();

    // Create latch for the second (successful) message
    CountDownLatch latch = new CountDownLatch(1);
    MessageDispatchListener listener = message -> latch.countDown();
    testInvoker.addMessageDispatchListener(listener);

    // Create valid JSON-RPC messages with required producer-id header
    Headers headersWithProducerId = new RecordHeaders();
    headersWithProducerId.add("producer-id", UuidUtils.toBytes(peerUuid));

    // Send first message (will throw exception during dispatch)
    String jsonRpc1 =
        "{\"jsonrpc\":\"2.0\",\"id\":\"test-1\",\"method\":\"new\","
            + "\"params\":{\"type\":\"java.lang.String\",\"args\":[]}}";
    InboundLogMsg msg1 =
        new InboundLogMsg(
            0, MessageFormatType.JSON, headersWithProducerId, jsonRpc1.getBytes(UTF_8));
    msg1.send(testDealerSocket);

    // Wait for first message to be processed (with exception)
    TimeUnit.MILLISECONDS.sleep(100);

    // Send second message (should succeed)
    String jsonRpc2 =
        "{\"jsonrpc\":\"2.0\",\"id\":\"test-2\",\"method\":\"new\","
            + "\"params\":{\"type\":\"java.lang.String\",\"args\":[]}}";
    InboundLogMsg msg2 =
        new InboundLogMsg(
            1, MessageFormatType.JSON, headersWithProducerId, jsonRpc2.getBytes(UTF_8));
    msg2.send(testDealerSocket);

    // Wait for second message to be dispatched
    boolean dispatched = latch.await(5, TimeUnit.SECONDS);

    // Verify: 2 dispatch calls attempted, invoker continued after first exception
    verify(testDispatcher, times(2)).incomingCall(any(), any(), any());
    assertThat("Second message should have been dispatched", dispatched, is(true));
    // Only one successful dispatch (second one)
    assertThat(testInvoker.getExecRequestsDispatched(), is(1L));
    // One error from the first dispatch
    assertThat(testInvoker.getExecRequestErrors(), is(1L));

    // Cleanup
    closeContext(testContext);
    invokerThread.join(2000);
  }

  /**
   * Tests that closeConnections() handles already-closed socket without throwing exception.
   *
   * <p>This verifies that calling closeConnections() when the socket is already closed (e.g., from
   * ZContext termination) does not throw an exception.
   */
  @Test
  public void closeConnections_socketAlreadyClosed_noException() throws Exception {
    // Create a separate context and invoker for this test to avoid affecting other tests
    ZContext testContext = createContext();
    Socket testDealerSocket = testContext.createSocket(SocketType.DEALER);
    String testAddress = "inproc://test_close_" + UUID.randomUUID();
    testDealerSocket.bind(testAddress);

    IncomingMessageDispatcher testDispatcher = mock(IncomingMessageDispatcher.class);
    MessageBuilder testMsgBuilder = new MessageBuilder(peerUuid);

    LogRpcInvoker testInvoker =
        new LogRpcInvoker(testContext, testMsgBuilder, testAddress, testDispatcher, peerUuid);

    // Start invoker in a separate thread
    Thread invokerThread = new Thread(testInvoker);
    invokerThread.start();

    // Wait for socket to be initialized in run()
    TimeUnit.MILLISECONDS.sleep(50);

    // Close the context which closes all sockets
    testContext.close();

    // Wait for thread to exit naturally (due to ZMQException ETERM)
    invokerThread.join(2000);

    // Verify the thread exited gracefully (no exception should propagate)
    assertThat("Invoker thread should have terminated", invokerThread.isAlive(), is(false));
  }

  /**
   * Tests that run() exits gracefully when the thread is interrupted.
   *
   * <p>This verifies that the invoker thread responds to interruption by breaking out of its main
   * loop and cleaning up resources.
   */
  @Test
  public void run_interrupted_exitsGracefully() throws Exception {
    // Create a separate context and invoker for this test
    ZContext testContext = createContext();
    Socket testDealerSocket = testContext.createSocket(SocketType.DEALER);
    String testAddress = "inproc://test_interrupt_" + UUID.randomUUID();
    testDealerSocket.bind(testAddress);

    IncomingMessageDispatcher testDispatcher = mock(IncomingMessageDispatcher.class);
    MessageBuilder testMsgBuilder = new MessageBuilder(peerUuid);

    LogRpcInvoker testInvoker =
        new LogRpcInvoker(testContext, testMsgBuilder, testAddress, testDispatcher, peerUuid);

    // Start invoker in a separate thread
    Thread invokerThread = new Thread(testInvoker);
    invokerThread.start();

    // Wait for invoker to enter blocking receive
    TimeUnit.MILLISECONDS.sleep(100);

    // Verify thread is running
    assertThat("Invoker thread should be alive", invokerThread.isAlive(), is(true));

    // Interrupt the thread - this will cause ZMQException with EINTR
    invokerThread.interrupt();

    // Wait for thread to terminate (with timeout)
    invokerThread.join(2000);

    // Verify thread has exited gracefully
    assertThat("Invoker thread should have terminated", invokerThread.isAlive(), is(false));

    // Cleanup
    closeContext(testContext);
  }

  // ===== Acceptance Criteria Tests =====

  /**
   * [TEST:LogRpcInvokerTest.testRun_processesMessagesFromQueue]
   *
   * <p>Tests that the run() method correctly processes messages from the ZMQ queue.
   *
   * <p>Given: LogRpcInvoker with messages in queue (InboundLogMsg containing ExecMessage)
   *
   * <p>When: run() executes
   *
   * <p>Then: Messages are received, parsed (binary or JSON format), and dispatched; responses are
   * generated via the IncomingMessageDispatcher
   */
  @Test
  public void testRun_processesMessagesFromQueue() throws Exception {
    // Given: LogRpcInvoker with messages in queue
    execService.execute(logRpcInvoker);

    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    CountDownLatch latch = new CountDownLatch(1);
    MessageDispatchListener listener = message -> latch.countDown();
    logRpcInvoker.addMessageDispatchListener(listener);

    // When: run() executes and processes messages
    int fakeOffset = 0;
    Headers emptyHeaders = new RecordHeaders();
    InboundLogMsg msg =
        new InboundLogMsg(
            fakeOffset,
            MessageFormatType.BINARY,
            emptyHeaders,
            ColferUtils.toBytes(msgBuilder.wrap(invokable)));
    msg.send(dealerSocket);

    // Then: Messages are processed; responses generated
    latch.await();

    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());
    assertThat(execMessageReplies.size(), is(1));
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(execMessageReplies.get(0).getResponseToId(), is(invokable.getMessageId()));
  }

  /**
   * [TEST:LogRpcInvokerTest.testRun_handlesInterruptionGracefully]
   *
   * <p>Tests that the run() method exits gracefully when the thread is interrupted.
   *
   * <p>Given: Running LogRpcInvoker processing messages in run() loop
   *
   * <p>When: Thread interrupted via interrupt() call
   *
   * <p>Then: Exits gracefully without error; closeConnections() called; no exceptions thrown
   */
  @Test
  public void testRun_handlesInterruptionGracefully() throws Exception {
    // Given: Running LogRpcInvoker
    ZContext testContext = createContext();
    Socket testDealerSocket = testContext.createSocket(SocketType.DEALER);
    String testAddress = "inproc://test_interrupt_ac_" + UUID.randomUUID();
    testDealerSocket.bind(testAddress);

    IncomingMessageDispatcher testDispatcher = mock(IncomingMessageDispatcher.class);
    MessageBuilder testMsgBuilder = new MessageBuilder(peerUuid);

    LogRpcInvoker testInvoker =
        new LogRpcInvoker(testContext, testMsgBuilder, testAddress, testDispatcher, peerUuid);

    Thread invokerThread = new Thread(testInvoker);
    invokerThread.start();

    // Wait for invoker to enter blocking receive
    TimeUnit.MILLISECONDS.sleep(100);

    // Verify thread is running
    assertThat("Invoker thread should be alive", invokerThread.isAlive(), is(true));

    // When: Thread interrupted
    invokerThread.interrupt();

    // Then: Exits gracefully without error
    invokerThread.join(2000);
    assertThat("Invoker thread should have terminated", invokerThread.isAlive(), is(false));

    // Cleanup
    closeContext(testContext);
  }

  /**
   * [TEST:LogRpcInvokerTest.testCloseConnections_closesAllResources]
   *
   * <p>Tests that closeConnections() properly releases all ZMQ resources.
   *
   * <p>Given: LogRpcInvoker with open ZMQ socket connection
   *
   * <p>When: closeConnections() called
   *
   * <p>Then: All resources released; socket is closed; superclass closeConnections() invoked
   */
  @Test
  public void testCloseConnections_closesAllResources() throws Exception {
    // Given: LogRpcInvoker with open connections
    ZContext testContext = createContext();
    Socket testDealerSocket = testContext.createSocket(SocketType.DEALER);
    String testAddress = "inproc://test_close_ac_" + UUID.randomUUID();
    testDealerSocket.bind(testAddress);

    IncomingMessageDispatcher testDispatcher = mock(IncomingMessageDispatcher.class);
    MessageBuilder testMsgBuilder = new MessageBuilder(peerUuid);

    LogRpcInvoker testInvoker =
        new LogRpcInvoker(testContext, testMsgBuilder, testAddress, testDispatcher, peerUuid);

    Thread invokerThread = new Thread(testInvoker);
    invokerThread.start();

    // Wait for socket to be initialized in run()
    TimeUnit.MILLISECONDS.sleep(50);

    // When: closeConnections called (via context close which closes all sockets)
    testContext.close();

    // Then: All resources released (thread exits naturally due to ZMQException ETERM)
    invokerThread.join(2000);
    assertThat("Invoker thread should have terminated", invokerThread.isAlive(), is(false));
  }
}
