/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.activation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.messages.colfer.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for immediate intercept activation when in-flight tracking is disabled.
 *
 * <p>These tests verify that intercepts activate immediately (without a drain phase) when the peer
 * has {@code --in-flight-tracking} explicitly disabled. The default is enabled ({@code true}), so
 * these tests exercise the opt-out path that matches the legacy implementation before in-flight
 * tracking was added.
 *
 * <p><b>Key Difference from InFlightInterceptActivationIT:</b>
 *
 * <ul>
 *   <li>{@link io.quasient.pal.intercept.inflight.InFlightInterceptActivationIT} tests with {@code
 *       --in-flight-tracking} enabled (drain before activation)
 *   <li>This class tests with tracking disabled (immediate activation, no drain)
 * </ul>
 *
 * <p><b>Test Scenario:</b>
 *
 * <ol>
 *   <li><b>Given:</b> The peer has {@code --in-flight-tracking} explicitly disabled
 *   <li><b>And:</b> A method has in-flight calls executing
 *   <li><b>When:</b> An intercept is registered for that method
 *   <li><b>Then:</b> The intercept activates immediately (no drain phase)
 *   <li><b>And:</b> New calls are intercepted immediately without blocking
 *   <li><b>And:</b> In-flight calls may or may not be intercepted (race condition is acceptable)
 * </ol>
 *
 * <p><b>Test Infrastructure:</b>
 *
 * <ul>
 *   <li>Uses the shared interceptable peer from {@link InterceptEndToEndTestSuite}
 *   <li>This peer has {@code --in-flight-tracking} explicitly set to {@code false}
 *   <li>Uses {@code SlowMethodApp} for controllable slow method execution
 * </ul>
 *
 * <p><b>Parameterization:</b> Tests are parameterized by {@link InterceptType} to ensure equal
 * coverage across BEFORE, AFTER, and AROUND intercept types. Each test runs three times, once for
 * each intercept type.
 *
 * @see io.quasient.pal.intercept.inflight.InFlightInterceptActivationIT
 */
@RunWith(Parameterized.class)
public class ImmediateActivationIT extends AbstractInterceptIT {

  /** Fully qualified class name for the SlowMethodApp test application. */
  private static final String SLOW_METHOD_APP_CLASS =
      "io.quasient.foobar.apps.quantized.intercept.SlowMethodApp";

  /** The intercept type being tested in this parameterized run. */
  private final InterceptType interceptType;

  /**
   * Constructs a test instance for the specified intercept type.
   *
   * @param interceptType the intercept type to test
   */
  public ImmediateActivationIT(InterceptType interceptType) {
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
   * Returns the UUID of the interceptable peer from InterceptEndToEndTestSuite.
   *
   * <p>This peer has {@code --in-flight-tracking} explicitly disabled, so intercepts activate
   * immediately without a drain phase.
   *
   * @return the UUID of the interceptable peer
   */
  @Override
  protected UUID getInterceptablePeerUuid() {
    return InterceptEndToEndTestSuite.INTERCEPTABLE_PEER_UUID;
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
        SLOW_METHOD_APP_CLASS,
        SLOW_METHOD_APP_CLASS,
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
   * Tests that intercept activates immediately without waiting for in-flight calls to complete.
   *
   * <p><b>Test Flow:</b>
   *
   * <ol>
   *   <li>Start a slow method call (becomes in-flight, runs for 3 seconds)
   *   <li>Register an intercept while the slow method is executing
   *   <li>Immediately invoke the method again
   *   <li>Verify the second call completes quickly (no blocking/drain)
   *   <li>Verify the second call is intercepted (callback received)
   * </ol>
   *
   * <p><b>Verification:</b>
   *
   * <ul>
   *   <li>Second call completes in less than 2 seconds (no drain phase waiting for 3s in-flight
   *       call)
   *   <li>Callback is received for the second call (intercept is active)
   * </ul>
   */
  @Test
  public void interceptActivatesImmediatelyWithoutDrain() throws Exception {
    logger.info(
        "===== interceptActivatesImmediatelyWithoutDrain [{}]: TEST STARTED =====", interceptType);

    // Given: A method has in-flight calls executing
    final int slowMethodDelayMs = 3000; // 3 seconds
    final long[] newCallTimes = new long[2]; // [startTime, endTime]
    final long[] inFlightCallEndTime = new long[1]; // When the in-flight call completes

    // Create separate ThinPeer for concurrent thread (ZMQ sockets are not thread-safe)
    ThinPeer inFlightThinPeer = createAdditionalThinPeer();

    try {
      // First, create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, SLOW_METHOD_APP_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("SlowMethodApp instance created with ref: {}", slowMethodAppInstance);

      // Start a slow method call (becomes in-flight)
      logger.info("Starting in-flight slowMethod call ({}ms)", slowMethodDelayMs);
      Thread inFlightThread =
          new Thread(
              () -> {
                try {
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          SLOW_METHOD_APP_CLASS,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      inFlightThinPeer);
                  inFlightCallEndTime[0] = System.currentTimeMillis();
                  logger.info("In-flight call completed at {}", inFlightCallEndTime[0]);
                } catch (Exception e) {
                  logger.error("Error in inFlightThread", e);
                }
              });
      inFlightThread.start();

      // Wait for method to become in-flight
      Thread.sleep(500);

      // When: An intercept is registered while the method is executing
      logger.info("Registering {} intercept while slowMethod is in-flight", interceptType);
      InterceptRequest<InterceptableMethodCall> interceptRequest =
          createSlowMethodIntercept(
              myPeerUuid, false); // forceImmediate=false (tracking disabled, so still immediate)

      register(interceptRequest);

      // Wait briefly for intercept registration to propagate
      Thread.sleep(200);

      // Then: Immediately invoke method again - should NOT block
      logger.info("Invoking slowMethod again - should NOT block (no drain phase)");
      newCallTimes[0] = System.currentTimeMillis();
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              SLOW_METHOD_APP_CLASS,
              "slowMethod",
              slowMethodAppInstance,
              new String[] {"int"},
              new Object[] {100})); // Short delay for second call
      newCallTimes[1] = System.currentTimeMillis();

      // Verify: Second call completed quickly (no drain phase)
      // With drain enabled, the call would wait ~3 seconds for the in-flight call to complete.
      // Without drain, it should complete much faster (just the 100ms delay + RPC overhead).
      long newCallDuration = newCallTimes[1] - newCallTimes[0];
      logger.info("New call duration: {}ms", newCallDuration);
      assertTrue(
          "New call should complete without waiting for in-flight drain (was "
              + newCallDuration
              + "ms)",
          newCallDuration < 2500); // Allow generous overhead, but must be less than 3s drain wait

      // Verify: Callback was received (intercept is active)
      int expectedCallbacks = expectedCallbacksPerInvocation();
      List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
      logger.info("Received {} callbacks", callbacks.size());
      assertThat(
          "Should receive " + expectedCallbacks + " callback(s) (intercept activated)",
          callbacks.size(),
          is(expectedCallbacks));

      // Wait for in-flight thread to complete
      inFlightThread.join();
      logger.info("In-flight thread completed normally");

      // CRITICAL VERIFICATION: Prove the first call was actually in-flight when we registered
      // the intercept and made the second call. The in-flight call (3000ms) must complete AFTER
      // the second call (100ms) finished.
      assertTrue(
          "In-flight call must complete AFTER second call to prove it was in-flight during "
              + "intercept registration (in-flight ended at "
              + inFlightCallEndTime[0]
              + ", second call ended at "
              + newCallTimes[1]
              + ")",
          inFlightCallEndTime[0] > newCallTimes[1]);

      logger.info(
          "===== interceptActivatesImmediatelyWithoutDrain [{}]: TEST PASSED =====", interceptType);
    } finally {
      // Clean up additional ThinPeer
      inFlightThinPeer.close();
    }
  }

  /**
   * Tests that new calls during intercept registration do not block when tracking is disabled.
   *
   * <p>This test verifies that when in-flight tracking is disabled, there is no fencing mechanism
   * that blocks new calls during intercept registration. Multiple concurrent calls can proceed
   * simultaneously without any coordination.
   *
   * <p><b>Test Flow:</b>
   *
   * <ol>
   *   <li>Start a slow method call (becomes in-flight)
   *   <li>Register an intercept while the slow method is executing
   *   <li>Immediately start another slow method call
   *   <li>Verify the second call starts before the first completes (no blocking on fence)
   * </ol>
   */
  @Test
  public void newCallsNotBlockedDuringRegistration() throws Exception {
    logger.info(
        "===== newCallsNotBlockedDuringRegistration [{}]: TEST STARTED =====", interceptType);

    // Given: An intercept is being registered while calls are in-flight
    final int slowMethodDelayMs = 2000; // 2 seconds
    final long[] secondCallStartTime = new long[1]; // When the second call starts
    final long[] firstCallEndTime = new long[1]; // When the first call completes

    // Create separate ThinPeers for concurrent threads (ZMQ sockets are not thread-safe)
    ThinPeer firstThinPeer = createAdditionalThinPeer();
    ThinPeer secondThinPeer = createAdditionalThinPeer();

    try {
      // First, create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, SLOW_METHOD_APP_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Start first slow method call
      logger.info("Starting first slowMethod call ({}ms)", slowMethodDelayMs);
      Thread firstThread =
          new Thread(
              () -> {
                try {
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          SLOW_METHOD_APP_CLASS,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      firstThinPeer);
                  firstCallEndTime[0] = System.currentTimeMillis();
                  logger.info("First call completed at {}", firstCallEndTime[0]);
                } catch (Exception e) {
                  logger.error("Error in firstThread", e);
                }
              });
      firstThread.start();

      // Wait for method to become in-flight
      Thread.sleep(300);

      // When: Register intercept while first call is in-flight
      logger.info("Registering {} intercept while first call is in-flight", interceptType);
      InterceptRequest<InterceptableMethodCall> interceptRequest =
          createSlowMethodIntercept(myPeerUuid, false);

      register(interceptRequest);

      // Then: Start second call immediately - should NOT block on fence
      logger.info("Starting second slowMethod call - should NOT block");
      Thread secondThread =
          new Thread(
              () -> {
                try {
                  secondCallStartTime[0] = System.currentTimeMillis();
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          SLOW_METHOD_APP_CLASS,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {slowMethodDelayMs}),
                      secondThinPeer);
                  logger.info("Second call completed");
                } catch (Exception e) {
                  logger.error("Error in secondThread", e);
                }
              });
      secondThread.start();

      // Wait for both threads to complete
      firstThread.join();
      secondThread.join();

      // Verify callbacks received (may receive 0, 1, or 2 depending on race)
      // The key assertion is that calls didn't block - callback count depends on timing
      Thread.sleep(500);
      List<Message> callbacks = getCallbacksNonBlocking();
      logger.info("Received {} callbacks (timing-dependent)", callbacks.size());

      // CRITICAL VERIFICATION: Prove the first call was actually in-flight when the second call
      // started. The second call must START before the first call ENDS (proving concurrency).
      assertTrue(
          "Second call must start BEFORE first call ends to prove first was in-flight "
              + "(second started at "
              + secondCallStartTime[0]
              + ", first ended at "
              + firstCallEndTime[0]
              + ")",
          secondCallStartTime[0] < firstCallEndTime[0]);

      logger.info(
          "===== newCallsNotBlockedDuringRegistration [{}]: TEST PASSED =====", interceptType);
    } finally {
      // Clean up additional ThinPeers
      firstThinPeer.close();
      secondThinPeer.close();
    }
  }
}
