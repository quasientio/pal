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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
 * <p>Test specifications added for issue #539, awaiting implementation in #540:
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
  // Test specifications for issue #539 - Awaiting implementation in #540
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
  @Ignore("Awaiting implementation in #540")
  public void testGetRequestsDispatched_returnsCorrectCount() {
    // Given: Invoker that has dispatched some requests
    // When: getRequestsDispatched called
    // Then: Returns accurate count

    // TODO(#540): Implement test logic
    // - Dispatch multiple message types (EXEC, CONTROL, META, INTERCEPT)
    // - Verify getRequestsDispatched() returns the sum of all successful dispatches
    // - Verify individual getExecRequestsDispatched(), getControlRequestsDispatched(),
    //   getMetaRequestsDispatched(), getInterceptRequestsDispatched() match the aggregate
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #540")
  public void testGetRequestErrors_returnsCorrectCount() {
    // Given: Invoker that has encountered some errors
    // When: getRequestErrors called
    // Then: Returns accurate count

    // TODO(#540): Implement test logic
    // - Cause dispatch errors for multiple message types (EXEC, CONTROL, META, INTERCEPT)
    // - Verify getRequestErrors() returns the sum of all errors
    // - Verify individual getExecRequestErrors(), getControlRequestErrors(),
    //   getMetaRequestErrors(), getInterceptRequestErrors() match the aggregate
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #540")
  public void testAddMessageDispatchListener_addsListenerSuccessfully() {
    // Given: Invoker without listeners
    // When: addMessageDispatchListener called
    // Then: Listener added; receives dispatch events

    // TODO(#540): Implement test logic
    // - Create invoker with no listeners
    // - Add a listener via addMessageDispatchListener
    // - Dispatch a message
    // - Verify listener's onMessageDispatched was called with the correct message
    // - Verify multiple listeners can be added and all receive events
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #540")
  public void testRemoveMessageDispatchListener_removesListenerSuccessfully() {
    // Given: Invoker with registered listener
    // When: removeMessageDispatchListener called
    // Then: Listener removed; no longer receives events

    // TODO(#540): Implement test logic
    // - Create invoker and add a listener
    // - Verify listener receives events on dispatch
    // - Remove the listener via removeMessageDispatchListener
    // - Dispatch another message
    // - Verify listener does NOT receive the event after removal
    // - Verify removing a non-registered listener does not cause errors
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #540")
  public void testLogMessageDispatch_logsWithoutError() {
    // Given: Message dispatch event
    // When: logMessageDispatch called
    // Then: Completes without error; listeners notified

    // TODO(#540): Implement test logic
    // - Create invoker and prepare test messages
    // - Call logMessageDispatch(Message requestMsg, String responseId, long dispatchStart)
    // - Verify no exception is thrown
    // - Call logMessageDispatch(Message requestMsg, Message responseMessage, long dispatchStart)
    // - Verify no exception is thrown
    // - Call logMessageDispatch(String requestId, String responseId, long dispatchStart)
    // - Verify no exception is thrown
    // - Call logMessageDispatch(String requestId, long dispatchStart)
    // - Verify no exception is thrown
    // - (Optional) Verify logging output when debug level is enabled
    fail("Not yet implemented");
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
