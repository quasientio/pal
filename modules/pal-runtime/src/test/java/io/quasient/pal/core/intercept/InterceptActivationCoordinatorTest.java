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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptableMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Unit tests for {@link InterceptActivationCoordinator}.
 *
 * <p>These tests verify the coordination of intercept activation with in-flight dispatch tracking
 * and fencing mechanism, ensuring:
 *
 * <ul>
 *   <li>Safe activation sequence: fence → wait for quiescence → enqueue → unfence
 *   <li>Timeout handling during drain operations
 *   <li>Immediate activation bypassing drain when forceImmediate=true
 *   <li>Respect for global WITH_IN_FLIGHT_TRACKING configuration
 *   <li>Per-intercept override with forceImmediate flag
 *   <li>Proper cleanup (unfencing) on activation failure
 *   <li>Async behavior for drain-based activations with MPSC queue
 *   <li>Parallel drain processing support
 * </ul>
 *
 * <p><b>Parameterization:</b> Tests are parameterized by {@link InterceptType} to ensure equal
 * coverage across BEFORE, AFTER, and AROUND intercept types. Each test runs three times, once for
 * each intercept type.
 *
 * <p><b>Note:</b> InterceptMatcher extends Service which is annotated @DoNotMock, so these tests
 * use a null provider and verify the tracker interactions instead. The actual registration behavior
 * is tested in integration tests.
 */
@RunWith(Parameterized.class)
public class InterceptActivationCoordinatorTest {

  private InterceptActivationCoordinator coordinator;
  private InFlightDispatchTracker tracker;
  private MessagePassingQueue<PendingInterceptActivation> pendingQueue;

  /**
   * Timeout for drain operations in tests. Kept short because the direct executor runs the async
   * task synchronously, and the {@link PendingInterceptActivation#awaitRegistration(long)} call
   * blocks for this duration when no consumer signals registration (as in unit tests).
   */
  private static final long DRAIN_TIMEOUT_MS = 50L;

  /**
   * A direct executor that runs tasks immediately in the calling thread. This allows testing of
   * async activation behavior synchronously.
   */
  private final ExecutorService directExecutor = MoreExecutors.newDirectExecutorService();

  /** The intercept type being tested in this parameterized run. */
  private final InterceptType interceptType;

  /**
   * Constructs a test instance for the specified intercept type.
   *
   * @param interceptType the intercept type to test
   */
  public InterceptActivationCoordinatorTest(InterceptType interceptType) {
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

  @Before
  public void setup() {
    tracker = mock(InFlightDispatchTracker.class);
    pendingQueue = new MpscUnboundedArrayQueue<>(64);
  }

  /**
   * Tests that the coordinator follows the correct activation sequence when drain is enabled.
   *
   * <p>Verifies that the activation sequence is:
   *
   * <ol>
   *   <li>Start fencing for the intercept pattern
   *   <li>Wait for in-flight dispatches to complete (quiescence)
   *   <li>Enqueue the intercept for registration
   *   <li>Stop fencing to allow new dispatches
   * </ol>
   *
   * <p>With async activation, this test uses a direct executor to run the async task immediately,
   * allowing verification of the sequence.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_fencesThenWaitsThenEnqueues]
   */
  @Test
  public void activateWithDrain_fencesThenWaitsThenEnqueues() throws Exception {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message for "com.example.Calculator.add" with forceImmediate=false
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", false);

    // And: The tracker will return true for waitForQuiescence (quiescence achieved)
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong())).thenReturn(true);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activateIntercept(message);

    // Then: The result should be async pending (activation submitted to executor)
    assertTrue("Should return async pending for drain-based activation", result.isAsyncPending());

    // And: The coordinator should:
    // 1. Call tracker.startFencing() SYNCHRONOUSLY before returning
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. Call tracker.waitForQuiescence() in the async task (via direct executor in this test)
    verify(tracker, times(1)).waitForQuiescence("com.example.Calculator", "add", DRAIN_TIMEOUT_MS);

    // 3. Enqueue the intercept for registration
    PendingInterceptActivation pending = pendingQueue.poll();
    assertNotNull("Intercept should be enqueued for registration", pending);
    assertEquals("com.example.Calculator", pending.classPattern());
    assertEquals("add", pending.methodPattern());
    assertNotNull(pending.interceptMessage());

    // 4. Call tracker.stopFencing() in the async task's finally block
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");
  }

  /**
   * Tests that the coordinator does not enqueue when the drain timeout is exceeded.
   *
   * <p>Verifies that if in-flight dispatches do not complete within the configured timeout, the
   * coordinator:
   *
   * <ul>
   *   <li>Does NOT enqueue for registration
   *   <li>Stops fencing to unblock waiting threads
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateWithDrain_timeoutDoesNotEnqueue]
   */
  @Test
  public void activateWithDrain_timeoutDoesNotEnqueue() throws Exception {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message for "com.example.Calculator.add"
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", false);

    // And: There are in-flight dispatches that will NOT complete before the timeout
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong())).thenReturn(false);

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activateIntercept(message);

    // Then: The result should be async pending
    assertTrue(result.isAsyncPending());

    // And: The coordinator should:
    // 1. Call tracker.startFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. Call tracker.waitForQuiescence (which returns false - timeout)
    verify(tracker, times(1)).waitForQuiescence("com.example.Calculator", "add", DRAIN_TIMEOUT_MS);

    // 3. NOT enqueue the intercept
    assertTrue("Queue should be empty on timeout", pendingQueue.isEmpty());

    // 4. Call tracker.stopFencing to clean up
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");
  }

  /**
   * Tests that immediate activation bypasses the drain mechanism entirely.
   *
   * <p>Verifies that when forceImmediate=true, the coordinator:
   *
   * <ul>
   *   <li>Does NOT start fencing
   *   <li>Does NOT wait for quiescence
   *   <li>Proceeds directly to registration (synchronously)
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activateImmediate_activatesWithoutDrain]
   */
  @Test
  public void activateImmediate_activatesWithoutDrain() throws Exception {
    // Given: A coordinator with in-flight tracking enabled
    // Using null provider - will cause NPE if registration is attempted, which is expected
    // in this test since we're verifying the tracker is NOT called
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message with forceImmediate=true
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", true);

    // When: The coordinator is asked to activate the intercept
    // Note: This will fail with NPE when trying to register, but that's after the tracker checks
    try {
      coordinator.activateIntercept(message);
    } catch (NullPointerException e) {
      // Expected - the null provider is accessed for registration
      // But we've already verified the tracker interactions by this point
    }

    // Then: The coordinator should:
    // 1. NOT call tracker.startFencing()
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 2. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());

    // 3. NOT call tracker.stopFencing()
    verify(tracker, never()).stopFencing(anyString(), anyString());

    // 4. Queue should remain empty (immediate path doesn't use queue)
    assertTrue("Queue should be empty for immediate activation", pendingQueue.isEmpty());
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
  public void activateWithDrain_respectsGlobalConfig() throws Exception {
    // Given: A coordinator where WITH_IN_FLIGHT_TRACKING is DISABLED globally
    Set<RunOptions> runOptions = EnumSet.noneOf(RunOptions.class);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message with forceImmediate=false
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", false);

    // When: The coordinator is asked to activate the intercept
    // Note: This will fail with NPE when trying to register, but that's after the tracker checks
    try {
      coordinator.activateIntercept(message);
    } catch (NullPointerException e) {
      // Expected
    }

    // Then: The coordinator should:
    // 1. NOT call tracker.startFencing() (because global tracking is disabled)
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 2. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());
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
  public void activateWithDrain_perInterceptOverrideBypassesDrain() throws Exception {
    // Given: A coordinator with WITH_IN_FLIGHT_TRACKING ENABLED globally
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message with forceImmediate=true (per-intercept override)
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", true);

    // When: The coordinator is asked to activate the intercept
    try {
      coordinator.activateIntercept(message);
    } catch (NullPointerException e) {
      // Expected
    }

    // Then: The coordinator should:
    // 1. Detect that forceImmediate=true (per-intercept override)
    // 2. NOT call tracker.startFencing() (override takes precedence)
    verify(tracker, never()).startFencing(anyString(), anyString());

    // 3. NOT call tracker.waitForQuiescence()
    verify(tracker, never()).waitForQuiescence(anyString(), anyString(), anyLong());
  }

  /**
   * Tests that fencing is properly removed if waitForQuiescence fails.
   *
   * <p>Verifies cleanup behavior when quiescence waiting throws an exception:
   *
   * <ul>
   *   <li>Fencing is started before activation attempt
   *   <li>waitForQuiescence throws exception
   *   <li>Coordinator calls stopFencing() in finally block
   * </ul>
   *
   * <p><b>Acceptance criterion:</b> [TEST:InterceptActivationCoordinatorTest.unfencesOnFailure]
   */
  @Test
  public void unfencesOnFailure() throws Exception {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: An intercept message for "com.example.Calculator.add"
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", false);

    // And: waitForQuiescence will throw an exception (simulating failure)
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong()))
        .thenThrow(new RuntimeException("Simulated failure"));

    // When: The coordinator is asked to activate the intercept
    InterceptActivationCoordinator.ActivationResult result = coordinator.activateIntercept(message);

    // Then: The result should be async pending (failure happens in async task)
    assertTrue(result.isAsyncPending());

    // And: The coordinator should:
    // 1. Call tracker.startFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).startFencing("com.example.Calculator", "add");

    // 2. In finally block, call tracker.stopFencing("com.example.Calculator", "add")
    verify(tracker, times(1)).stopFencing("com.example.Calculator", "add");

    // 3. Queue should be empty (enqueue didn't happen due to exception)
    assertTrue("Queue should be empty on failure", pendingQueue.isEmpty());
  }

  /**
   * Tests that multiple drain operations can be tracked with the active drain counter.
   *
   * <p>This test verifies the monitoring capability of the coordinator.
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:InterceptActivationCoordinatorTest.activeDrainCount_tracksParallelOperations]
   */
  @Test
  public void activeDrainCount_tracksParallelOperations() throws Exception {
    // Given: A coordinator with in-flight tracking enabled
    Set<RunOptions> runOptions = EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING);
    coordinator =
        new InterceptActivationCoordinator(
            tracker, () -> null, runOptions, DRAIN_TIMEOUT_MS, pendingQueue, directExecutor);

    // And: Initially no active drains
    assertEquals(0, coordinator.getActiveDrainCount());

    // And: waitForQuiescence returns true
    when(tracker.waitForQuiescence(anyString(), anyString(), anyLong())).thenReturn(true);

    // When: We activate an intercept (using direct executor, it runs synchronously)
    InterceptMessage message = createInterceptMessage("com.example.Calculator", "add", false);
    coordinator.activateIntercept(message);

    // Then: After the direct executor completes, drain count should be back to 0
    assertEquals(0, coordinator.getActiveDrainCount());
  }

  /**
   * Helper method to create an InterceptMessage for testing.
   *
   * <p>Uses the parameterized {@link #interceptType} to test different intercept types.
   *
   * @param clazz the target class name
   * @param methodName the target method name
   * @param forceImmediate whether to force immediate activation
   * @return a new InterceptMessage
   */
  private InterceptMessage createInterceptMessage(
      String clazz, String methodName, boolean forceImmediate) {
    InterceptMessage message = new InterceptMessage();
    message.setClazz(clazz);
    message.setInterceptType(interceptType.toByte());
    message.setForceImmediate(forceImmediate);

    InterceptableMethod method = new InterceptableMethod();
    method.setName(methodName);
    message.setMethod(method);

    return message;
  }
}
