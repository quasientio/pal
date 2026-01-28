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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link InFlightDispatchTracker}.
 *
 * <p>These tests verify the core functionality of tracking in-flight dispatch operations,
 * including:
 *
 * <ul>
 *   <li>Tracking dispatch entry/exit with counter management
 *   <li>Pattern matching for in-flight dispatch detection (including wildcard patterns)
 *   <li>Quiescence waiting with timeout support
 *   <li>Fencing mechanism to block new dispatches
 *   <li>Thread-safety under concurrent operations
 *   <li>Constructor tracking (using "new" as executable name)
 *   <li>Field-op tracking (null parameterTypes)
 *   <li>Parameter-type-specific counter separation (overloaded methods)
 *   <li>Fencing precision (fencing one overload does not block another)
 * </ul>
 */
public class InFlightDispatchTrackerTest {

  /** Constant for no-arg method/constructor parameter types. */
  private static final String[] NO_PARAMS = new String[0];

  private InFlightDispatchTracker tracker;
  private ExecutorService executorService;

  @Before
  public void setup() {
    tracker = new InFlightDispatchTracker();
    executorService = Executors.newCachedThreadPool();
  }

  @After
  public void cleanup() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  public void enterDispatch_incrementsCounter() throws InterruptedException {
    // Given: An empty tracker with no in-flight dispatches
    // When: enterDispatch is called for a specific class and method
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: The in-flight counter for that class+method should be incremented
    // And: hasInFlightDispatches should return true for matching patterns
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS);
    assertTrue("Expected in-flight dispatches after enterDispatch", hasInFlight);

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
  }

  @Test
  public void exitDispatch_decrementsCounter() throws InterruptedException {
    // Given: A tracker with an active dispatch (counter = 1)
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    assertTrue(
        "Expected in-flight dispatches after enterDispatch",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));

    // When: exitDispatch is called for the same class and method
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: The in-flight counter should be decremented back to 0
    // And: hasInFlightDispatches should return false for that pattern
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS);
    assertFalse("Expected no in-flight dispatches after exitDispatch", hasInFlight);
  }

  @Test
  public void hasInFlightDispatches_returnsTrueWhenDispatchActive() throws InterruptedException {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    // When: hasInFlightDispatches is called with pattern "com.example.Calculator.add"
    // Then: It should return true
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS);
    assertTrue("Expected in-flight dispatches to be detected", hasInFlight);

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
  }

  @Test
  public void hasInFlightDispatches_returnsFalseWhenNoDispatches() {
    // Given: A tracker with no active dispatches
    // When: hasInFlightDispatches is called with any pattern
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS);

    // Then: It should return false
    assertFalse("Expected no in-flight dispatches", hasInFlight);
  }

  @Test
  public void hasInFlightDispatches_supportsWildcardPatterns() throws InterruptedException {
    // Given: Active dispatches for:
    //   - "com.example.Calculator.add"
    //   - "com.example.Calculator.subtract"
    //   - "org.other.Service.process"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    tracker.enterDispatch("com.example.Calculator", "subtract", NO_PARAMS);
    tracker.enterDispatch("org.other.Service", "process", NO_PARAMS);

    try {
      // When: hasInFlightDispatches is called with various wildcard patterns
      // Then: Pattern "com.example.*" should match the first two
      assertTrue(
          "Expected wildcard pattern 'com.example.*' to match",
          tracker.hasInFlightDispatches("com.example.*", "*", NO_PARAMS));

      // And: Pattern "*" should match all three
      assertTrue(
          "Expected wildcard pattern '*' to match all",
          tracker.hasInFlightDispatches("*", "*", NO_PARAMS));

      // And: Pattern "com.example.Calculator.*" should match the first two
      assertTrue(
          "Expected pattern 'com.example.Calculator.*' to match",
          tracker.hasInFlightDispatches("com.example.Calculator", "*", NO_PARAMS));

      // And: Pattern "org.other.*" should match only the third
      assertTrue(
          "Expected pattern 'org.other.*' to match",
          tracker.hasInFlightDispatches("org.other.*", "*", NO_PARAMS));

      // And: Pattern that doesn't match anything should return false
      assertFalse(
          "Expected unmatched pattern to return false",
          tracker.hasInFlightDispatches("com.unrelated.Class", "method", NO_PARAMS));

    } finally {
      // Cleanup
      tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
      tracker.exitDispatch("com.example.Calculator", "subtract", NO_PARAMS);
      tracker.exitDispatch("org.other.Service", "process", NO_PARAMS);
    }
  }

  @Test
  public void waitForQuiescence_returnsImmediatelyWhenNoDispatches() throws InterruptedException {
    // Given: A tracker with no active dispatches
    // When: waitForQuiescence is called with a pattern and timeout
    long startTime = System.currentTimeMillis();
    boolean quiescent = tracker.waitForQuiescence("com.example.Calculator", "add", NO_PARAMS, 1000);
    long elapsed = System.currentTimeMillis() - startTime;

    // Then: It should return true immediately (before timeout)
    assertTrue("Expected immediate quiescence when no dispatches", quiescent);

    // And: The elapsed time should be negligible (< 100ms)
    assertTrue("Expected negligible wait time but got " + elapsed + "ms", elapsed < 100);
  }

  @Test
  public void waitForQuiescence_blocksUntilDispatchCompletes() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    CountDownLatch exitLatch = new CountDownLatch(1);
    AtomicBoolean quiescentResult = new AtomicBoolean(false);

    // When: One thread calls waitForQuiescence for "com.example.Calculator.add"
    long startTime = System.currentTimeMillis();
    var unused1 =
        executorService.submit(
            () -> {
              try {
                boolean result =
                    tracker.waitForQuiescence("com.example.Calculator", "add", NO_PARAMS, 5000);
                quiescentResult.set(result);
                exitLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // And: Another thread calls exitDispatch after a short delay (e.g., 200ms)
    Thread.sleep(200);
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: waitForQuiescence should block until exitDispatch is called
    // And: waitForQuiescence should return true
    exitLatch.await(1, TimeUnit.SECONDS);
    long elapsed = System.currentTimeMillis() - startTime;

    assertTrue("Expected quiescence to be achieved", quiescentResult.get());

    // And: The elapsed time should be approximately the delay time (200ms +/- 100ms)
    assertTrue(
        "Expected elapsed time around 200ms but got " + elapsed + "ms",
        elapsed >= 150 && elapsed <= 350);
  }

  @Test
  public void waitForQuiescence_timeoutReturnsEvenIfDispatchesInFlight() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    try {
      // When: waitForQuiescence is called with a short timeout (e.g., 100ms)
      long startTime = System.currentTimeMillis();
      boolean quiescent =
          tracker.waitForQuiescence("com.example.Calculator", "add", NO_PARAMS, 100);
      long elapsed = System.currentTimeMillis() - startTime;

      // Then: waitForQuiescence should return false after the timeout expires
      assertFalse("Expected timeout without achieving quiescence", quiescent);

      // And: The elapsed time should be approximately the timeout duration
      assertTrue(
          "Expected elapsed time around 100ms but got " + elapsed + "ms",
          elapsed >= 80 && elapsed <= 200);

      // And: The dispatch should still be in-flight after the call returns
      assertTrue(
          "Expected dispatch still in-flight after timeout",
          tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));

    } finally {
      // Cleanup
      tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
    }
  }

  @Test
  public void startFencing_blocksNewDispatches() throws Exception {
    // Given: A tracker with fencing started for pattern "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*", NO_PARAMS);

    CountDownLatch enteredLatch = new CountDownLatch(1);
    AtomicBoolean entered = new AtomicBoolean(false);

    // When: A new thread attempts to call enterDispatch for "com.example.Calculator.add"
    var unused2 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
                entered.set(true);
                enteredLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: The enterDispatch call should block indefinitely
    // And: The thread should remain blocked until stopFencing is called
    boolean enteredWithinTimeout = enteredLatch.await(500, TimeUnit.MILLISECONDS);

    assertFalse("Expected enterDispatch to block when fencing is active", enteredWithinTimeout);
    assertFalse("Expected thread to remain blocked", entered.get());

    // Cleanup: stop fencing and wait for thread to complete
    tracker.stopFencing("com.example.Calculator", "*", NO_PARAMS);
    enteredLatch.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void stopFencing_allowsNewDispatches() throws Exception {
    // Given: A tracker with fencing started for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*", NO_PARAMS);

    CountDownLatch enteredLatch = new CountDownLatch(1);
    AtomicBoolean entered = new AtomicBoolean(false);

    // And: A thread blocked in enterDispatch for "com.example.Calculator.add"
    var unused3 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
                entered.set(true);
                enteredLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Verify thread is blocked
    Thread.sleep(100);
    assertFalse("Expected thread to be blocked before stopFencing", entered.get());

    // When: stopFencing is called for "com.example.Calculator.*"
    tracker.stopFencing("com.example.Calculator", "*", NO_PARAMS);

    // Then: The blocked enterDispatch call should unblock and proceed
    boolean unblocked = enteredLatch.await(1, TimeUnit.SECONDS);
    assertTrue("Expected enterDispatch to unblock after stopFencing", unblocked);
    assertTrue("Expected thread to have entered dispatch", entered.get());

    // And: Subsequent enterDispatch calls should not block
    long startTime = System.currentTimeMillis();
    tracker.enterDispatch("com.example.Calculator", "subtract", NO_PARAMS);
    long elapsed = System.currentTimeMillis() - startTime;
    assertTrue(
        "Expected subsequent enterDispatch to not block (took " + elapsed + "ms)", elapsed < 100);
    tracker.exitDispatch("com.example.Calculator", "subtract", NO_PARAMS);
  }

  @Test
  public void enterDispatch_throwsIfInterruptedDuringFencing() throws Exception {
    // Given: A tracker with fencing active for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*", NO_PARAMS);

    CountDownLatch exceptionLatch = new CountDownLatch(1);
    AtomicBoolean interruptedExceptionThrown = new AtomicBoolean(false);

    // When: A thread calls enterDispatch for "com.example.Calculator.add" (blocks)
    Future<?> future =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
              } catch (InterruptedException e) {
                interruptedExceptionThrown.set(true);
                exceptionLatch.countDown();
              }
            });

    // Give thread time to block
    Thread.sleep(100);

    // And: The thread is interrupted while blocked
    future.cancel(true);

    // Then: enterDispatch should throw InterruptedException
    boolean exceptionCaught = exceptionLatch.await(1, TimeUnit.SECONDS);
    assertTrue("Expected InterruptedException to be thrown", exceptionCaught);
    assertTrue("Expected InterruptedException to be thrown", interruptedExceptionThrown.get());

    // Cleanup
    tracker.stopFencing("com.example.Calculator", "*", NO_PARAMS);
  }

  @Test
  public void threadSafety_concurrentEnterExitOperations() throws Exception {
    // Given: A tracker shared across multiple threads
    final int threadCount = 10;
    final int operationsPerThread = 100;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completeLatch = new CountDownLatch(threadCount);
    AtomicInteger errorCount = new AtomicInteger(0);

    // When: Multiple threads concurrently perform enter/exit/check operations
    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      var unused =
          executorService.submit(
              () -> {
                try {
                  startLatch.await(); // Wait for all threads to be ready
                  for (int op = 0; op < operationsPerThread; op++) {
                    String className = "com.example.Class" + (threadId % 3);
                    String methodName = "method" + (op % 5);

                    // Enter dispatch
                    tracker.enterDispatch(className, methodName, NO_PARAMS);

                    // Check in-flight (should find it)
                    if (!tracker.hasInFlightDispatches(className, methodName, NO_PARAMS)) {
                      errorCount.incrementAndGet();
                    }

                    // Exit dispatch
                    tracker.exitDispatch(className, methodName, NO_PARAMS);
                  }
                } catch (Exception e) {
                  errorCount.incrementAndGet();
                } finally {
                  completeLatch.countDown();
                }
              });
    }

    // Start all threads simultaneously
    startLatch.countDown();

    // Then: All operations should complete without errors
    boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
    assertTrue("Expected all threads to complete", completed);
    assertEquals("Expected no errors during concurrent operations", 0, errorCount.get());

    // And: Final state should be consistent (all counters at 0)
    assertFalse(
        "Expected no in-flight dispatches after all threads complete",
        tracker.hasInFlightDispatches("*", "*", NO_PARAMS));
  }

  @Test
  public void enterDispatch_multipleCallsSameDispatch_incrementsMultipleTimes()
      throws InterruptedException {
    // Given: A tracker with no active dispatches
    // When: enterDispatch is called 3 times for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: The counter should be 3
    // And: hasInFlightDispatches should return true
    assertTrue(
        "Expected in-flight dispatches after 3 enters",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));

    // When: exitDispatch is called once
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: The counter should be 2 (still > 0)
    // And: hasInFlightDispatches should still return true
    assertTrue(
        "Expected in-flight dispatches still active after 1 exit",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
  }

  @Test
  public void waitForQuiescence_multipleWaitersAllUnblocked() throws Exception {
    // Given: A tracker with one active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    final int waiterCount = 5;
    CountDownLatch allWaitersCompleteLatch = new CountDownLatch(waiterCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // When: Multiple threads (e.g., 5 threads) call waitForQuiescence with same pattern
    for (int i = 0; i < waiterCount; i++) {
      var unused =
          executorService.submit(
              () -> {
                try {
                  boolean quiescent =
                      tracker.waitForQuiescence("com.example.Calculator", "add", NO_PARAMS, 5000);
                  if (quiescent) {
                    successCount.incrementAndGet();
                  }
                  allWaitersCompleteLatch.countDown();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });
    }

    // Give threads time to start waiting
    Thread.sleep(100);

    // And: Another thread calls exitDispatch after a delay
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: All waiting threads should unblock and return true
    boolean allCompleted = allWaitersCompleteLatch.await(2, TimeUnit.SECONDS);
    assertTrue("Expected all waiters to complete", allCompleted);
    assertEquals("Expected all waiters to achieve quiescence", waiterCount, successCount.get());
  }

  @Test
  public void fencing_doesNotBlockAlreadyInFlightDispatches() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    //   (dispatch entered before fencing started)
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);

    // When: startFencing is called for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*", NO_PARAMS);

    // Then: The already in-flight dispatch can complete (exitDispatch succeeds)
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
    assertFalse(
        "Expected dispatch to complete successfully",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));

    // And: Only NEW enterDispatch calls should be blocked
    CountDownLatch newEnterLatch = new CountDownLatch(1);
    AtomicBoolean newEntered = new AtomicBoolean(false);

    var unused4 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "subtract", NO_PARAMS);
                newEntered.set(true);
                newEnterLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    boolean newEnteredWithinTimeout = newEnterLatch.await(500, TimeUnit.MILLISECONDS);
    assertFalse("Expected new enterDispatch to be blocked by fencing", newEnteredWithinTimeout);
    assertFalse("Expected new dispatch to remain blocked", newEntered.get());

    // Cleanup
    tracker.stopFencing("com.example.Calculator", "*", NO_PARAMS);
    newEnterLatch.await(1, TimeUnit.SECONDS);
    if (newEntered.get()) {
      tracker.exitDispatch("com.example.Calculator", "subtract", NO_PARAMS);
    }
  }

  @Test
  public void waitForQuiescence_withWildcardPattern_waitsForAllMatching() throws Exception {
    // Given: Active dispatches for:
    //   - "com.example.Calculator.add"
    //   - "com.example.Calculator.subtract"
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    tracker.enterDispatch("com.example.Calculator", "subtract", NO_PARAMS);

    CountDownLatch quiescenceLatch = new CountDownLatch(1);
    AtomicBoolean quiescent = new AtomicBoolean(false);

    // When: waitForQuiescence is called with pattern "com.example.Calculator.*"
    var unused5 =
        executorService.submit(
            () -> {
              try {
                boolean result =
                    tracker.waitForQuiescence("com.example.Calculator", "*", NO_PARAMS, 5000);
                quiescent.set(result);
                quiescenceLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Give waiter time to start
    Thread.sleep(100);

    // And: Only one dispatch (add) exits
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: waitForQuiescence should remain blocked (subtract still active)
    boolean completedAfterFirstExit = quiescenceLatch.await(500, TimeUnit.MILLISECONDS);
    assertFalse(
        "Expected waitForQuiescence to remain blocked after first exit", completedAfterFirstExit);

    // When: The second dispatch (subtract) exits
    tracker.exitDispatch("com.example.Calculator", "subtract", NO_PARAMS);

    // Then: waitForQuiescence should return true
    boolean completedAfterSecondExit = quiescenceLatch.await(1, TimeUnit.SECONDS);
    assertTrue(
        "Expected waitForQuiescence to complete after all dispatches exit",
        completedAfterSecondExit);
    assertTrue("Expected quiescence to be achieved", quiescent.get());
  }

  @Test
  public void fencing_withWildcardPattern_blocksAllMatchingDispatches() throws Exception {
    // Given: A tracker with fencing started for "com.example.*"
    tracker.startFencing("com.example.*", "*", NO_PARAMS);

    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    CountDownLatch latch3 = new CountDownLatch(1);
    AtomicBoolean entered1 = new AtomicBoolean(false);
    AtomicBoolean entered2 = new AtomicBoolean(false);
    AtomicBoolean entered3 = new AtomicBoolean(false);

    // When: Multiple threads attempt enterDispatch for:
    //   - "com.example.Calculator.add"
    var unused6 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
                entered1.set(true);
                latch1.countDown();
                tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    //   - "com.example.Service.process"
    var unused7 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Service", "process", NO_PARAMS);
                entered2.set(true);
                latch2.countDown();
                tracker.exitDispatch("com.example.Service", "process", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    //   - "org.other.Service.process" (does NOT match pattern)
    var unused8 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("org.other.Service", "process", NO_PARAMS);
                entered3.set(true);
                latch3.countDown();
                tracker.exitDispatch("org.other.Service", "process", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: The first two should block
    boolean entered1Timeout = latch1.await(500, TimeUnit.MILLISECONDS);
    boolean entered2Timeout = latch2.await(500, TimeUnit.MILLISECONDS);
    assertFalse("Expected com.example.Calculator.add to be blocked", entered1Timeout);
    assertFalse("Expected com.example.Service.process to be blocked", entered2Timeout);

    // And: The third should proceed immediately (different pattern)
    boolean entered3Success = latch3.await(500, TimeUnit.MILLISECONDS);
    assertTrue("Expected org.other.Service.process to proceed", entered3Success);
    assertTrue("Expected non-matching dispatch to enter", entered3.get());

    // Cleanup
    tracker.stopFencing("com.example.*", "*", NO_PARAMS);
    latch1.await(1, TimeUnit.SECONDS);
    latch2.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void exitDispatch_withoutMatchingEnter_handlesGracefully() throws InterruptedException {
    // Given: A tracker with no active dispatches for "com.example.Calculator.add"
    // When: exitDispatch is called for "com.example.Calculator.add"
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);

    // Then: It should handle gracefully (either no-op or log warning)
    // And: Should not throw exception or cause negative counter
    // (The test passes if no exception is thrown)

    // Verify that subsequent operations still work
    tracker.enterDispatch("com.example.Calculator", "add", NO_PARAMS);
    assertTrue(
        "Expected dispatch to be tracked normally after graceful exit",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", NO_PARAMS));
    tracker.exitDispatch("com.example.Calculator", "add", NO_PARAMS);
  }

  // ===== New tests for constructor tracking =====

  @Test
  public void constructorTracking_enterExitWithNewName() throws InterruptedException {
    // Given: An empty tracker
    // When: enterDispatch is called with "new" for a constructor
    tracker.enterDispatch("com.example.Foo", "new", NO_PARAMS);

    // Then: hasInFlightDispatches should detect it
    assertTrue(
        "Expected in-flight constructor dispatch",
        tracker.hasInFlightDispatches("com.example.Foo", "new", NO_PARAMS));

    // When: exitDispatch is called
    tracker.exitDispatch("com.example.Foo", "new", NO_PARAMS);

    // Then: No more in-flight dispatches
    assertFalse(
        "Expected no in-flight dispatches after constructor exit",
        tracker.hasInFlightDispatches("com.example.Foo", "new", NO_PARAMS));
  }

  @Test
  public void constructorTracking_withParameterTypes() throws InterruptedException {
    // Given: A constructor with parameter types
    String[] params = new String[] {"int", "java.lang.String"};
    tracker.enterDispatch("com.example.Foo", "new", params);

    // Then: Should be tracked separately from no-arg constructor
    assertTrue(
        "Expected in-flight constructor with params",
        tracker.hasInFlightDispatches("com.example.Foo", "new", params));
    assertFalse(
        "Expected no-arg constructor to not be in-flight",
        tracker.hasInFlightDispatches("com.example.Foo", "new", NO_PARAMS));

    // Cleanup
    tracker.exitDispatch("com.example.Foo", "new", params);
  }

  // ===== New tests for field-op tracking =====

  @Test
  public void fieldOpTracking_enterExitWithNullParams() throws InterruptedException {
    // Given: An empty tracker
    // When: enterDispatch is called with null parameterTypes (field op)
    tracker.enterDispatch("com.example.Foo", "myField", null);

    // Then: hasInFlightDispatches should detect it
    assertTrue(
        "Expected in-flight field op dispatch",
        tracker.hasInFlightDispatches("com.example.Foo", "myField", null));

    // When: exitDispatch is called
    tracker.exitDispatch("com.example.Foo", "myField", null);

    // Then: No more in-flight dispatches
    assertFalse(
        "Expected no in-flight dispatches after field op exit",
        tracker.hasInFlightDispatches("com.example.Foo", "myField", null));
  }

  @Test
  public void fieldOpTracking_doesNotMatchMethodWithSameName() throws InterruptedException {
    // Given: A field op dispatch for "myField" (null params)
    tracker.enterDispatch("com.example.Foo", "myField", null);

    // Then: A method with the same name should NOT match (different param types)
    assertFalse(
        "Expected field op not to match method query",
        tracker.hasInFlightDispatches("com.example.Foo", "myField", NO_PARAMS));

    // And: The field op query DOES match
    assertTrue(
        "Expected field op to match field query",
        tracker.hasInFlightDispatches("com.example.Foo", "myField", null));

    // Cleanup
    tracker.exitDispatch("com.example.Foo", "myField", null);
  }

  @Test
  public void fencing_fieldDoesNotBlockMethodWithSameName() throws Exception {
    // Given: Fencing on a field "myField" (null params)
    tracker.startFencing("com.example.Foo", "myField", null);

    // When: A method with the same name but with params tries to enter
    CountDownLatch enteredLatch = new CountDownLatch(1);
    AtomicBoolean entered = new AtomicBoolean(false);

    var unused =
        executorService.submit(
            () -> {
              try {
                // This is a method, not a field (has params)
                tracker.enterDispatch("com.example.Foo", "myField", NO_PARAMS);
                entered.set(true);
                enteredLatch.countDown();
                tracker.exitDispatch("com.example.Foo", "myField", NO_PARAMS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: The method should NOT be blocked (field fence doesn't affect methods)
    boolean enteredOk = enteredLatch.await(500, TimeUnit.MILLISECONDS);
    assertTrue("Expected method dispatch to proceed despite field fencing", enteredOk);
    assertTrue("Expected method dispatch to enter", entered.get());

    // Cleanup
    tracker.stopFencing("com.example.Foo", "myField", null);
  }

  // ===== New tests for parameter type differentiation =====

  @Test
  public void parameterTypes_overloadedMethodsHaveSeparateCounters() throws InterruptedException {
    // Given: Two overloaded methods with different parameter types
    String[] oneParam = new String[] {"int"};
    String[] twoParams = new String[] {"int", "int"};

    // When: enterDispatch is called for both overloads
    tracker.enterDispatch("com.example.Calculator", "add", oneParam);
    tracker.enterDispatch("com.example.Calculator", "add", twoParams);

    // Then: Each overload has a separate counter
    assertTrue(
        "Expected add(int) to be in-flight",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", oneParam));
    assertTrue(
        "Expected add(int,int) to be in-flight",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", twoParams));

    // When: exitDispatch is called for add(int) only
    tracker.exitDispatch("com.example.Calculator", "add", oneParam);

    // Then: add(int) should be quiescent, add(int,int) still in-flight
    assertFalse(
        "Expected add(int) to be quiescent after exit",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", oneParam));
    assertTrue(
        "Expected add(int,int) to still be in-flight",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", twoParams));

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add", twoParams);
  }

  @Test
  public void fencing_overloadedMethodFencesOnlyMatchingSignature() throws Exception {
    // Given: Fencing for add(int) only
    String[] oneParam = new String[] {"int"};
    String[] twoParams = new String[] {"int", "int"};

    tracker.startFencing("com.example.Calculator", "add", oneParam);

    // When: A thread tries to enter add(int,int)
    CountDownLatch twoParamLatch = new CountDownLatch(1);
    AtomicBoolean twoParamEntered = new AtomicBoolean(false);

    var unused1 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", twoParams);
                twoParamEntered.set(true);
                twoParamLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add", twoParams);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: add(int,int) should NOT be blocked (different signature)
    boolean twoParamOk = twoParamLatch.await(500, TimeUnit.MILLISECONDS);
    assertTrue("Expected add(int,int) to proceed despite add(int) fencing", twoParamOk);
    assertTrue("Expected add(int,int) dispatch to enter", twoParamEntered.get());

    // And: add(int) SHOULD be blocked
    CountDownLatch oneParamLatch = new CountDownLatch(1);
    AtomicBoolean oneParamEntered = new AtomicBoolean(false);

    var unused2 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add", oneParam);
                oneParamEntered.set(true);
                oneParamLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add", oneParam);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    boolean oneParamBlocked = oneParamLatch.await(500, TimeUnit.MILLISECONDS);
    assertFalse("Expected add(int) to be blocked by fencing", oneParamBlocked);
    assertFalse("Expected add(int) dispatch to remain blocked", oneParamEntered.get());

    // Cleanup
    tracker.stopFencing("com.example.Calculator", "add", oneParam);
    oneParamLatch.await(1, TimeUnit.SECONDS);
  }

  @Test
  public void quiescence_overloadedMethodWaitsOnlyForMatchingSignature() throws Exception {
    // Given: add(int) is in-flight but add(int,int) is not
    String[] oneParam = new String[] {"int"};
    String[] twoParams = new String[] {"int", "int"};
    tracker.enterDispatch("com.example.Calculator", "add", oneParam);

    // When: waitForQuiescence is called for add(int,int) specifically
    boolean quiescent = tracker.waitForQuiescence("com.example.Calculator", "add", twoParams, 100);

    // Then: Quiescence should be immediate (add(int,int) has no in-flight dispatches)
    assertTrue("Expected immediate quiescence for add(int,int)", quiescent);

    // And: add(int) should still be in-flight
    assertTrue(
        "Expected add(int) to still be in-flight",
        tracker.hasInFlightDispatches("com.example.Calculator", "add", oneParam));

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add", oneParam);
  }

  // ===== Tests for buildKey and matchesPattern helpers =====

  @Test
  public void buildKey_methodWithParams() {
    // Given: A method with parameter types
    String key =
        InFlightDispatchTracker.buildKey(
            "com.example.Calc", "add", new String[] {"int", "java.lang.String"});

    // Then: Key should include parenthesized param types
    assertEquals("com.example.Calc.add(int,java.lang.String)", key);
  }

  @Test
  public void buildKey_methodNoParams() {
    // Given: A no-arg method
    String key = InFlightDispatchTracker.buildKey("com.example.Calc", "reset", new String[0]);

    // Then: Key should include empty parens
    assertEquals("com.example.Calc.reset()", key);
  }

  @Test
  public void buildKey_constructor() {
    // Given: A constructor
    String key = InFlightDispatchTracker.buildKey("com.example.Foo", "new", new String[] {"int"});

    // Then: Key should use "new" with parens
    assertEquals("com.example.Foo.new(int)", key);
  }

  @Test
  public void buildKey_fieldOp() {
    // Given: A field operation (null params)
    String key = InFlightDispatchTracker.buildKey("com.example.Foo", "myField", null);

    // Then: Key should have no parens
    assertEquals("com.example.Foo.myField", key);
  }

  @Test
  public void matchesPattern_exactMatch() {
    // Given: Exact method key and pattern
    assertTrue(
        InFlightDispatchTracker.matchesPattern(
            "com.example.Calc.add(int)", "com.example.Calc.add(int)"));
  }

  @Test
  public void matchesPattern_wildcardClassMethodWithParams() {
    // Given: Wildcard class pattern with exact params
    assertTrue(
        InFlightDispatchTracker.matchesPattern(
            "com.example.*.add(int)", "com.example.Calculator.add(int)"));
  }

  @Test
  public void matchesPattern_differentParams_noMatch() {
    // Given: Same class.method but different param types
    assertFalse(
        InFlightDispatchTracker.matchesPattern(
            "com.example.Calc.add(int)", "com.example.Calc.add(int,int)"));
  }

  @Test
  public void matchesPattern_fieldPatternDoesNotMatchMethod() {
    // Given: Field pattern (no parens) and method key (with parens)
    assertFalse(
        InFlightDispatchTracker.matchesPattern(
            "com.example.Foo.myField", "com.example.Foo.myField()"));
  }

  @Test
  public void matchesPattern_methodPatternDoesNotMatchField() {
    // Given: Method pattern (with parens) and field key (no parens)
    assertFalse(
        InFlightDispatchTracker.matchesPattern(
            "com.example.Foo.myField()", "com.example.Foo.myField"));
  }

  @Test
  public void matchesPattern_fieldExactMatch() {
    // Given: Exact field pattern and key (both without parens)
    assertTrue(
        InFlightDispatchTracker.matchesPattern(
            "com.example.Foo.myField", "com.example.Foo.myField"));
  }
}
