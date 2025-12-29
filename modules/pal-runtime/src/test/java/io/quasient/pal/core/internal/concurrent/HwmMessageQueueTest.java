/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
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
import org.junit.Test;

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
}
