/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.internal.concurrent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.jctools.queues.MessagePassingQueue;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@link HwmMessageQueue}.
 *
 * <p>This test class validates the high-water-mark message queue functionality including offer/poll
 * operations, size tracking, and the various fill/drain methods with their strategies.
 */
public class HwmMessageQueueTest {

  @Test
  public void fixedQueue_offerPoll_hwmAndCurrentTrack() {
    HwmMessageQueue<String> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 4, 4);

    assertThat(q.capacity(), is(4));
    assertTrue(q.isEmpty());
    assertThat(q.currentSize(), is(0));
    assertThat(q.highWaterMark(), is(0));

    // Offer up to capacity
    assertTrue(q.offer("a"));
    assertTrue(q.offer("b"));
    assertTrue(q.offer("c"));
    assertTrue(q.offer("d"));
    assertThat(q.currentSize(), is(4));
    assertThat(q.highWaterMark(), is(4));

    // Over capacity should fail for FIXED
    assertThat(q.offer("e"), is(false));
    assertThat(q.currentSize(), is(4));
    assertThat(q.highWaterMark(), is(4));

    // Peek does not change counters
    assertThat(q.peek(), is("a"));
    assertThat(q.currentSize(), is(4));
    assertThat(q.highWaterMark(), is(4));

    // Poll decrements current
    assertThat(q.poll(), is("a"));
    assertThat(q.currentSize(), is(3));
    assertThat(q.highWaterMark(), is(4));

    // Clear resets current, preserves HWM
    q.clear();
    assertTrue(q.isEmpty());
    assertThat(q.currentSize(), is(0));
    assertThat(q.highWaterMark(), is(4));
  }

  @Test
  public void chunkedQueue_growsWithinMax_hwmPersistsAfterDrains() {
    // initial small, but can grow up to max
    HwmMessageQueue<Integer> q = HwmMessageQueue.createQueue(MpscKind.CHUNKED, 2, 8);

    // relaxedOffer behaves like offer for success path
    assertTrue(q.relaxedOffer(1));
    assertTrue(q.relaxedOffer(2));
    assertTrue(q.relaxedOffer(3));
    assertThat(q.currentSize(), is(3));
    assertThat(q.highWaterMark(), is(3));

    // Further offers still succeed within max
    assertTrue(q.offer(4));
    assertTrue(q.offer(5));
    assertThat(q.currentSize(), is(5));
    assertThat(q.highWaterMark(), is(5));

    // relaxedPoll decrements when element exists
    assertThat(q.relaxedPoll(), is(1));
    assertThat(q.currentSize(), is(4));
    assertThat(q.highWaterMark(), is(5));

    // Drain with consumer should empty and decrement correctly
    AtomicInteger sum = new AtomicInteger();
    int drained = q.drain(sum::addAndGet);
    assertThat(drained, is(4));
    assertThat(sum.get(), is(2 + 3 + 4 + 5));
    assertTrue(q.isEmpty());
    assertThat(q.currentSize(), is(0));

    // HWM remains the peak
    assertThat(q.highWaterMark(), is(5));
  }

  @Test
  public void fillAndDrainWithLimit_updatesCounters() {
    HwmMessageQueue<String> q = HwmMessageQueue.createQueue(MpscKind.GROWABLE, 4, 64);

    // Fill with a supplier and limit; exact offer count depends on delegate behavior
    int limit = 10;
    int offered = q.fill(() -> "x", limit);
    assertThat(offered, greaterThanOrEqualTo(1));
    assertThat(offered <= limit, is(true));
    assertThat(q.currentSize(), is(offered));
    assertThat(q.highWaterMark(), is(offered));

    // Drain with a limit smaller than or equal to current size
    AtomicInteger count = new AtomicInteger();
    int toDrain = Math.max(1, offered - 1);
    int drained = q.drain(e -> count.incrementAndGet(), toDrain);
    assertThat(drained, is(toDrain));
    assertThat(count.get(), is(toDrain));
    assertThat(q.currentSize(), is(offered - toDrain));
    assertThat(q.highWaterMark(), is(offered));

    // Drain remaining
    while (q.poll() != null) {
      // no-op
    }
    assertNull(q.relaxedPoll());
    assertThat(q.currentSize(), is(0));
    assertTrue(q.isEmpty());
    assertThat(q.highWaterMark(), greaterThanOrEqualTo(offered));
  }

  @Test
  public void drainWithWaitAndExitCondition_decrementsUntilExit() {
    HwmMessageQueue<Integer> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 8, 8);

    // pre-fill 5
    for (int i = 0; i < 5; i++) {
      assertTrue(q.offer(i));
    }
    assertEquals(5, q.currentSize());

    // Exit after consuming all current elements once
    MessagePassingQueue.ExitCondition ec =
        new MessagePassingQueue.ExitCondition() {
          private int remaining = 5;

          @Override
          public boolean keepRunning() {
            return remaining-- > 0;
          }
        };

    MessagePassingQueue.WaitStrategy ws = idleCounter -> 0; // no-op wait

    AtomicInteger consumed = new AtomicInteger();
    q.drain(e -> consumed.incrementAndGet(), ws, ec);

    assertThat(consumed.get(), is(5));
    assertThat(q.currentSize(), is(0));
    assertThat(q.highWaterMark(), is(5));
  }

  // ========== Test Specifications for Issue #555 ==========

  /**
   * Test specification: Verify that fill(Supplier) populates the queue.
   *
   * <p>This test validates that the fill method with only a Supplier parameter populates the queue
   * with elements supplied by the Supplier and correctly updates the size and high-water-mark
   * counters.
   *
   * @see HwmMessageQueue#fill(MessagePassingQueue.Supplier)
   */
  @Test
  @Ignore("Awaiting implementation in #556")
  public void testFill_withSupplier_populatesQueue() {
    // Given: An empty queue and a supplier that produces elements
    // When: fill(Supplier) is called
    // Then: Queue is populated with supplied items
    // And: currentSize() reflects the number of items added
    // And: highWaterMark() is updated to reflect the peak

    // TODO(#556): Implement test logic
    // Implementation hints:
    // - Create a queue with known capacity
    // - Create a supplier that returns predictable values
    // - Call fill(supplier) without limit
    // - Verify queue contains expected elements via poll()
    // - Verify size counters are correct
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Test specification: Verify that fill with WaitStrategy and ExitCondition populates queue.
   *
   * <p>This test validates that the fill method with Supplier, WaitStrategy, and ExitCondition
   * parameters correctly populates the queue while respecting the exit condition and using the wait
   * strategy when the queue is full.
   *
   * @see HwmMessageQueue#fill(MessagePassingQueue.Supplier, MessagePassingQueue.WaitStrategy,
   *     MessagePassingQueue.ExitCondition)
   */
  @Test
  @Ignore("Awaiting implementation in #556")
  public void testFill_withWaitStrategyAndExitCondition_populatesQueue() {
    // Given: An empty queue, a supplier, a no-op wait strategy, and an exit condition
    // When: fill(Supplier, WaitStrategy, ExitCondition) is called
    // Then: Queue is populated respecting the exit condition
    // And: Wait strategy is used when queue is full (if applicable)
    // And: Loop terminates when exit condition returns false

    // TODO(#556): Implement test logic
    // Implementation hints:
    // - Create a fixed-size queue
    // - Create an exit condition that limits iterations (e.g., countdown)
    // - Use a no-op wait strategy: idleCounter -> 0
    // - Verify the fill loop terminates as expected
    // - Verify counters are correct after fill completes
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Test specification: Verify that relaxedPeek returns head without removing it.
   *
   * <p>This test validates that the relaxedPeek method returns the head element of the queue
   * without removing it, leaving the queue state unchanged.
   *
   * @see HwmMessageQueue#relaxedPeek()
   */
  @Test
  @Ignore("Awaiting implementation in #556")
  public void testRelaxedPeek_returnsHeadWithoutRemoving() {
    // Given: A queue with one or more items
    // When: relaxedPeek() is called
    // Then: The head element is returned
    // And: The queue size remains unchanged
    // And: Subsequent peek/poll returns the same element

    // TODO(#556): Implement test logic
    // Implementation hints:
    // - Create queue and add known elements
    // - Record initial size
    // - Call relaxedPeek() multiple times
    // - Verify same element returned each time
    // - Verify size unchanged
    // - Verify poll() returns the same element
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Test specification: Verify that size() returns the correct queue size.
   *
   * <p>This test validates that the size() method (delegated to underlying queue) returns the
   * correct number of elements in the queue.
   *
   * @see HwmMessageQueue#size()
   */
  @Test
  @Ignore("Awaiting implementation in #556")
  public void testSize_returnsCorrectSize() {
    // Given: A queue with a known number of items
    // When: size() is called
    // Then: Returns the correct count matching currentSize()

    // TODO(#556): Implement test logic
    // Implementation hints:
    // - Create queue and add known number of elements
    // - Call size() and compare with currentSize()
    // - Add more elements and verify size updates
    // - Remove elements and verify size decrements
    // Note: size() is the delegate's size which may differ from currentSize() under contention
    org.junit.Assert.fail("Not yet implemented");
  }
}
