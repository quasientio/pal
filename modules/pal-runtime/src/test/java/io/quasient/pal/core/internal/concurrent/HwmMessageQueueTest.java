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
package io.quasient.pal.core.internal.concurrent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.jctools.queues.MessagePassingQueue;
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

  /**
   * Test: Verify that fill(Supplier) populates the queue.
   *
   * <p>This test validates that the fill method with only a Supplier parameter populates the queue
   * with elements supplied by the Supplier and correctly updates the size and high-water-mark
   * counters.
   *
   * @see HwmMessageQueue#fill(MessagePassingQueue.Supplier)
   */
  @Test
  public void testFill_withSupplier_populatesQueue() {
    // Given: An empty queue and a supplier that produces elements
    HwmMessageQueue<String> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 8, 8);
    AtomicInteger callCount = new AtomicInteger(0);
    MessagePassingQueue.Supplier<String> supplier =
        () -> {
          int count = callCount.incrementAndGet();
          return "item-" + count;
        };

    // When: fill(Supplier) is called
    int offered = q.fill(supplier);

    // Then: Queue is populated with supplied items
    assertThat(offered, greaterThanOrEqualTo(1));

    // And: currentSize() reflects the number of items added
    assertThat(q.currentSize(), is(offered));

    // And: highWaterMark() is updated to reflect the peak
    assertThat(q.highWaterMark(), is(offered));

    // Verify queue contains expected elements via poll()
    String first = q.poll();
    assertThat(first, is("item-1"));
  }

  /**
   * Test: Verify that fill with WaitStrategy and ExitCondition populates queue.
   *
   * <p>This test validates that the fill method with Supplier, WaitStrategy, and ExitCondition
   * parameters correctly populates the queue while respecting the exit condition and using the wait
   * strategy when the queue is full.
   *
   * @see HwmMessageQueue#fill(MessagePassingQueue.Supplier, MessagePassingQueue.WaitStrategy,
   *     MessagePassingQueue.ExitCondition)
   */
  @Test
  public void testFill_withWaitStrategyAndExitCondition_populatesQueue() {
    // Given: An empty queue, a supplier, a no-op wait strategy, and an exit condition
    HwmMessageQueue<Integer> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 8, 8);
    AtomicInteger supplierCallCount = new AtomicInteger(0);
    MessagePassingQueue.Supplier<Integer> supplier = () -> supplierCallCount.incrementAndGet();

    // Use a no-op wait strategy: idleCounter -> 0
    MessagePassingQueue.WaitStrategy ws = idleCounter -> 0;

    // Exit condition that limits to 5 iterations
    AtomicInteger remaining = new AtomicInteger(5);
    MessagePassingQueue.ExitCondition ec = () -> remaining.decrementAndGet() >= 0;

    // When: fill(Supplier, WaitStrategy, ExitCondition) is called
    q.fill(supplier, ws, ec);

    // Then: Queue is populated respecting the exit condition
    // The number of items added depends on exact semantics
    assertThat(q.currentSize(), greaterThanOrEqualTo(1));

    // And: Loop terminates when exit condition returns false
    // Verify counters are correct after fill completes
    assertThat(q.highWaterMark(), is(q.currentSize()));
  }

  /**
   * Test: Verify that relaxedPeek returns head without removing it.
   *
   * <p>This test validates that the relaxedPeek method returns the head element of the queue
   * without removing it, leaving the queue state unchanged.
   *
   * @see HwmMessageQueue#relaxedPeek()
   */
  @Test
  public void testRelaxedPeek_returnsHeadWithoutRemoving() {
    // Given: A queue with known elements
    HwmMessageQueue<String> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 8, 8);
    assertTrue(q.offer("first"));
    assertTrue(q.offer("second"));
    assertTrue(q.offer("third"));
    int initialSize = q.currentSize();
    assertThat(initialSize, is(3));

    // When: relaxedPeek() is called
    String peeked = q.relaxedPeek();

    // Then: The head element is returned
    assertThat(peeked, is("first"));

    // And: The queue size remains unchanged
    assertThat(q.currentSize(), is(initialSize));

    // And: Subsequent relaxedPeek returns the same element
    String peekedAgain = q.relaxedPeek();
    assertThat(peekedAgain, is("first"));
    assertThat(q.currentSize(), is(initialSize));

    // And: poll() returns the same element
    String polled = q.poll();
    assertThat(polled, is("first"));
    assertThat(q.currentSize(), is(initialSize - 1));
  }

  /**
   * Test: Verify that size() returns the correct queue size.
   *
   * <p>This test validates that the size() method (delegated to underlying queue) returns the
   * correct number of elements in the queue.
   *
   * @see HwmMessageQueue#size()
   */
  @Test
  public void testSize_returnsCorrectSize() {
    // Given: A queue with a known number of items
    HwmMessageQueue<String> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 8, 8);
    assertTrue(q.offer("a"));
    assertTrue(q.offer("b"));
    assertTrue(q.offer("c"));

    // When: size() is called
    int size = q.size();

    // Then: Returns the correct count (should match or be close to currentSize)
    assertThat(size, is(3));
    assertThat(q.currentSize(), is(3));

    // Add more elements and verify size updates
    assertTrue(q.offer("d"));
    assertThat(q.size(), is(4));
    assertThat(q.currentSize(), is(4));

    // Remove elements and verify size decrements
    q.poll();
    assertThat(q.size(), is(3));
    assertThat(q.currentSize(), is(3));

    q.poll();
    q.poll();
    assertThat(q.size(), is(1));
    assertThat(q.currentSize(), is(1));

    q.poll();
    assertThat(q.size(), is(0));
    assertThat(q.currentSize(), is(0));
    assertTrue(q.isEmpty());
  }
}
