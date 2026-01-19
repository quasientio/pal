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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * </ul>
 */
public class InFlightDispatchTrackerTest {

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
    tracker.enterDispatch("com.example.Calculator", "add");

    // Then: The in-flight counter for that class+method should be incremented
    // And: hasInFlightDispatches should return true for matching patterns
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add");
    org.junit.Assert.assertTrue("Expected in-flight dispatches after enterDispatch", hasInFlight);

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add");
  }

  @Test
  public void exitDispatch_decrementsCounter() throws InterruptedException {
    // Given: A tracker with an active dispatch (counter = 1)
    tracker.enterDispatch("com.example.Calculator", "add");
    org.junit.Assert.assertTrue(
        "Expected in-flight dispatches after enterDispatch",
        tracker.hasInFlightDispatches("com.example.Calculator", "add"));

    // When: exitDispatch is called for the same class and method
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: The in-flight counter should be decremented back to 0
    // And: hasInFlightDispatches should return false for that pattern
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add");
    org.junit.Assert.assertFalse(
        "Expected no in-flight dispatches after exitDispatch", hasInFlight);
  }

  @Test
  public void hasInFlightDispatches_returnsTrueWhenDispatchActive() throws InterruptedException {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add");

    // When: hasInFlightDispatches is called with pattern "com.example.Calculator.add"
    // Then: It should return true
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add");
    org.junit.Assert.assertTrue("Expected in-flight dispatches to be detected", hasInFlight);

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add");
  }

  @Test
  public void hasInFlightDispatches_returnsFalseWhenNoDispatches() {
    // Given: A tracker with no active dispatches
    // When: hasInFlightDispatches is called with any pattern
    boolean hasInFlight = tracker.hasInFlightDispatches("com.example.Calculator", "add");

    // Then: It should return false
    org.junit.Assert.assertFalse("Expected no in-flight dispatches", hasInFlight);
  }

  @Test
  public void hasInFlightDispatches_supportsWildcardPatterns() throws InterruptedException {
    // Given: Active dispatches for:
    //   - "com.example.Calculator.add"
    //   - "com.example.Calculator.subtract"
    //   - "org.other.Service.process"
    tracker.enterDispatch("com.example.Calculator", "add");
    tracker.enterDispatch("com.example.Calculator", "subtract");
    tracker.enterDispatch("org.other.Service", "process");

    try {
      // When: hasInFlightDispatches is called with various wildcard patterns
      // Then: Pattern "com.example.*" should match the first two
      org.junit.Assert.assertTrue(
          "Expected wildcard pattern 'com.example.*' to match",
          tracker.hasInFlightDispatches("com.example.*", "*"));

      // And: Pattern "*" should match all three
      org.junit.Assert.assertTrue(
          "Expected wildcard pattern '*' to match all", tracker.hasInFlightDispatches("*", "*"));

      // And: Pattern "com.example.Calculator.*" should match the first two
      org.junit.Assert.assertTrue(
          "Expected pattern 'com.example.Calculator.*' to match",
          tracker.hasInFlightDispatches("com.example.Calculator", "*"));

      // And: Pattern "org.other.*" should match only the third
      org.junit.Assert.assertTrue(
          "Expected pattern 'org.other.*' to match",
          tracker.hasInFlightDispatches("org.other.*", "*"));

      // And: Pattern that doesn't match anything should return false
      org.junit.Assert.assertFalse(
          "Expected unmatched pattern to return false",
          tracker.hasInFlightDispatches("com.unrelated.Class", "method"));

    } finally {
      // Cleanup
      tracker.exitDispatch("com.example.Calculator", "add");
      tracker.exitDispatch("com.example.Calculator", "subtract");
      tracker.exitDispatch("org.other.Service", "process");
    }
  }

  @Test
  public void waitForQuiescence_returnsImmediatelyWhenNoDispatches() throws InterruptedException {
    // Given: A tracker with no active dispatches
    // When: waitForQuiescence is called with a pattern and timeout
    long startTime = System.currentTimeMillis();
    boolean quiescent = tracker.waitForQuiescence("com.example.Calculator", "add", 1000);
    long elapsed = System.currentTimeMillis() - startTime;

    // Then: It should return true immediately (before timeout)
    org.junit.Assert.assertTrue("Expected immediate quiescence when no dispatches", quiescent);

    // And: The elapsed time should be negligible (< 100ms)
    org.junit.Assert.assertTrue(
        "Expected negligible wait time but got " + elapsed + "ms", elapsed < 100);
  }

  @Test
  public void waitForQuiescence_blocksUntilDispatchCompletes() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add");

    java.util.concurrent.CountDownLatch exitLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean quiescentResult =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // When: One thread calls waitForQuiescence for "com.example.Calculator.add"
    long startTime = System.currentTimeMillis();
    var unused1 =
        executorService.submit(
            () -> {
              try {
                boolean result = tracker.waitForQuiescence("com.example.Calculator", "add", 5000);
                quiescentResult.set(result);
                exitLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // And: Another thread calls exitDispatch after a short delay (e.g., 200ms)
    Thread.sleep(200);
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: waitForQuiescence should block until exitDispatch is called
    // And: waitForQuiescence should return true
    exitLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    long elapsed = System.currentTimeMillis() - startTime;

    org.junit.Assert.assertTrue("Expected quiescence to be achieved", quiescentResult.get());

    // And: The elapsed time should be approximately the delay time (200ms +/- 100ms)
    org.junit.Assert.assertTrue(
        "Expected elapsed time around 200ms but got " + elapsed + "ms",
        elapsed >= 150 && elapsed <= 350);
  }

  @Test
  public void waitForQuiescence_timeoutReturnsEvenIfDispatchesInFlight() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add");

    try {
      // When: waitForQuiescence is called with a short timeout (e.g., 100ms)
      long startTime = System.currentTimeMillis();
      boolean quiescent = tracker.waitForQuiescence("com.example.Calculator", "add", 100);
      long elapsed = System.currentTimeMillis() - startTime;

      // Then: waitForQuiescence should return false after the timeout expires
      org.junit.Assert.assertFalse("Expected timeout without achieving quiescence", quiescent);

      // And: The elapsed time should be approximately the timeout duration
      org.junit.Assert.assertTrue(
          "Expected elapsed time around 100ms but got " + elapsed + "ms",
          elapsed >= 80 && elapsed <= 200);

      // And: The dispatch should still be in-flight after the call returns
      org.junit.Assert.assertTrue(
          "Expected dispatch still in-flight after timeout",
          tracker.hasInFlightDispatches("com.example.Calculator", "add"));

    } finally {
      // Cleanup
      tracker.exitDispatch("com.example.Calculator", "add");
    }
  }

  @Test
  public void startFencing_blocksNewDispatches() throws Exception {
    // Given: A tracker with fencing started for pattern "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*");

    java.util.concurrent.CountDownLatch enteredLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean entered =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // When: A new thread attempts to call enterDispatch for "com.example.Calculator.add"
    var unused2 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add");
                entered.set(true);
                enteredLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: The enterDispatch call should block indefinitely
    // And: The thread should remain blocked until stopFencing is called
    boolean enteredWithinTimeout =
        enteredLatch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);

    org.junit.Assert.assertFalse(
        "Expected enterDispatch to block when fencing is active", enteredWithinTimeout);
    org.junit.Assert.assertFalse("Expected thread to remain blocked", entered.get());

    // Cleanup: stop fencing and wait for thread to complete
    tracker.stopFencing("com.example.Calculator", "*");
    enteredLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  public void stopFencing_allowsNewDispatches() throws Exception {
    // Given: A tracker with fencing started for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*");

    java.util.concurrent.CountDownLatch enteredLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean entered =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // And: A thread blocked in enterDispatch for "com.example.Calculator.add"
    var unused3 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add");
                entered.set(true);
                enteredLatch.countDown();
                tracker.exitDispatch("com.example.Calculator", "add");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Verify thread is blocked
    Thread.sleep(100);
    org.junit.Assert.assertFalse("Expected thread to be blocked before stopFencing", entered.get());

    // When: stopFencing is called for "com.example.Calculator.*"
    tracker.stopFencing("com.example.Calculator", "*");

    // Then: The blocked enterDispatch call should unblock and proceed
    boolean unblocked = enteredLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    org.junit.Assert.assertTrue("Expected enterDispatch to unblock after stopFencing", unblocked);
    org.junit.Assert.assertTrue("Expected thread to have entered dispatch", entered.get());

    // And: Subsequent enterDispatch calls should not block
    long startTime = System.currentTimeMillis();
    tracker.enterDispatch("com.example.Calculator", "subtract");
    long elapsed = System.currentTimeMillis() - startTime;
    org.junit.Assert.assertTrue(
        "Expected subsequent enterDispatch to not block (took " + elapsed + "ms)", elapsed < 100);
    tracker.exitDispatch("com.example.Calculator", "subtract");
  }

  @Test
  public void enterDispatch_throwsIfInterruptedDuringFencing() throws Exception {
    // Given: A tracker with fencing active for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*");

    java.util.concurrent.CountDownLatch exceptionLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean interruptedExceptionThrown =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // When: A thread calls enterDispatch for "com.example.Calculator.add" (blocks)
    java.util.concurrent.Future<?> future =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add");
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
    boolean exceptionCaught = exceptionLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    org.junit.Assert.assertTrue("Expected InterruptedException to be thrown", exceptionCaught);
    org.junit.Assert.assertTrue(
        "Expected InterruptedException to be thrown", interruptedExceptionThrown.get());

    // Cleanup
    tracker.stopFencing("com.example.Calculator", "*");
  }

  @Test
  public void threadSafety_concurrentEnterExitOperations() throws Exception {
    // Given: A tracker shared across multiple threads
    final int threadCount = 10;
    final int operationsPerThread = 100;
    java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch completeLatch =
        new java.util.concurrent.CountDownLatch(threadCount);
    java.util.concurrent.atomic.AtomicInteger errorCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

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
                    tracker.enterDispatch(className, methodName);

                    // Check in-flight (should find it)
                    if (!tracker.hasInFlightDispatches(className, methodName)) {
                      errorCount.incrementAndGet();
                    }

                    // Exit dispatch
                    tracker.exitDispatch(className, methodName);
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
    boolean completed = completeLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
    org.junit.Assert.assertTrue("Expected all threads to complete", completed);
    org.junit.Assert.assertEquals(
        "Expected no errors during concurrent operations", 0, errorCount.get());

    // And: Final state should be consistent (all counters at 0)
    org.junit.Assert.assertFalse(
        "Expected no in-flight dispatches after all threads complete",
        tracker.hasInFlightDispatches("*", "*"));
  }

  @Test
  public void enterDispatch_multipleCallsSameDispatch_incrementsMultipleTimes()
      throws InterruptedException {
    // Given: A tracker with no active dispatches
    // When: enterDispatch is called 3 times for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add");
    tracker.enterDispatch("com.example.Calculator", "add");
    tracker.enterDispatch("com.example.Calculator", "add");

    // Then: The counter should be 3
    // And: hasInFlightDispatches should return true
    org.junit.Assert.assertTrue(
        "Expected in-flight dispatches after 3 enters",
        tracker.hasInFlightDispatches("com.example.Calculator", "add"));

    // When: exitDispatch is called once
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: The counter should be 2 (still > 0)
    // And: hasInFlightDispatches should still return true
    org.junit.Assert.assertTrue(
        "Expected in-flight dispatches still active after 1 exit",
        tracker.hasInFlightDispatches("com.example.Calculator", "add"));

    // Cleanup
    tracker.exitDispatch("com.example.Calculator", "add");
    tracker.exitDispatch("com.example.Calculator", "add");
  }

  @Test
  public void waitForQuiescence_multipleWaitersAllUnblocked() throws Exception {
    // Given: A tracker with one active dispatch for "com.example.Calculator.add"
    tracker.enterDispatch("com.example.Calculator", "add");

    final int waiterCount = 5;
    java.util.concurrent.CountDownLatch allWaitersCompleteLatch =
        new java.util.concurrent.CountDownLatch(waiterCount);
    java.util.concurrent.atomic.AtomicInteger successCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    // When: Multiple threads (e.g., 5 threads) call waitForQuiescence with same pattern
    for (int i = 0; i < waiterCount; i++) {
      var unused =
          executorService.submit(
              () -> {
                try {
                  boolean quiescent =
                      tracker.waitForQuiescence("com.example.Calculator", "add", 5000);
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
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: All waiting threads should unblock and return true
    boolean allCompleted = allWaitersCompleteLatch.await(2, java.util.concurrent.TimeUnit.SECONDS);
    org.junit.Assert.assertTrue("Expected all waiters to complete", allCompleted);
    org.junit.Assert.assertEquals(
        "Expected all waiters to achieve quiescence", waiterCount, successCount.get());
  }

  @Test
  public void fencing_doesNotBlockAlreadyInFlightDispatches() throws Exception {
    // Given: A tracker with an active dispatch for "com.example.Calculator.add"
    //   (dispatch entered before fencing started)
    tracker.enterDispatch("com.example.Calculator", "add");

    // When: startFencing is called for "com.example.Calculator.*"
    tracker.startFencing("com.example.Calculator", "*");

    // Then: The already in-flight dispatch can complete (exitDispatch succeeds)
    tracker.exitDispatch("com.example.Calculator", "add");
    org.junit.Assert.assertFalse(
        "Expected dispatch to complete successfully",
        tracker.hasInFlightDispatches("com.example.Calculator", "add"));

    // And: Only NEW enterDispatch calls should be blocked
    java.util.concurrent.CountDownLatch newEnterLatch = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean newEntered =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    var unused4 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "subtract");
                newEntered.set(true);
                newEnterLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    boolean newEnteredWithinTimeout =
        newEnterLatch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    org.junit.Assert.assertFalse(
        "Expected new enterDispatch to be blocked by fencing", newEnteredWithinTimeout);
    org.junit.Assert.assertFalse("Expected new dispatch to remain blocked", newEntered.get());

    // Cleanup
    tracker.stopFencing("com.example.Calculator", "*");
    newEnterLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    if (newEntered.get()) {
      tracker.exitDispatch("com.example.Calculator", "subtract");
    }
  }

  @Test
  public void waitForQuiescence_withWildcardPattern_waitsForAllMatching() throws Exception {
    // Given: Active dispatches for:
    //   - "com.example.Calculator.add"
    //   - "com.example.Calculator.subtract"
    tracker.enterDispatch("com.example.Calculator", "add");
    tracker.enterDispatch("com.example.Calculator", "subtract");

    java.util.concurrent.CountDownLatch quiescenceLatch =
        new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean quiescent =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // When: waitForQuiescence is called with pattern "com.example.Calculator.*"
    var unused5 =
        executorService.submit(
            () -> {
              try {
                boolean result = tracker.waitForQuiescence("com.example.Calculator", "*", 5000);
                quiescent.set(result);
                quiescenceLatch.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Give waiter time to start
    Thread.sleep(100);

    // And: Only one dispatch (add) exits
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: waitForQuiescence should remain blocked (subtract still active)
    boolean completedAfterFirstExit =
        quiescenceLatch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    org.junit.Assert.assertFalse(
        "Expected waitForQuiescence to remain blocked after first exit", completedAfterFirstExit);

    // When: The second dispatch (subtract) exits
    tracker.exitDispatch("com.example.Calculator", "subtract");

    // Then: waitForQuiescence should return true
    boolean completedAfterSecondExit =
        quiescenceLatch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    org.junit.Assert.assertTrue(
        "Expected waitForQuiescence to complete after all dispatches exit",
        completedAfterSecondExit);
    org.junit.Assert.assertTrue("Expected quiescence to be achieved", quiescent.get());
  }

  @Test
  public void fencing_withWildcardPattern_blocksAllMatchingDispatches() throws Exception {
    // Given: A tracker with fencing started for "com.example.*"
    tracker.startFencing("com.example.*", "*");

    java.util.concurrent.CountDownLatch latch1 = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch latch2 = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch latch3 = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean entered1 =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicBoolean entered2 =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicBoolean entered3 =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    // When: Multiple threads attempt enterDispatch for:
    //   - "com.example.Calculator.add"
    var unused6 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Calculator", "add");
                entered1.set(true);
                latch1.countDown();
                tracker.exitDispatch("com.example.Calculator", "add");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    //   - "com.example.Service.process"
    var unused7 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("com.example.Service", "process");
                entered2.set(true);
                latch2.countDown();
                tracker.exitDispatch("com.example.Service", "process");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    //   - "org.other.Service.process" (does NOT match pattern)
    var unused8 =
        executorService.submit(
            () -> {
              try {
                tracker.enterDispatch("org.other.Service", "process");
                entered3.set(true);
                latch3.countDown();
                tracker.exitDispatch("org.other.Service", "process");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    // Then: The first two should block
    boolean entered1Timeout = latch1.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    boolean entered2Timeout = latch2.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    org.junit.Assert.assertFalse(
        "Expected com.example.Calculator.add to be blocked", entered1Timeout);
    org.junit.Assert.assertFalse(
        "Expected com.example.Service.process to be blocked", entered2Timeout);

    // And: The third should proceed immediately (different pattern)
    boolean entered3Success = latch3.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
    org.junit.Assert.assertTrue("Expected org.other.Service.process to proceed", entered3Success);
    org.junit.Assert.assertTrue("Expected non-matching dispatch to enter", entered3.get());

    // Cleanup
    tracker.stopFencing("com.example.*", "*");
    latch1.await(1, java.util.concurrent.TimeUnit.SECONDS);
    latch2.await(1, java.util.concurrent.TimeUnit.SECONDS);
  }

  @Test
  public void exitDispatch_withoutMatchingEnter_handlesGracefully() throws InterruptedException {
    // Given: A tracker with no active dispatches for "com.example.Calculator.add"
    // When: exitDispatch is called for "com.example.Calculator.add"
    tracker.exitDispatch("com.example.Calculator", "add");

    // Then: It should handle gracefully (either no-op or log warning)
    // And: Should not throw exception or cause negative counter
    // (The test passes if no exception is thrown)

    // Verify that subsequent operations still work
    tracker.enterDispatch("com.example.Calculator", "add");
    org.junit.Assert.assertTrue(
        "Expected dispatch to be tracked normally after graceful exit",
        tracker.hasInFlightDispatches("com.example.Calculator", "add"));
    tracker.exitDispatch("com.example.Calculator", "add");
  }
}
