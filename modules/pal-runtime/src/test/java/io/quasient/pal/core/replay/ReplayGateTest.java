/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayGate} — the WAL-offset-based ordering barrier that coordinates
 * cross-thread replay ordering during deterministic replay.
 *
 * <p>ReplayGate prevents entry-point injection threads from running ahead of the self-caller thread
 * by gating on WAL offsets. Threads call {@code waitForOffset()} to block until the gate has been
 * advanced past their target offset via {@code advanceTo()}.
 *
 * <p>Tests cover monotonic gate advancement, immediate proceed when already past, blocking until
 * gate advances, zero-offset semantics, unordered mode bypass, concurrent thread safety, and
 * multiple-waiter ordering.
 */
public class ReplayGateTest {

  /**
   * Verifies that the gate value only increases monotonically, ignoring attempts to set it to a
   * lower value.
   */
  @Test
  public void advanceTo_updatesGateMonotonically() {
    // Given: ReplayGate with initial state (no offsets advanced)
    ReplayGate gate = new ReplayGate(true);

    // When: advanceTo(10) then advanceTo(20) then advanceTo(15)
    gate.advanceTo(10);
    assertThat(gate.getCompletedOffset(), is(10L));

    gate.advanceTo(20);
    assertThat(gate.getCompletedOffset(), is(20L));

    gate.advanceTo(15);
    // Then: Current gate value is 20 (never goes backward)
    assertThat(gate.getCompletedOffset(), is(20L));
  }

  /**
   * Verifies that {@code waitForOffset()} returns immediately when the gate has already been
   * advanced past the requested offset.
   */
  @Test
  public void waitForOffset_proceedsImmediatelyWhenAlreadyPast() {
    // Given: ReplayGate with gate advanced to offset 50
    ReplayGate gate = new ReplayGate(true);
    gate.advanceTo(50);

    // When: waitForOffset(30) called
    long start = System.nanoTime();
    gate.waitForOffset(30);
    long elapsed = System.nanoTime() - start;

    // Then: Returns immediately without blocking (less than 5ms)
    assertThat(elapsed, is(lessThan(TimeUnit.MILLISECONDS.toNanos(5))));
  }

  /**
   * Verifies that {@code waitForOffset()} blocks the calling thread until another thread advances
   * the gate to (or past) the requested offset.
   */
  @Test
  public void waitForOffset_blocksUntilGateAdvanced() throws Exception {
    // Given: ReplayGate with gate at initial state (-1)
    ReplayGate gate = new ReplayGate(true);
    AtomicBoolean unblocked = new AtomicBoolean(false);
    CountDownLatch done = new CountDownLatch(1);

    // When: Thread A calls waitForOffset(10) (blocks)
    Thread waiter =
        new Thread(
            () -> {
              gate.waitForOffset(10);
              unblocked.set(true);
              done.countDown();
            });
    waiter.start();

    // Verify still blocked after a short wait
    Thread.sleep(50);
    assertThat("Should still be blocked before advanceTo", unblocked.get(), is(false));

    // Thread B calls advanceTo(9) after 100ms delay
    gate.advanceTo(9);

    // Then: Thread A unblocks after Thread B advances the gate
    boolean completed = done.await(2, TimeUnit.SECONDS);
    assertThat("Waiter thread should have completed", completed, is(true));
    assertThat(unblocked.get(), is(true));
  }

  /** Verifies that waiting for offset zero never blocks, regardless of the current gate value. */
  @Test
  public void waitForOffset_zeroOffsetNeverBlocks() {
    // Given: ReplayGate with initial state (gate at -1)
    ReplayGate gate = new ReplayGate(true);

    // When: waitForOffset(0) called
    long start = System.nanoTime();
    gate.waitForOffset(0);
    long elapsed = System.nanoTime() - start;

    // Then: Returns immediately without blocking (less than 5ms)
    assertThat(elapsed, is(lessThan(TimeUnit.MILLISECONDS.toNanos(5))));
  }

  /**
   * Verifies that a ReplayGate created in unordered mode never blocks, even for offsets that have
   * not been reached.
   */
  @Test
  public void unorderedMode_neverBlocks() {
    // Given: ReplayGate created with ordered = false (unordered mode)
    ReplayGate gate = new ReplayGate(false);

    // When: waitForOffset(999999) called without any advanceTo()
    long start = System.nanoTime();
    gate.waitForOffset(999999);
    long elapsed = System.nanoTime() - start;

    // Then: Returns immediately without blocking (less than 5ms)
    assertThat(elapsed, is(lessThan(TimeUnit.MILLISECONDS.toNanos(5))));
  }

  /**
   * Verifies that concurrent calls to {@code advanceTo()} from multiple threads are handled safely,
   * with the final gate value reflecting the maximum offset provided.
   */
  @Test
  public void concurrentAdvanceTo_threadSafe() throws Exception {
    // Given: ReplayGate with initial state
    ReplayGate gate = new ReplayGate(true);
    int threadCount = 10;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    CountDownLatch done = new CountDownLatch(threadCount);

    // When: 10 threads concurrently call advanceTo() with values 1 through 10
    for (int i = 1; i <= threadCount; i++) {
      final long offset = i;
      new Thread(
              () -> {
                try {
                  barrier.await(5, TimeUnit.SECONDS);
                  gate.advanceTo(offset);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                } finally {
                  done.countDown();
                }
              })
          .start();
    }

    boolean completed = done.await(5, TimeUnit.SECONDS);
    assertThat("All threads should complete", completed, is(true));

    // Then: Final gate value is 10; no exceptions thrown
    assertThat(gate.getCompletedOffset(), is(10L));
  }

  /**
   * Verifies that multiple threads waiting on different offsets unblock in the correct order as the
   * gate advances.
   */
  @Test
  public void waitForOffset_multipleWaiters() throws Exception {
    // Given: ReplayGate at initial state
    ReplayGate gate = new ReplayGate(true);
    List<Long> unblockOrder = Collections.synchronizedList(new ArrayList<>());
    AtomicLong sequencer = new AtomicLong(0);
    CountDownLatch waitersReady = new CountDownLatch(2);
    CountDownLatch allDone = new CountDownLatch(2);

    // When: Thread A waits for offset 5; Thread B waits for offset 10
    Thread waiterA =
        new Thread(
            () -> {
              waitersReady.countDown();
              gate.waitForOffset(5);
              unblockOrder.add(sequencer.getAndIncrement());
              allDone.countDown();
            });

    Thread waiterB =
        new Thread(
            () -> {
              waitersReady.countDown();
              gate.waitForOffset(10);
              unblockOrder.add(sequencer.getAndIncrement());
              allDone.countDown();
            });

    waiterA.start();
    waiterB.start();

    // Wait for both waiters to be blocked
    waitersReady.await(2, TimeUnit.SECONDS);
    Thread.sleep(50); // Give threads time to enter waitForOffset

    // Gate advances to 4 (offset 5 needs completedOffset >= 4)
    gate.advanceTo(4);
    Thread.sleep(50); // Give thread A time to unblock

    // Then: Thread A unblocks first (at advance to 4, since waitForOffset(5) needs >= 4)
    assertThat(
        "At least waiter A should have unblocked", unblockOrder.size(), greaterThanOrEqualTo(1));
    assertThat("Waiter A should unblock first", unblockOrder.get(0), is(0L));

    // Gate advances to 9 (offset 10 needs completedOffset >= 9)
    gate.advanceTo(9);

    // Thread B unblocks second (at advance to 9)
    boolean completed = allDone.await(2, TimeUnit.SECONDS);
    assertThat("Both waiters should complete", completed, is(true));
    assertThat("Both waiters should have recorded", unblockOrder.size(), is(2));
    assertThat("Waiter B should unblock second", unblockOrder.get(1), is(1L));
  }
}
