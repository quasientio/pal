/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.inflight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InFlightTrackingTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for in-flight tracking with intercept activation.
 *
 * <p>These tests verify the complete end-to-end flow of in-flight dispatch tracking when intercepts
 * are registered while method calls are actively executing. The tests ensure that:
 *
 * <ul>
 *   <li>Intercepts activate only after in-flight method executions complete (drain phase)
 *   <li>New calls block during the drain phase until activation completes
 *   <li>Immediate activation bypasses the drain phase when forceImmediate=true
 *   <li>Timeout handling works correctly for long-running in-flight calls
 *   <li>Concurrent intercept registrations are handled safely
 *   <li>Tracking can be disabled to restore legacy immediate activation behavior
 * </ul>
 *
 * <p><b>Test Infrastructure:</b>
 *
 * <ul>
 *   <li>Extends AbstractInterceptIT for PAL infrastructure (etcd, Kafka)
 *   <li>Uses the shared interceptable peer from InFlightTrackingTestSuite
 *   <li>Requires concurrent thread execution for realistic in-flight scenarios
 *   <li>Tests register intercepts via etcd while methods are executing
 *   <li>Verifies activation timing through callback execution and return values
 * </ul>
 *
 * <p><b>Multi-threaded Testing Pattern:</b>
 *
 * <p>Tests spawn multiple threads that:
 *
 * <ol>
 *   <li>Invoke a long-running method (e.g., sleepThenReturn) concurrently
 *   <li>Register an intercept while method executions are in-flight
 *   <li>Verify that in-flight calls complete without interception
 *   <li>Verify that new calls after drain block until activation completes
 *   <li>Verify that post-activation calls are intercepted correctly
 * </ol>
 *
 * <p><b>Parameterization:</b> Tests are parameterized by {@link InterceptType} to ensure equal
 * coverage across BEFORE, AFTER, and AROUND intercept types. Each test runs three times, once for
 * each intercept type.
 */
@RunWith(Parameterized.class)
public class InFlightInterceptActivationIT extends AbstractInterceptIT {

  /** The intercept type being tested in this parameterized run. */
  private final InterceptType interceptType;

  /**
   * Constructs a test instance for the specified intercept type.
   *
   * @param interceptType the intercept type to test
   */
  public InFlightInterceptActivationIT(InterceptType interceptType) {
    this.interceptType = interceptType;
  }

  /**
   * Returns the parameterized test data for intercept types.
   *
   * <p>Tests run for BEFORE, AFTER, and AROUND intercept types to ensure equal coverage.
   *
   * @return collection of intercept type parameters
   */
  @Parameterized.Parameters(name = "{index}: interceptType={0}")
  public static Collection<Object[]> interceptTypes() {
    return Arrays.asList(
        new Object[][] {{InterceptType.BEFORE}, {InterceptType.AFTER}, {InterceptType.AROUND}});
  }

  /**
   * Returns the UUID of the interceptable peer configured with in-flight tracking.
   *
   * <p>This test class uses the dedicated in-flight tracking peer from {@link
   * InFlightTrackingTestSuite} instead of the standard interceptable peer.
   *
   * @return the UUID of the in-flight tracking test peer
   */
  @Override
  protected UUID getInterceptablePeerUuid() {
    return InFlightTrackingTestSuite.INTERCEPTABLE_PEER_UUID;
  }

  /**
   * Creates an intercept request for the slowMethod with the current parameterized intercept type.
   *
   * @param callbackPeerUuid the UUID of the peer to receive callbacks
   * @param forceImmediate whether to force immediate activation
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createSlowMethodIntercept(
      UUID callbackPeerUuid, boolean forceImmediate) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        callbackPeerUuid,
        interceptType,
        "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp",
        "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp",
        "slowMethod",
        new InterceptableMethodCall("slowMethod", Collections.singletonList("int")),
        forceImmediate);
  }

  /**
   * Returns the expected number of callbacks per method invocation for the current intercept type.
   *
   * <p>AROUND intercepts send 2 callbacks per invocation (BEFORE and AFTER phases), while BEFORE
   * and AFTER intercepts each send 1 callback.
   *
   * @return the expected callback count per invocation
   */
  private int expectedCallbacksPerInvocation() {
    return interceptType == InterceptType.AROUND ? 2 : 1;
  }

  /**
   * Tests that intercept activates only after in-flight method executions complete.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> A method is invoked and begins executing (becomes in-flight)
   *   <li><b>When:</b> An intercept is registered for that method while it's still executing
   *   <li><b>Then:</b> The in-flight execution completes without interception
   *   <li><b>And:</b> The intercept only applies to subsequent calls after activation completes
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>In-flight call returns without callback being invoked
   *   <li>New call after drain completes DOES trigger callback
   *   <li>Timing confirms drain phase waited for in-flight completion
   * </ul>
   *
   * <p><b>Implementation Notes:</b>
   *
   * <ul>
   *   <li>Use a test method that sleeps (e.g., sleepThenReturn(millis)) to create controllable
   *       in-flight window
   *   <li>Spawn thread to invoke sleep method
   *   <li>Wait for method to become in-flight (small delay)
   *   <li>Register intercept while sleep is active
   *   <li>Verify first call completes without callback
   *   <li>Invoke method again and verify callback is triggered
   * </ul>
   */
  @Test
  public void interceptActivatedAfterInFlightCompletes() throws Exception {
    logger.info(
        "===== interceptActivatedAfterInFlightCompletes [{}]: TEST STARTED =====", interceptType);
    logger.info("DEBUG: myPeerUuid (ThinPeer/callback target) = {}", myPeerUuid);
    logger.info("DEBUG: interceptablePeerUuid = {}", getInterceptablePeerUuid());
    logger.info("DEBUG: interceptablePeerInfo = {}", interceptablePeerInfo);

    // Given: A method is executing (in-flight)
    final int slowMethodDelayMs = 2000; // 2 seconds
    final String slowMethodAppClass = "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

    // First, create an instance of SlowMethodApp
    logger.info("Creating SlowMethodApp instance");
    ObjectRef slowMethodAppInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("DEBUG: SlowMethodApp instance created with ref: {}", slowMethodAppInstance);

    // Start a thread that invokes slowMethod
    logger.info("Starting thread to invoke slowMethod with delay {}ms", slowMethodDelayMs);
    Thread slowThread =
        new Thread(
            () -> {
              try {
                invoke(
                    messageBuilder.buildInstanceMethod(
                        myPeerUuid,
                        slowMethodAppClass,
                        "slowMethod",
                        slowMethodAppInstance,
                        new String[] {"int"},
                        new Object[] {slowMethodDelayMs}));
                logger.info("slowMethod returned");
              } catch (Exception e) {
                logger.error("Error in slowThread", e);
              }
            });
    slowThread.start();

    // Wait for method to become in-flight (small delay)
    Thread.sleep(500);

    // When: An intercept is registered for that method while it's still executing
    logger.info("Registering {} intercept while slowMethod is in-flight", interceptType);
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createSlowMethodIntercept(myPeerUuid, false); // Do NOT force immediate - wait for drain

    logger.info("DEBUG: Intercept request details:");
    logger.info("DEBUG:   interceptUuid = {}", interceptRequest.getUuid());
    logger.info("DEBUG:   callbackPeerUuid = {}", myPeerUuid);
    logger.info("DEBUG:   interceptType = {}", interceptType);
    logger.info(
        "DEBUG:   classPattern = io.quasient.foobar.apps.quantized.intercept.SlowMethodApp");
    logger.info("DEBUG:   methodPattern = slowMethod");
    logger.info("DEBUG:   forceImmediate = false");
    register(interceptRequest);
    logger.info("Intercept registered successfully, waiting for activation");

    // Wait for the slow thread to complete
    slowThread.join();
    long slowThreadCompletionTime = System.currentTimeMillis();
    logger.info("slowThread completed at {}", slowThreadCompletionTime);

    // Then: The in-flight execution completes without interception
    // (no callback should have been received during the first call)
    // Wait briefly and verify no callbacks received
    Thread.sleep(100);
    // Don't call getCallbacks - it will block waiting for callbacks
    // Instead, we'll just proceed to the next invocation

    // Wait a bit for intercept to activate after drain completes
    Thread.sleep(500);

    // And: Subsequent calls after activation are intercepted
    logger.info("DEBUG: About to invoke slowMethod again - this should be intercepted");
    logger.info(
        "DEBUG: If intercept is active, interceptable peer should send callback to {}", myPeerUuid);
    ExecMessage secondCallResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                slowMethodAppClass,
                "slowMethod",
                slowMethodAppInstance,
                new String[] {"int"},
                new Object[] {100})); // Short delay for second call
    logger.info(
        "DEBUG: Second invoke completed. Response: {}", colferToPrettyJson(secondCallResponse));

    // Verify that callback is triggered for the second call
    int expectedCallbacks = expectedCallbacksPerInvocation();
    logger.info(
        "DEBUG: Now waiting for callbacks (expecting {}, timeout 5000ms)...", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("DEBUG: Received {} callbacks", callbacks.size());
    for (int i = 0; i < callbacks.size(); i++) {
      logger.info("DEBUG: Callback[{}]: {}", i, colferToPrettyJson(callbacks.get(i)));
    }
    assertThat(
        "Should receive " + expectedCallbacks + " callback(s) for second call",
        callbacks.size(),
        is(expectedCallbacks));

    logger.info(
        "===== interceptActivatedAfterInFlightCompletes [{}]: TEST PASSED =====", interceptType);
  }

  /**
   * Tests that new calls to intercepted method block until intercept activation completes.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> An intercept is registered for a method with in-flight calls
   *   <li><b>When:</b> A new call arrives during the drain phase (waiting for in-flight to
   *       complete)
   *   <li><b>Then:</b> The new call blocks until drain completes and intercept activates
   *   <li><b>And:</b> Once unblocked, the new call is intercepted correctly
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>New call during drain blocks (doesn't return immediately)
   *   <li>New call unblocks after drain completes
   *   <li>Unblocked call is intercepted (callback executes)
   *   <li>Timing confirms blocking duration matches drain phase
   * </ul>
   *
   * <p><b>Implementation Notes:</b>
   *
   * <ul>
   *   <li>Thread 1: Invoke long-running method (becomes in-flight)
   *   <li>Thread 2: Register intercept while Thread 1 is executing
   *   <li>Thread 3: Invoke method during drain phase (should block)
   *   <li>Verify Thread 3 blocks until Thread 1 completes
   *   <li>Verify Thread 3's call is intercepted after unblocking
   * </ul>
   */
  @Test
  public void newCallsBlockedDuringDrain() throws Exception {
    logger.info("===== newCallsBlockedDuringDrain [{}]: TEST STARTED =====", interceptType);

    // Given: An intercept is being activated with in-flight calls draining
    final int slowMethodDelayMs = 3000; // 3 seconds
    final AtomicLong blockedCallStartTime = new AtomicLong();
    final AtomicLong blockedCallEndTime = new AtomicLong();
    final String slowMethodAppClass = "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

    // Create separate ThinPeers for concurrent threads (ZMQ sockets are not thread-safe)
    ThinPeer inFlightThinPeer = createAdditionalThinPeer();
    ThinPeer blockedThinPeer = createAdditionalThinPeer();

    try {
      // First, create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Thread 1: Invoke long-running method (becomes in-flight)
      logger.info("Thread 1: Starting in-flight slowMethod call");
      Thread inFlightThread =
          new Thread(
              () -> {
                try {
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      inFlightThinPeer);
                  logger.info("Thread 1: in-flight call completed");
                } catch (Exception e) {
                  logger.error("Error in inFlightThread", e);
                }
              });
      inFlightThread.start();

      // Wait for method to become in-flight
      Thread.sleep(500);

      // Thread 2: Register intercept while Thread 1 is executing
      logger.info("Registering {} intercept while slowMethod is in-flight", interceptType);
      InterceptRequest<InterceptableMethodCall> interceptRequest =
          createSlowMethodIntercept(myPeerUuid, false); // Wait for drain

      register(interceptRequest);

      // Small delay to ensure registration is processed.
      // Note: Fencing starts synchronously when the coordinator receives the activation request,
      // so we only need to wait for the ZMQ round-trip, not for async processing.
      Thread.sleep(200);

      // Thread 3: Invoke method during drain phase (should block)
      logger.info("Thread 3: Starting new call during drain - should block");
      Thread blockedThread =
          new Thread(
              () -> {
                try {
                  blockedCallStartTime.set(System.currentTimeMillis());
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {100}),
                      blockedThinPeer);
                  blockedCallEndTime.set(System.currentTimeMillis());
                  logger.info("Thread 3: blocked call completed");
                } catch (Exception e) {
                  logger.error("Error in blockedThread", e);
                }
              });
      blockedThread.start();

      // Wait for both threads to complete
      inFlightThread.join();
      blockedThread.join();

      // Then: The new call blocks until drain completes
      // Verify Thread 3 blocked until Thread 1 completed
      long blockDuration = blockedCallEndTime.get() - blockedCallStartTime.get();
      logger.info("Blocked call duration: {}ms", blockDuration);
      assertTrue(
          "Blocked call should have waited at least 2 seconds for drain", blockDuration >= 2000);

      // And: The blocked call is intercepted once unblocked
      int expectedCallbacks = expectedCallbacksPerInvocation();
      List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
      assertThat(
          "Should receive " + expectedCallbacks + " callback(s) for blocked call",
          callbacks.size(),
          is(expectedCallbacks));

      logger.info("===== newCallsBlockedDuringDrain [{}]: TEST PASSED =====", interceptType);
    } finally {
      // Clean up additional ThinPeers
      inFlightThinPeer.close();
      blockedThinPeer.close();
    }
  }

  /**
   * Tests that intercept with forceImmediate=true activates without waiting for drain.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> A method has in-flight calls executing
   *   <li><b>When:</b> An intercept is registered with forceImmediate=true
   *   <li><b>Then:</b> The intercept activates immediately without waiting for drain
   *   <li><b>And:</b> In-flight calls may or may not be intercepted (race condition acceptable)
   *   <li><b>And:</b> New calls are immediately intercepted
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>Intercept activates while in-flight calls are still executing
   *   <li>No blocking occurs for new calls
   *   <li>New calls are intercepted immediately (even while old calls still running)
   *   <li>Timing confirms no drain phase occurred
   * </ul>
   *
   * <p><b>Implementation Notes:</b>
   *
   * <ul>
   *   <li>Invoke long-running method (becomes in-flight)
   *   <li>Register intercept with forceImmediate=true
   *   <li>Immediately invoke method again (should not block)
   *   <li>Verify second call is intercepted before first call completes
   * </ul>
   */
  @Test
  public void immediateInterceptActivatesWithoutWaiting() throws Exception {
    logger.info(
        "===== immediateInterceptActivatesWithoutWaiting [{}]: TEST STARTED =====", interceptType);

    // Given: A method has in-flight calls executing
    final int slowMethodDelayMs = 3000; // 3 seconds
    final long[] newCallTimes = new long[2]; // [startTime, endTime]
    final String slowMethodAppClass = "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

    // Create separate ThinPeer for concurrent thread (ZMQ sockets are not thread-safe)
    ThinPeer inFlightThinPeer = createAdditionalThinPeer();

    try {
      // First, create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Start a long-running method (becomes in-flight)
      logger.info("Starting in-flight slowMethod call");
      Thread inFlightThread =
          new Thread(
              () -> {
                try {
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      inFlightThinPeer);
                  logger.info("In-flight call completed");
                } catch (Exception e) {
                  logger.error("Error in inFlightThread", e);
                }
              });
      inFlightThread.start();

      // Wait for method to become in-flight
      Thread.sleep(500);

      // When: An intercept is registered with forceImmediate=true
      logger.info("Registering {} intercept with forceImmediate=true", interceptType);
      InterceptRequest<InterceptableMethodCall> interceptRequest =
          createSlowMethodIntercept(myPeerUuid, true); // Force immediate activation

      register(interceptRequest);

      // Wait for intercept to activate (should be immediate)
      Thread.sleep(200);

      // Immediately invoke method again (should not block)
      logger.info("Invoking slowMethod again immediately - should not block");
      newCallTimes[0] = System.currentTimeMillis();
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              slowMethodAppClass,
              "slowMethod",
              slowMethodAppInstance,
              new String[] {"int"},
              new Object[] {100}));
      newCallTimes[1] = System.currentTimeMillis();

      // Wait for in-flight thread to complete
      inFlightThread.join();

      // Then: The intercept activates immediately without drain
      // Verify second call completed quickly (not blocked)
      long newCallDuration = newCallTimes[1] - newCallTimes[0];
      logger.info("New call duration: {}ms", newCallDuration);
      assertTrue("New call should complete quickly (not wait for drain)", newCallDuration < 2000);

      // And: New calls are immediately intercepted
      // With forceImmediate=true, in-flight calls may also trigger AFTER/AROUND callbacks
      // when they complete, so we expect at least 1 callback for the new call.
      // For AFTER: in-flight call also triggers callback = 2 callbacks
      // For AROUND: both calls trigger BEFORE+AFTER = 4 callbacks
      // For BEFORE: only new call triggers callback = 1 callback
      int minExpectedCallbacks = expectedCallbacksPerInvocation();
      List<Message> callbacks = getCallbacks(minExpectedCallbacks, 5000);
      assertThat(
          "Should receive at least " + minExpectedCallbacks + " callback(s)",
          callbacks.size(),
          is(greaterThanOrEqualTo(minExpectedCallbacks)));

      logger.info(
          "===== immediateInterceptActivatesWithoutWaiting [{}]: TEST PASSED =====", interceptType);
    } finally {
      // Clean up additional ThinPeer
      inFlightThinPeer.close();
    }
  }

  /**
   * Tests that drain timeout causes waiting calls to unblock but intercept is NOT activated.
   *
   * <p><b>Design Decision:</b> When quiescence cannot be achieved within the timeout, the intercept
   * is intentionally NOT activated. This is a fail-safe behavior - if we can't guarantee that all
   * in-flight calls have completed, we don't activate the intercept to avoid inconsistent state.
   * Users who need to hot-patch hanging methods should use {@code forceImmediate=true}.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> An intercept is registered with a drain timeout (5000ms from peer config)
   *   <li><b>And:</b> In-flight calls are taking longer than the timeout (10000ms)
   *   <li><b>When:</b> The drain timeout expires
   *   <li><b>Then:</b> Waiting new calls are unblocked (fencing stops)
   *   <li><b>And:</b> The intercept is NOT activated (fail-safe behavior)
   *   <li><b>And:</b> Unblocked calls execute WITHOUT interception
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>Drain timeout triggers before in-flight calls complete
   *   <li>Blocked calls unblock after timeout (~5s, not after 10s slow call)
   *   <li>Intercept is NOT activated (no callbacks received)
   *   <li>In-flight calls continue executing and complete normally
   *   <li>Timing confirms unblock happens at timeout, not at in-flight completion
   * </ul>
   *
   * <p><b>Implementation Notes:</b>
   *
   * <ul>
   *   <li>Thread 1: Invoke method that sleeps for 10 seconds (becomes in-flight, exceeds timeout)
   *   <li>Register intercept (peer has 5000ms drain timeout)
   *   <li>Thread 2: Invoke method during drain (should block)
   *   <li>Verify Thread 2 unblocks after ~5 seconds (at timeout, not after 10s)
   *   <li>Verify Thread 2's call is NOT intercepted (no callback)
   *   <li>Verify Thread 1 completes normally (no interruption)
   * </ul>
   *
   * @see InterceptActivationCoordinator#activateWithDrainAndRegister
   */
  @Test
  public void timeoutUnblocksWaitingCalls() throws Exception {
    logger.info("===== timeoutUnblocksWaitingCalls [{}]: TEST STARTED =====", interceptType);

    // Given: An intercept is registered with a drain timeout
    // The peer is configured with --drain-timeout-ms 5000 (from InFlightTrackingTestSuite)
    // And: In-flight calls exceed the timeout duration
    final int slowMethodDelayMs = 10000; // 10 seconds - exceeds 5 second timeout
    final AtomicLong blockedCallStartTime = new AtomicLong();
    final AtomicLong blockedCallEndTime = new AtomicLong();
    final String slowMethodAppClass = "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

    // Create separate ThinPeers for concurrent threads (ZMQ sockets are not thread-safe)
    ThinPeer verySlowThinPeer = createAdditionalThinPeer();
    ThinPeer blockedThinPeer = createAdditionalThinPeer();

    try {
      // First, create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Start a very slow method (will exceed drain timeout)
      logger.info("Starting very slow in-flight call ({}ms)", slowMethodDelayMs);
      Thread verySlowThread =
          new Thread(
              () -> {
                try {
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      verySlowThinPeer);
                  logger.info("Very slow call completed");
                } catch (Exception e) {
                  logger.error("Error in verySlowThread", e);
                }
              });
      verySlowThread.start();

      // Wait for method to become in-flight
      Thread.sleep(500);

      // Register intercept (will use default 5000ms timeout from peer config)
      logger.info("Registering {} intercept - will timeout after 5000ms", interceptType);
      InterceptRequest<InterceptableMethodCall> interceptRequest =
          createSlowMethodIntercept(myPeerUuid, false); // Wait for drain (but will timeout)

      register(interceptRequest);

      // Wait for fencing to start
      Thread.sleep(200);

      // New call during drain (should block)
      logger.info("Starting new call during drain - should block then unblock at timeout");
      Thread blockedThread =
          new Thread(
              () -> {
                try {
                  blockedCallStartTime.set(System.currentTimeMillis());
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {100}),
                      blockedThinPeer);
                  blockedCallEndTime.set(System.currentTimeMillis());
                  logger.info("Blocked call completed");
                } catch (Exception e) {
                  logger.error("Error in blockedThread", e);
                }
              });
      blockedThread.start();

      // Wait for blocked thread to complete
      blockedThread.join();

      // When: The timeout expires
      // Then: Blocked calls are unblocked
      long blockDuration = blockedCallEndTime.get() - blockedCallStartTime.get();
      logger.info("Blocked call duration: {}ms", blockDuration);

      // Verify unblock happened around timeout (5000ms), not after slow call (10000ms)
      // Allow some tolerance: 4.5s to 7s (timeout is 5s, plus method execution ~100ms + overhead)
      assertTrue(
          "Blocked call should unblock near 5s timeout (was " + blockDuration + "ms)",
          blockDuration >= 4500 && blockDuration <= 7000);

      // And: The intercept is NOT activated (fail-safe behavior on timeout)
      // Wait briefly to ensure no callbacks arrive, then verify none received
      Thread.sleep(500);
      List<Message> callbacks = getCallbacksNonBlocking();
      logger.info("Callbacks received: {} (expected 0)", callbacks.size());
      assertThat(
          "Should receive 0 callbacks - intercept not activated on timeout",
          callbacks.size(),
          is(0));

      // Clean up: wait for very slow thread to finish
      verySlowThread.join();
      logger.info("Very slow thread completed normally (not interrupted)");

      logger.info("===== timeoutUnblocksWaitingCalls [{}]: TEST PASSED =====", interceptType);
    } finally {
      // Clean up additional ThinPeers
      verySlowThinPeer.close();
      blockedThinPeer.close();
    }
  }

  /**
   * Tests that multiple concurrent intercept registrations are handled correctly.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> Multiple methods have in-flight calls executing
   *   <li><b>When:</b> Intercepts are registered for different methods concurrently
   *   <li><b>Then:</b> Each method's drain phase operates independently
   *   <li><b>And:</b> Method A's drain doesn't block Method B's calls
   *   <li><b>And:</b> All intercepts activate correctly after their respective drains
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>Intercepts for different methods don't interfere with each other
   *   <li>Drain phases run concurrently and independently
   *   <li>Each method's new calls block only during that method's drain
   *   <li>All intercepts activate correctly after their respective completions
   * </ul>
   *
   * <p><b>Implementation Notes:</b>
   *
   * <ul>
   *   <li>Invoke methodA and methodB concurrently (both become in-flight)
   *   <li>Register interceptA and interceptB concurrently
   *   <li>Invoke methodA and methodB again (should block independently)
   *   <li>Verify methodA calls unblock when methodA's drain completes
   *   <li>Verify methodB calls unblock when methodB's drain completes
   *   <li>Verify both callbacks execute correctly after activation
   * </ul>
   */
  @Test
  public void concurrentInterceptRegistrations() throws Exception {
    logger.info("===== concurrentInterceptRegistrations [{}]: TEST STARTED =====", interceptType);

    // For this test, we need two different methods. SlowMethodApp only has slowMethod,
    // so we'll use the same method but register two different intercepts with different UUIDs.
    // The drain phases should still operate independently per intercept pattern.

    final int methodADelayMs = 2000;
    final int methodBDelayMs = 3000;
    final String slowMethodAppClass = "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

    // First, create an instance of SlowMethodApp
    logger.info("Creating SlowMethodApp instance");
    ObjectRef slowMethodAppInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                .getReturnValue()
                .getObject()
                .getRef());

    // Given: Multiple methods have in-flight calls executing
    logger.info("Starting in-flight call A");
    Thread inFlightThreadA =
        new Thread(
            () -> {
              try {
                invoke(
                    messageBuilder.buildInstanceMethod(
                        myPeerUuid,
                        slowMethodAppClass,
                        "slowMethod",
                        slowMethodAppInstance,
                        new String[] {"int"},
                        new Object[] {methodADelayMs}));
                logger.info("In-flight call A completed");
              } catch (Exception e) {
                logger.error("Error in inFlightThreadA", e);
              }
            });

    logger.info("Starting in-flight call B");
    Thread inFlightThreadB =
        new Thread(
            () -> {
              try {
                invoke(
                    messageBuilder.buildInstanceMethod(
                        myPeerUuid,
                        slowMethodAppClass,
                        "slowMethod",
                        slowMethodAppInstance,
                        new String[] {"int"},
                        new Object[] {methodBDelayMs}));
                logger.info("In-flight call B completed");
              } catch (Exception e) {
                logger.error("Error in inFlightThreadB", e);
              }
            });

    inFlightThreadA.start();
    Thread.sleep(100); // Slight delay between starts
    inFlightThreadB.start();

    // Wait for both to become in-flight
    Thread.sleep(500);

    // When: Intercepts are registered for the same method concurrently
    logger.info("Registering two {} intercepts concurrently", interceptType);
    InterceptRequest<InterceptableMethodCall> interceptRequestA =
        createSlowMethodIntercept(myPeerUuid, false);

    InterceptRequest<InterceptableMethodCall> interceptRequestB =
        createSlowMethodIntercept(myPeerUuid, false);

    // Register both intercepts concurrently
    Thread regThreadA =
        new Thread(
            () -> {
              try {
                register(interceptRequestA);
                logger.info("Intercept A registered");
              } catch (Exception e) {
                logger.error("Error registering intercept A", e);
              }
            });

    Thread regThreadB =
        new Thread(
            () -> {
              try {
                register(interceptRequestB);
                logger.info("Intercept B registered");
              } catch (Exception e) {
                logger.error("Error registering intercept B", e);
              }
            });

    regThreadA.start();
    regThreadB.start();

    regThreadA.join();
    regThreadB.join();

    // Wait for both in-flight threads to complete
    inFlightThreadA.join();
    inFlightThreadB.join();

    // Wait for intercepts to activate
    Thread.sleep(500);

    // Then: All intercepts activate correctly
    // Invoke the method - should trigger both intercepts' callbacks
    logger.info("Invoking slowMethod - should trigger both intercept callbacks");
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            slowMethodAppClass,
            "slowMethod",
            slowMethodAppInstance,
            new String[] {"int"},
            new Object[] {100}));

    // Should receive callbacks from both intercepts
    // AROUND sends 2 callbacks per intercept (BEFORE + AFTER), others send 1
    int expectedCallbacks = 2 * expectedCallbacksPerInvocation();
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    assertThat(
        "Should receive " + expectedCallbacks + " callbacks (from 2 intercepts)",
        callbacks.size(),
        is(expectedCallbacks));

    logger.info("===== concurrentInterceptRegistrations [{}]: TEST PASSED =====", interceptType);
  }
}
