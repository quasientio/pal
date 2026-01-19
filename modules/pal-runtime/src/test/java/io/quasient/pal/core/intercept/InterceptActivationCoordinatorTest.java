/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.core.service.RunOptions;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link InterceptActivationCoordinator}.
 *
 * <p>These tests verify the coordination of intercept activation with in-flight dispatch tracking
 * and fencing mechanism, ensuring:
 *
 * <ul>
 *   <li>Safe activation sequence: fence → wait for quiescence → activate → unfence
 *   <li>Timeout handling during drain operations
 *   <li>Immediate activation bypassing drain when forceImmediate=true
 *   <li>Respect for global WITH_IN_FLIGHT_TRACKING configuration
 *   <li>Per-intercept override with forceImmediate flag
 *   <li>Proper cleanup (unfencing) on activation failure
 * </ul>
 */
public class InterceptActivationCoordinatorTest {

  private InterceptActivationCoordinator coordinator;
  private InFlightDispatchTracker tracker;
  private static final long DRAIN_TIMEOUT_MS = 5000L;

  @Before
  public void setup() {
    tracker = mock(InFlightDispatchTracker.class);
  }

  /**
   * Tests that the coordinator follows the correct activation sequence when drain is enabled.
   *
   * <p>Verifies that the activation sequence is:
   *
   * <ol>
   *   <li>Start fencing for the intercept pattern
   *   <li>Wait for in-flight dispatches to complete (quiescence)
   *   <li>Activate the intercept (register with InterceptMatcher)
   *   <li>Stop fencing to allow new dispatches
   * </ol>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_fencesThenWaitsThenActivates]
   */
  @Test
  public void activateWithDrain_fencesThenWaitsThenActivates() throws InterruptedException {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request for "com.example.Calculator.add" with forceImmediate=false
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", false);

    // And: The tracker will return true for waitForQuiescence (quiescence achieved)
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong())).thenReturn(true);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. Call tracker.startFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. Call tracker.waitForQuiescence("com.example.Calculator", "add", timeoutMs)
    verify(tracker, times(1)).waitForQuiescence("com.example.Calculator", "add", DRAIN_TIMEOUT_MS);

    // 3. Call tracker.stopFencing("com.example.Calculator", "add") in finally block
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");

    // And: The activation should succeed (return success status)
    assertTrue(result.isSuccess());
  }

  /**
   * Tests that the coordinator returns failure status when the drain timeout is exceeded.
   *
   * <p>Verifies that if in-flight dispatches do not complete within the configured timeout, the
   * coordinator:
   *
   * <ul>
   *   <li>Returns failure status
   *   <li>Does NOT activate the intercept
   *   <li>Stops fencing to unblock waiting threads
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_timeoutReturnsFailure]
   */
  @Test
  public void activateWithDrain_timeoutReturnsFailure() throws InterruptedException {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request for "com.example.Calculator.add"
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", false);

    // And: There are in-flight dispatches that will NOT complete before the timeout
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong())).thenReturn(false);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. Call tracker.startFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. Call tracker.waitForQuiescence("com.example.Calculator", "add", timeoutMs)
    //    which returns false (timeout)
    verify(tracker, times(1)).waitForQuiescence("com.example.Calculator", "add", DRAIN_TIMEOUT_MS);

    // 3. Call tracker.stopFencing("com.example.Calculator", "add") to clean up
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");

    // And: The activation result should indicate failure (result.isSuccess() == false)
    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("Timeout"));
  }

  /**
   * Tests that immediate activation bypasses the drain mechanism entirely.
   *
   * <p>Verifies that when forceImmediate=true, the coordinator:
   *
   * <ul>
   *   <li>Does NOT start fencing
   *   <li>Does NOT wait for quiescence
   *   <li>Immediately activates the intercept
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateImmediate_activatesWithoutDrain]
   */
  @Test
  public void activateImmediate_activatesWithoutDrain() throws InterruptedException {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request with forceImmediate=true
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", true);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. NOT call tracker.startFencing()
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 2. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());

    // 3. NOT call tracker.stopFencing()
    verify(tracker, never()).stopFencing(anyString(), anyString());

    // And: The activation should succeed immediately
    assertTrue(result.isSuccess());
  }

  /**
   * Tests that drain behavior respects the global WITH_IN_FLIGHT_TRACKING configuration.
   *
   * <p>Verifies that when WITH_IN_FLIGHT_TRACKING is disabled globally, the coordinator skips the
   * drain mechanism even when forceImmediate=false.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_respectsGlobalConfig]
   */
  @Test
  public void activateWithDrain_respectsGlobalConfig() throws InterruptedException {
    // Given: A coordinator where WITH_IN_FLIGHT_TRACKING is DISABLED globally
    Set<RunOptions> runOptions = EnumSet.noneOf(RunOptions.class);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request with forceImmediate=false
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", false);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. NOT call tracker.startFencing() (because global tracking is disabled)
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 2. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());

    // And: The activation should succeed immediately
    assertTrue(result.isSuccess());
  }

  /**
   * Tests that per-intercept forceImmediate flag bypasses drain even when global tracking is
   * enabled.
   *
   * <p>Verifies the per-intercept override behavior:
   *
   * <ul>
   *   <li>Global WITH_IN_FLIGHT_TRACKING = enabled
   *   <li>Per-intercept forceImmediate = true
   *   <li>Result: No drain, immediate activation
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_perInterceptOverrideBypassesDrain]
   */
  @Test
  public void activateWithDrain_perInterceptOverrideBypassesDrain() throws InterruptedException {
    // Given: A coordinator with WITH_IN_FLIGHT_TRACKING ENABLED globally
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request with forceImmediate=true (per-intercept override)
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", true);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. Detect that forceImmediate=true (per-intercept override)
    // 2. NOT call tracker.startFencing() (override takes precedence)
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 3. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());

    // And: The activation should succeed immediately
    assertTrue(result.isSuccess());
  }

  /**
   * Tests that fencing is properly removed if activation fails.
   *
   * <p>Verifies cleanup behavior when activation fails (e.g., due to duplicate intercept):
   *
   * <ul>
   *   <li>Fencing is started before activation attempt
   *   <li>Activation fails (e.g., matcher.addIntercept() throws exception)
   *   <li>Coordinator calls stopFencing() in finally block
   * </ul>
   *
   * <p><b>Acceptance criterion:</b> [TEST:InterceptActivationCoordinatorTest.unfencesOnFailure]
   */
  @Test
  public void unfencesOnFailure() throws InterruptedException {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS);

    // And: An intercept request for "com.example.Calculator.add"
    InterceptRequest<?> request = createInterceptRequest("com.example.Calculator", "add", false);

    // And: waitForQuiescence will throw an exception (simulating failure)
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("Simulated failure"));

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activate(request);

    // Then: The coordinator should:
    // 1. Call tracker.startFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. In finally block, call tracker.stopFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");

    // And: The activation should fail
    assertFalse(result.isSuccess());
    assertTrue(result.getMessage().contains("Error"));
  }

  /**
   * Helper method to create an InterceptRequest for testing.
   *
   * @param clazz the target class name
   * @param methodName the target method name
   * @param forceImmediate whether to force immediate activation
   * @return a new InterceptRequest
   */
  private InterceptRequest<?> createInterceptRequest(
      String clazz, String methodName, boolean forceImmediate) {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptType type = InterceptType.BEFORE;
    String callbackClass = "com.example.Callback";
    String callbackMethod = "onIntercept";
    InterceptableMethodCall interceptable = new InterceptableMethodCall(methodName, null);

    return new InterceptRequest<>(
        uuid, peer, type, clazz, callbackClass, callbackMethod, interceptable, forceImmediate);
  }
}
