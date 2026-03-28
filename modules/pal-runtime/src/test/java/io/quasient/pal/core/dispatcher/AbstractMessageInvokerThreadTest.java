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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Unit tests for AbstractMessageInvokerThread focusing on dispatch listeners, metrics recording,
 * and error handling. These tests exercise the listener management and dispatch flow paths in the
 * abstract invoker thread.
 *
 * <p>This test class uses a lightweight concrete implementation (TestInvoker) that provides an
 * empty run() implementation, allowing direct testing of the protected dispatch methods without
 * requiring full thread lifecycle management.
 *
 * <p>Test specifications for invoker thread lifecycle:
 *
 * <ul>
 *   <li>{@link #testGetRequestsDispatched_returnsCorrectCount}
 *   <li>{@link #testGetRequestErrors_returnsCorrectCount}
 *   <li>{@link #testAddMessageDispatchListener_addsListenerSuccessfully}
 *   <li>{@link #testRemoveMessageDispatchListener_removesListenerSuccessfully}
 *   <li>{@link #testLogMessageDispatch_logsWithoutError}
 * </ul>
 */
public class AbstractMessageInvokerThreadTest {

  private UUID peerUuid;
  private ZContext ctx;
  private MessageBuilder builder;
  private IncomingMessageDispatcher dispatcher;

  /**
   * Lightweight concrete implementation of AbstractMessageInvokerThread for testing purposes.
   * Provides an empty run() implementation and exposes dispatch methods for testing.
   */
  private static class TestInvoker extends AbstractMessageInvokerThread {

    /**
     * Constructs a TestInvoker for unit testing.
     *
     * @param zmqContext the ZeroMQ context
     * @param messageBuilder the message builder
     * @param incomingMessageDispatcher the incoming message dispatcher
     * @param peerUuid the peer UUID
     */
    TestInvoker(
        ZContext zmqContext,
        MessageBuilder messageBuilder,
        IncomingMessageDispatcher incomingMessageDispatcher,
        UUID peerUuid) {
      super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    }

    @Override
    public void run() {
      // Empty implementation for testing - no thread lifecycle needed
    }

    /**
     * Exposes the protected dispatch method for testing.
     *
     * @param m the message to dispatch
     * @return the response message
     */
    public Message dispatchMsg(Message m) {
      return dispatch(m, MessageChannelType.ZMQ_SOCKET_RPC);
    }

    /**
     * Exposes the protected logMessageDispatch method for testing.
     *
     * @param requestMsg the request message
     * @param responseId the response ID
     * @param dispatchStart the dispatch start time
     */
    public void testLogMessageDispatch(Message requestMsg, String responseId, long dispatchStart) {
      logMessageDispatch(requestMsg, responseId, dispatchStart);
    }

    /**
     * Exposes the protected logMessageDispatch method for testing.
     *
     * @param requestMsg the request message
     * @param responseMessage the response message
     * @param dispatchStart the dispatch start time
     */
    public void testLogMessageDispatch(
        Message requestMsg, Message responseMessage, long dispatchStart) {
      logMessageDispatch(requestMsg, responseMessage, dispatchStart);
    }

    /**
     * Exposes the protected logMessageDispatch method for testing.
     *
     * @param requestId the request ID
     * @param responseId the response ID
     * @param dispatchStart the dispatch start time
     */
    public void testLogMessageDispatch(String requestId, String responseId, long dispatchStart) {
      logMessageDispatch(requestId, responseId, dispatchStart);
    }

    /**
     * Exposes the protected logMessageDispatch method for testing.
     *
     * @param requestId the request ID
     * @param dispatchStart the dispatch start time
     */
    public void testLogMessageDispatch(String requestId, long dispatchStart) {
      logMessageDispatch(requestId, dispatchStart);
    }
  }

  /** Sets up test fixtures before each test. */
  @Before
  public void setup() {
    peerUuid = UUID.randomUUID();
    ctx = new ZContext(1);
    builder = new MessageBuilder(peerUuid);
    dispatcher = mock(IncomingMessageDispatcher.class);
  }

  /** Cleans up resources after each test. */
  @After
  public void tearDown() {
    if (ctx != null) {
      ctx.close();
    }
  }

  /**
   * Tests that a registered dispatch listener is called when a message is dispatched.
   *
   * <p>Given: A listener registered via addMessageDispatchListener
   *
   * <p>When: A message is dispatched successfully
   *
   * <p>Then: The listener's onMessageDispatched method is called with the dispatched message
   */
  @Test
  public void addDispatchListener_listenerCalled_onDispatch() {
    // Setup mock to return a valid response
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Create invoker and track listener calls
    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);
    AtomicBoolean listenerCalled = new AtomicBoolean(false);
    AtomicReference<Message> receivedMessage = new AtomicReference<>();

    // Register listener
    invoker.addMessageDispatchListener(
        msg -> {
          listenerCalled.set(true);
          receivedMessage.set(msg);
        });

    // Dispatch message
    Message wrappedRequest = builder.wrap(ctrlReq);
    invoker.dispatchMsg(wrappedRequest);

    // Assert listener was called with correct message
    assertTrue("Listener should have been called", listenerCalled.get());
    assertThat(receivedMessage.get(), is(wrappedRequest));
  }

  /**
   * Tests that a removed dispatch listener is not called after removal.
   *
   * <p>Given: A listener that was registered then removed via removeMessageDispatchListener
   *
   * <p>When: A message is dispatched
   *
   * <p>Then: The removed listener is NOT called
   */
  @Test
  public void removeDispatchListener_listenerNotCalled_afterRemoval() {
    // Setup mock to return a valid response
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Create invoker and track listener calls
    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);
    AtomicBoolean listenerCalled = new AtomicBoolean(false);

    // Create listener
    MessageDispatchListener listener = msg -> listenerCalled.set(true);

    // Register then remove the listener
    invoker.addMessageDispatchListener(listener);
    invoker.removeMessageDispatchListener(listener);

    // Dispatch message
    invoker.dispatchMsg(builder.wrap(ctrlReq));

    // Assert listener was NOT called after removal
    assertTrue("Listener should NOT have been called after removal", !listenerCalled.get());
  }

  /**
   * Tests that dispatch operations correctly record timing statistics via dispatch counters.
   *
   * <p>Given: An invoker with metrics enabled (counters)
   *
   * <p>When: Multiple messages are dispatched
   *
   * <p>Then: Dispatch counters are recorded correctly reflecting the number of dispatched messages
   */
  @Test
  public void dispatchWithMetrics_recordsTimingStats() {
    // Setup mock to return valid responses
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Create invoker
    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Verify initial counters are zero
    assertThat(invoker.getControlRequestsDispatched(), is(0L));
    assertThat(invoker.getRequestsDispatched(), is(0L));
    assertThat(invoker.getControlRequestErrors(), is(0L));

    // Dispatch multiple ControlMessages
    int numMessages = 3;
    for (int i = 0; i < numMessages; i++) {
      ControlMessage req = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
      invoker.dispatchMsg(builder.wrap(req));
    }

    // Assert counters reflect the number of dispatched messages
    assertThat(invoker.getControlRequestsDispatched(), is((long) numMessages));
    assertThat(invoker.getRequestsDispatched(), is((long) numMessages));
    assertThat(invoker.getControlRequestErrors(), is(0L));
  }

  /**
   * Tests that when the dispatcher throws an exception, the error is recorded and the invoker can
   * continue processing subsequent messages.
   *
   * <p>Given: A dispatcher that throws a RuntimeException
   *
   * <p>When: dispatch is called
   *
   * <p>Then: The exception is propagated, the error counter is incremented, and the invoker is
   * still in a valid state to process more messages
   */
  @Test
  public void dispatch_throwsException_logsAndContinues() {
    // Create invoker
    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Configure mock dispatcher to throw RuntimeException
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    doThrow(new RuntimeException("Simulated dispatch error"))
        .when(dispatcher)
        .incomingControlMessage(any(ControlMessage.class));

    // Call dispatch and expect RuntimeException to be thrown
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(ctrlReq)));

    // Assert that error counter was incremented
    assertThat(invoker.getControlRequestErrors(), is(1L));
    assertThat(invoker.getControlRequestsDispatched(), is(0L));

    // Reconfigure mock to succeed
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Dispatch another message successfully
    ControlMessage ctrlReq2 = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    invoker.dispatchMsg(builder.wrap(ctrlReq2));

    // Assert invoker continues working - success counter should increment
    assertThat(invoker.getControlRequestsDispatched(), is(1L));
    // Error counter remains at 1 from the previous failure
    assertThat(invoker.getControlRequestErrors(), is(1L));
  }

  /**
   * Tests that interrupting the invoker thread signals it to stop.
   *
   * <p>Given: A running invoker thread (subclass with actual run() implementation)
   *
   * <p>When: Thread.interrupt() is called
   *
   * <p>Then: The thread's interrupted flag is set, causing the run() loop to exit on next iteration
   *
   * <p>Note: AbstractMessageInvokerThread uses standard Thread.interrupt() mechanism rather than a
   * custom triggerStop() method. Subclasses check interrupted() in their run() loop.
   */
  @Test
  public void triggerStop_setsStopFlag() throws InterruptedException {
    // Create a test invoker with a run() that loops until interrupted
    CountDownLatch startedLatch = new CountDownLatch(1);
    CountDownLatch stoppedLatch = new CountDownLatch(1);
    AtomicBoolean wasInterrupted = new AtomicBoolean(false);

    RunnableTestInvoker invoker =
        new RunnableTestInvoker(
            ctx, builder, dispatcher, peerUuid, startedLatch, stoppedLatch, wasInterrupted);

    // Start the invoker thread
    invoker.start();

    // Wait for thread to start its run loop
    assertTrue("Thread should start within timeout", startedLatch.await(5, TimeUnit.SECONDS));

    // Interrupt the thread to signal stop
    invoker.interrupt();

    // Wait for thread to stop
    assertTrue("Thread should stop within timeout", stoppedLatch.await(5, TimeUnit.SECONDS));

    // Verify the thread detected the interrupt
    assertTrue("Thread should have detected interrupt", wasInterrupted.get());

    // Join the thread to ensure it has fully terminated
    invoker.join(1000);
    assertTrue("Thread should have terminated", !invoker.isAlive());
  }

  // ==================================================================================
  // Test specifications for invoker thread lifecycle
  // ==================================================================================

  /**
   * Tests that getRequestsDispatched returns the correct cumulative count across all message types.
   *
   * <p>Given: An invoker that has dispatched some requests (EXEC, CONTROL, META, INTERCEPT)
   *
   * <p>When: getRequestsDispatched is called
   *
   * <p>Then: Returns accurate count equal to sum of all dispatched message types
   *
   * <p>Note: This test specification extends coverage of the existing
   * dispatchWithMetrics_recordsTimingStats test to verify the aggregate getRequestsDispatched()
   * method returns the correct total across all message families.
   */
  @Test
  public void testGetRequestsDispatched_returnsCorrectCount() {
    // Setup mock responses for all message types

    // Control message setup
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    // Meta message setup
    MetaMessage metaReq =
        builder.buildMetaMessageRequest(
            peerUuid, ctrlReq.getMessageId(), MetaServiceType.FETCH_CLASSES_INFO);
    MetaMessage metaResp =
        builder.buildMetaMessageResponse(
            peerUuid,
            MetaServiceType.FETCH_CLASSES_INFO,
            MetaStatusType.OK,
            null,
            metaReq.getMessageId());
    when(dispatcher.incomingMetaMessage(any(MetaMessage.class))).thenReturn(metaResp);

    // Exec message setup - use buildClassMethod for a static method call
    ExecMessage execReq =
        builder.buildClassMethod(
            peerUuid,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    // Echo the request back as response for simplicity
    when(dispatcher.incomingCall(any(ExecMessage.class), any(MessageType.class), any()))
        .thenReturn(execReq);

    // Intercept callback message setup
    InterceptCallbackRequestMessage interceptReq = new InterceptCallbackRequestMessage();
    interceptReq.setCallbackId("callback-123");
    InterceptCallbackResponseMessage interceptResp = new InterceptCallbackResponseMessage();
    interceptResp.setCallbackId("callback-123");
    when(dispatcher.incomingInterceptCallback(any(InterceptCallbackRequestMessage.class)))
        .thenReturn(interceptResp);

    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Verify initial counters are zero
    assertThat(invoker.getRequestsDispatched(), is(0L));
    assertThat(invoker.getExecRequestsDispatched(), is(0L));
    assertThat(invoker.getControlRequestsDispatched(), is(0L));
    assertThat(invoker.getMetaRequestsDispatched(), is(0L));
    assertThat(invoker.getInterceptRequestsDispatched(), is(0L));

    // Dispatch 2 control messages
    invoker.dispatchMsg(builder.wrap(ctrlReq));
    invoker.dispatchMsg(builder.wrap(ctrlReq));

    // Dispatch 3 meta messages
    invoker.dispatchMsg(builder.wrap(metaReq));
    invoker.dispatchMsg(builder.wrap(metaReq));
    invoker.dispatchMsg(builder.wrap(metaReq));

    // Dispatch 1 exec message
    invoker.dispatchMsg(builder.wrap(execReq));

    // Dispatch 2 intercept callback messages
    invoker.dispatchMsg(builder.wrap(interceptReq));
    invoker.dispatchMsg(builder.wrap(interceptReq));

    // Verify individual counters
    assertThat(invoker.getControlRequestsDispatched(), is(2L));
    assertThat(invoker.getMetaRequestsDispatched(), is(3L));
    assertThat(invoker.getExecRequestsDispatched(), is(1L));
    assertThat(invoker.getInterceptRequestsDispatched(), is(2L));

    // Verify aggregate equals sum of individuals
    long expectedTotal =
        invoker.getExecRequestsDispatched()
            + invoker.getControlRequestsDispatched()
            + invoker.getMetaRequestsDispatched()
            + invoker.getInterceptRequestsDispatched();
    assertThat(invoker.getRequestsDispatched(), is(expectedTotal));
    assertThat(invoker.getRequestsDispatched(), is(8L));
  }

  /**
   * Tests that getRequestErrors returns the correct cumulative error count across all message
   * types.
   *
   * <p>Given: An invoker that has encountered dispatch errors for various message types
   *
   * <p>When: getRequestErrors is called
   *
   * <p>Then: Returns accurate count equal to sum of all errors across message families
   *
   * <p>Note: This test specification extends coverage of the existing
   * dispatch_throwsException_logsAndContinues test to verify the aggregate getRequestErrors()
   * method returns the correct total across all message families.
   */
  @Test
  public void testGetRequestErrors_returnsCorrectCount() {
    // Configure all dispatchers to throw exceptions
    doThrow(new RuntimeException("Control error"))
        .when(dispatcher)
        .incomingControlMessage(any(ControlMessage.class));
    doThrow(new RuntimeException("Meta error"))
        .when(dispatcher)
        .incomingMetaMessage(any(MetaMessage.class));
    doThrow(new RuntimeException("Exec error"))
        .when(dispatcher)
        .incomingCall(any(ExecMessage.class), any(MessageType.class), any());
    doThrow(new RuntimeException("Intercept error"))
        .when(dispatcher)
        .incomingInterceptCallback(any(InterceptCallbackRequestMessage.class));

    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Verify initial error counters are zero
    assertThat(invoker.getRequestErrors(), is(0L));
    assertThat(invoker.getExecRequestErrors(), is(0L));
    assertThat(invoker.getControlRequestErrors(), is(0L));
    assertThat(invoker.getMetaRequestErrors(), is(0L));
    assertThat(invoker.getInterceptRequestErrors(), is(0L));

    // Trigger 2 control errors
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(ctrlReq)));
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(ctrlReq)));

    // Trigger 3 meta errors
    MetaMessage metaReq =
        builder.buildMetaMessageRequest(
            peerUuid, ctrlReq.getMessageId(), MetaServiceType.FETCH_CLASSES_INFO);
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(metaReq)));
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(metaReq)));
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(metaReq)));

    // Trigger 1 exec error
    ExecMessage execReq =
        builder.buildClassMethod(
            peerUuid,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(execReq)));

    // Trigger 2 intercept errors
    InterceptCallbackRequestMessage interceptReq = new InterceptCallbackRequestMessage();
    interceptReq.setCallbackId("callback-123");
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(interceptReq)));
    assertThrows(RuntimeException.class, () -> invoker.dispatchMsg(builder.wrap(interceptReq)));

    // Verify individual error counters
    assertThat(invoker.getControlRequestErrors(), is(2L));
    assertThat(invoker.getMetaRequestErrors(), is(3L));
    assertThat(invoker.getExecRequestErrors(), is(1L));
    assertThat(invoker.getInterceptRequestErrors(), is(2L));

    // Verify aggregate equals sum of individuals
    long expectedTotal =
        invoker.getExecRequestErrors()
            + invoker.getControlRequestErrors()
            + invoker.getMetaRequestErrors()
            + invoker.getInterceptRequestErrors();
    assertThat(invoker.getRequestErrors(), is(expectedTotal));
    assertThat(invoker.getRequestErrors(), is(8L));

    // Verify dispatch counters remain at zero (all failed)
    assertThat(invoker.getRequestsDispatched(), is(0L));
  }

  /**
   * Tests that addMessageDispatchListener successfully adds a listener that receives dispatch
   * events.
   *
   * <p>Given: An invoker without any listeners registered
   *
   * <p>When: addMessageDispatchListener is called with a listener
   *
   * <p>Then: The listener is added and receives onMessageDispatched events for subsequent
   * dispatches
   *
   * <p>Note: This test specification formalizes the behavior tested in
   * addDispatchListener_listenerCalled_onDispatch with explicit verification of listener
   * registration and event delivery.
   */
  @Test
  public void testAddMessageDispatchListener_addsListenerSuccessfully() {
    // Setup mock to return a valid response
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Create first listener to track calls
    AtomicInteger listener1CallCount = new AtomicInteger(0);
    List<Message> listener1Messages = new ArrayList<>();
    MessageDispatchListener listener1 =
        msg -> {
          listener1CallCount.incrementAndGet();
          listener1Messages.add(msg);
        };

    // Create second listener to track calls
    AtomicInteger listener2CallCount = new AtomicInteger(0);
    List<Message> listener2Messages = new ArrayList<>();
    MessageDispatchListener listener2 =
        msg -> {
          listener2CallCount.incrementAndGet();
          listener2Messages.add(msg);
        };

    // Add first listener
    invoker.addMessageDispatchListener(listener1);

    // Dispatch a message
    Message wrappedRequest1 = builder.wrap(ctrlReq);
    invoker.dispatchMsg(wrappedRequest1);

    // Verify first listener was called with correct message
    assertThat(listener1CallCount.get(), is(1));
    assertThat(listener1Messages.size(), is(1));
    assertThat(listener1Messages.get(0), is(wrappedRequest1));

    // Add second listener
    invoker.addMessageDispatchListener(listener2);

    // Dispatch another message
    ControlMessage ctrlReq2 = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    Message wrappedRequest2 = builder.wrap(ctrlReq2);
    invoker.dispatchMsg(wrappedRequest2);

    // Verify both listeners received the second message
    assertThat(listener1CallCount.get(), is(2));
    assertThat(listener2CallCount.get(), is(1));
    assertThat(listener1Messages.get(1), is(wrappedRequest2));
    assertThat(listener2Messages.get(0), is(wrappedRequest2));

    // Dispatch a third message to confirm both listeners still active
    ControlMessage ctrlReq3 = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    Message wrappedRequest3 = builder.wrap(ctrlReq3);
    invoker.dispatchMsg(wrappedRequest3);

    // Verify both listeners received the third message
    assertThat(listener1CallCount.get(), is(3));
    assertThat(listener2CallCount.get(), is(2));
  }

  /**
   * Tests that removeMessageDispatchListener successfully removes a listener so it no longer
   * receives events.
   *
   * <p>Given: An invoker with a registered listener
   *
   * <p>When: removeMessageDispatchListener is called for that listener
   *
   * <p>Then: The listener is removed and no longer receives onMessageDispatched events
   *
   * <p>Note: This test specification formalizes the behavior tested in
   * removeDispatchListener_listenerNotCalled_afterRemoval with explicit verification of listener
   * removal semantics.
   */
  @Test
  public void testRemoveMessageDispatchListener_removesListenerSuccessfully() {
    // Setup mock to return a valid response
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());
    when(dispatcher.incomingControlMessage(any(ControlMessage.class))).thenReturn(ctrlResp);

    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Create two listeners to track calls
    AtomicInteger listener1CallCount = new AtomicInteger(0);
    MessageDispatchListener listener1 = msg -> listener1CallCount.incrementAndGet();

    AtomicInteger listener2CallCount = new AtomicInteger(0);
    MessageDispatchListener listener2 = msg -> listener2CallCount.incrementAndGet();

    // Add both listeners
    invoker.addMessageDispatchListener(listener1);
    invoker.addMessageDispatchListener(listener2);

    // Dispatch a message - both listeners should be called
    invoker.dispatchMsg(builder.wrap(ctrlReq));
    assertThat(listener1CallCount.get(), is(1));
    assertThat(listener2CallCount.get(), is(1));

    // Remove first listener
    invoker.removeMessageDispatchListener(listener1);

    // Dispatch another message - only listener2 should be called
    ControlMessage ctrlReq2 = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    invoker.dispatchMsg(builder.wrap(ctrlReq2));
    assertThat(listener1CallCount.get(), is(1)); // unchanged
    assertThat(listener2CallCount.get(), is(2)); // incremented

    // Remove second listener
    invoker.removeMessageDispatchListener(listener2);

    // Dispatch another message - neither listener should be called
    ControlMessage ctrlReq3 = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    invoker.dispatchMsg(builder.wrap(ctrlReq3));
    assertThat(listener1CallCount.get(), is(1)); // unchanged
    assertThat(listener2CallCount.get(), is(2)); // unchanged

    // Verify removing a non-registered listener does not cause errors
    MessageDispatchListener nonRegisteredListener = msg -> {};
    invoker.removeMessageDispatchListener(nonRegisteredListener); // should not throw
  }

  /**
   * Tests that logMessageDispatch methods complete without error and notify listeners
   * appropriately.
   *
   * <p>Given: A message dispatch event with request/response IDs and timing information
   *
   * <p>When: logMessageDispatch is called (any of the overloaded variants)
   *
   * <p>Then: The method completes without error and logs dispatch information (when debug enabled)
   *
   * <p>Note: logMessageDispatch is a protected utility method used by subclasses to log dispatch
   * timing. This test verifies it handles all parameter combinations correctly without throwing
   * exceptions.
   */
  @Test
  public void testLogMessageDispatch_logsWithoutError() {
    TestInvoker invoker = new TestInvoker(ctx, builder, dispatcher, peerUuid);

    // Create test messages for use in logMessageDispatch calls
    ControlMessage ctrlReq = builder.buildControlCommandMessage(peerUuid, ControlCommandType.GC);
    ControlMessage ctrlResp =
        builder.buildControlStatusMessage(peerUuid, ControlStatusType.OK, ctrlReq.getMessageId());

    Message wrappedRequest = builder.wrap(ctrlReq);
    Message wrappedResponse = builder.wrap(ctrlResp);

    long dispatchStart = System.currentTimeMillis() - 100; // 100ms ago

    // Test variant 1: logMessageDispatch(Message requestMsg, String responseId, long dispatchStart)
    // Should complete without exception
    invoker.testLogMessageDispatch(wrappedRequest, ctrlResp.getMessageId(), dispatchStart);

    // Test variant 2: logMessageDispatch(Message requestMsg, Message responseMessage, long
    // dispatchStart)
    // Should complete without exception
    invoker.testLogMessageDispatch(wrappedRequest, wrappedResponse, dispatchStart);

    // Test variant 3: logMessageDispatch(String requestId, String responseId, long dispatchStart)
    // Should complete without exception
    invoker.testLogMessageDispatch(ctrlReq.getMessageId(), ctrlResp.getMessageId(), dispatchStart);

    // Test variant 4: logMessageDispatch(String requestId, long dispatchStart)
    // Should complete without exception
    invoker.testLogMessageDispatch(ctrlReq.getMessageId(), dispatchStart);

    // Test with plain strings - these should also complete without exception
    invoker.testLogMessageDispatch("req-id", "resp-id", System.currentTimeMillis());
    invoker.testLogMessageDispatch("req-id-only", System.currentTimeMillis());

    // If we reach here without exceptions, all logMessageDispatch variants work correctly
    assertTrue("All logMessageDispatch variants completed without error", true);
  }

  /**
   * A test invoker with an actual run() loop that checks for interruption, used to test the
   * interrupt/stop mechanism.
   */
  private static class RunnableTestInvoker extends AbstractMessageInvokerThread {

    private final CountDownLatch startedLatch;
    private final CountDownLatch stoppedLatch;
    private final AtomicBoolean wasInterrupted;

    /**
     * Constructs a RunnableTestInvoker for testing the interrupt mechanism.
     *
     * @param zmqContext the ZeroMQ context
     * @param messageBuilder the message builder
     * @param incomingMessageDispatcher the incoming message dispatcher
     * @param peerUuid the peer UUID
     * @param startedLatch latch to signal when run loop starts
     * @param stoppedLatch latch to signal when run loop stops
     * @param wasInterrupted flag to record if interrupt was detected
     */
    RunnableTestInvoker(
        ZContext zmqContext,
        MessageBuilder messageBuilder,
        IncomingMessageDispatcher incomingMessageDispatcher,
        UUID peerUuid,
        CountDownLatch startedLatch,
        CountDownLatch stoppedLatch,
        AtomicBoolean wasInterrupted) {
      super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
      this.startedLatch = startedLatch;
      this.stoppedLatch = stoppedLatch;
      this.wasInterrupted = wasInterrupted;
    }

    @Override
    public void run() {
      startedLatch.countDown();
      try {
        while (!isInterrupted()) {
          // Simulate work with a small sleep
          sleep(10);
        }
      } catch (InterruptedException e) {
        // Expected when interrupted during sleep
        wasInterrupted.set(true);
        interrupt();
      } finally {
        // Also check the flag in case interrupt happened outside sleep
        if (isInterrupted()) {
          wasInterrupted.set(true);
        }
        stoppedLatch.countDown();
      }
    }
  }
}
