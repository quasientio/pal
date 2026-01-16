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

import java.util.concurrent.atomic.AtomicInteger;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscArrayQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.jctools.queues.MpscGrowableArrayQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;

/**
 * Wraps an {@link MpscUnboundedArrayQueue} and tracks
 *
 * <ul>
 *   <li>current depth (exact) – {@link #currentSize()}
 *   <li>peak depth (high-water-mark) – {@link #highWaterMark()}
 * </ul>
 *
 * <p>All counter updates are O(1) and use only plain atomics.
 *
 * @param <E> the type of elements held in this queue
 */
public final class HwmMessageQueue<E> implements MessagePassingQueue<E> {

  /** Underlying multi-producer, single-consumer queue. */
  private final MessagePassingQueue<E> delegate;

  /** Tracks the current number of elements in the queue. */
  private final AtomicInteger current = new AtomicInteger();

  /** Tracks the highest number of elements ever observed in the queue. */
  private final AtomicInteger hwm = new AtomicInteger();

  /**
   * Constructs a new high-water-mark tracking queue.
   *
   * @param wrapped the queue that will be wrapped by this class and used as delegate
   */
  private HwmMessageQueue(MessagePassingQueue<E> wrapped) {
    delegate = wrapped;
  }

  /* ---------- Factory methods ---------------------------------------- */

  /**
   * Creates and returns a {@link HwmMessageQueue} wrapping a MPSC queue of the specified kind.
   *
   * @param kind see {@link MpscKind} for options
   * @param initial initial size of queue
   * @param max max capacity for bounded variable-sized types. The maximum capacity will be rounded
   *     up to the closest power of 2 and will be the upper limit of number of elements in this
   *     queue. Must be 4 or more and round up to a larger power of 2 than initialCapacity.
   * @return the initialized and wrapped queue, null if kind is {@link MpscKind#NONE}
   * @param <E> the type of the queue elements
   */
  public static <E> HwmMessageQueue<E> createQueue(MpscKind kind, int initial, int max) {
    return switch (kind) {
      case NONE -> null;
      case FIXED -> new HwmMessageQueue<>(new MpscArrayQueue<>(initial));
      case CHUNKED -> new HwmMessageQueue<>(new MpscChunkedArrayQueue<>(initial, max));
      case GROWABLE -> new HwmMessageQueue<>(new MpscGrowableArrayQueue<>(initial, max));
    };
  }

  /* ---------- helpers ------------------------------------------------ */

  /**
   * Increment the current counter and update the high-water-mark if needed. Opportunistic—races do
   * not affect correctness of the high-water-mark.
   */
  private void inc() {
    int n = current.incrementAndGet();
    if (n > hwm.get()) { // opportunistic – races don’t matter
      hwm.lazySet(n);
    }
  }

  /** Decrement the current counter. */
  private void dec() {
    current.decrementAndGet();
  }

  /* ---------- puts --------------------------------------------------- */

  /**
   * Called from a producer thread subject to the restrictions appropriate to the implementation and
   * according to the {@link java.util.Queue#offer(Object)} interface.
   *
   * @param e not null, will throw NPE if it is
   * @return true if element was inserted into the queue, false iff full
   */
  @Override
  public boolean offer(E e) {
    boolean ok = delegate.offer(e);
    if (ok) inc();
    return ok;
  }

  /**
   * Called from a producer thread subject to the restrictions appropriate to the implementation. As
   * opposed to {@link #offer(Object)} this method may return false without the queue being full.
   *
   * @param e not null, will throw NPE if it is
   * @return true if element was inserted into the queue, false if unable to offer
   */
  @Override
  public boolean relaxedOffer(E e) {
    boolean ok = delegate.relaxedOffer(e);
    if (ok) inc();
    return ok;
  }

  /**
   * Stuff the queue with elements from the supplier. Semantically similar to:
   *
   * <pre>
   *   while (relaxedOffer(s.get()));
   * </pre>
   *
   * There's no strong commitment to the queue being full at the end of a fill. Called from a
   * producer thread subject to the restrictions appropriate to the implementation.
   *
   * @param s supplier of elements to insert (must not be null)
   * @return the number of offered elements
   */
  @Override
  public int fill(Supplier<E> s) {
    return delegate.fill(
        () -> {
          E e = s.get();
          inc();
          return e;
        });
  }

  /**
   * Stuff the queue with up to {@code limit} elements from the supplier. Semantically similar to:
   * {@code for(int i = 0; i < limit && relaxedOffer(s.get()); i++);}
   *
   * <p>There's no strong commitment to the queue being full at the end of a fill. Called from a
   * producer thread subject to the restrictions appropriate to the implementation.
   *
   * @param s supplier of elements to insert (must not be null)
   * @param limit maximum number of elements to insert (must be non-negative)
   * @return the number of offered elements
   */
  @Override
  public int fill(Supplier<E> s, int limit) {
    return delegate.fill(
        () -> {
          E e = s.get();
          inc();
          return e;
        },
        limit);
  }

  /**
   * Stuff the queue with elements from the supplier forever. Semantically similar to:
   *
   * <pre>
   *   int idleCounter = 0;
   *   while (exit.keepRunning()) {
   *     E e = s.get();
   *     while (!relaxedOffer(e)) {
   *       idleCounter = wait.idle(idleCounter);
   *     }
   *     idleCounter = 0;
   *   }
   * </pre>
   *
   * Called from a producer thread subject to the restrictions appropriate to the implementation.
   * Implementors MUST ensure room before calling {@link Supplier#get()}.
   *
   * @param s supplier of elements to insert (must not be null)
   * @param w wait strategy to apply when the queue is full (must not be null)
   * @param ec exit condition to break out of the loop (must not be null)
   */
  @Override
  public void fill(Supplier<E> s, WaitStrategy w, ExitCondition ec) {
    delegate.fill(
        () -> {
          E e = s.get();
          inc();
          return e;
        },
        w,
        ec);
  }

  /* ---------- takes -------------------------------------------------- */

  /**
   * Called from the consumer thread subject to the restrictions appropriate to the implementation
   * and according to the {@link java.util.Queue#poll()} interface.
   *
   * @return a message from the queue if one is available, null iff empty
   */
  @Override
  public E poll() {
    E e = delegate.poll();
    if (e != null) dec();
    return e;
  }

  /**
   * Called from the consumer thread subject to the restrictions appropriate to the implementation.
   * As opposed to {@link #poll()} this method may return null without the queue being empty.
   *
   * @return a message from the queue if one is available, null if unable to poll
   */
  @Override
  public E relaxedPoll() {
    E e = delegate.relaxedPoll();
    if (e != null) dec();
    return e;
  }

  /**
   * Remove all available items from the queue and hand to consumer. Should be semantically similar
   * to:
   *
   * <pre>
   *   M m;
   *   while((m = relaxedPoll()) != null){ c.accept(m); }
   * </pre>
   *
   * There's no strong commitment to the queue being empty at the end of a drain. Called from a
   * consumer thread subject to the restrictions appropriate to the implementation.
   *
   * @param c consumer to process each element (must not be null)
   * @return the number of polled elements
   */
  @Override
  public int drain(Consumer<E> c) {
    return delegate.drain(
        e -> {
          c.accept(e);
          dec();
        });
  }

  /**
   * Remove up to {@code limit} elements from the queue and hand to consumer. Semantically similar
   * to: {@code for(int i = 0; i < limit && (m = relaxedPoll()) != null; i++){ c.accept(m); }}
   *
   * <p>There's no strong commitment to the queue being empty at the end of a drain. Called from a
   * consumer thread subject to the restrictions appropriate to the implementation.
   *
   * @param c consumer to process each element (must not be null)
   * @param limit maximum number of elements to drain (must be non-negative)
   * @return the number of polled elements
   */
  @Override
  public int drain(Consumer<E> c, int limit) {
    return delegate.drain(
        e -> {
          c.accept(e);
          dec();
        },
        limit);
  }

  /**
   * Remove elements from the queue and hand to consumer forever. Semantically similar to:
   *
   * <pre>
   *   int idleCounter = 0;
   *   while (exit.keepRunning()) {
   *     E e = relaxedPoll();
   *     if (e == null) {
   *       idleCounter = wait.idle(idleCounter);
   *       continue;
   *     }
   *     idleCounter = 0;
   *     c.accept(e);
   *   }
   * </pre>
   *
   * Called from a consumer thread subject to the restrictions appropriate to the implementation.
   *
   * @param c consumer to process each element (must not be null)
   * @param w wait strategy when the queue is empty (must not be null)
   * @param ec exit condition to break out of the loop (must not be null)
   */
  @Override
  public void drain(Consumer<E> c, WaitStrategy w, ExitCondition ec) {
    delegate.drain(
        e -> {
          c.accept(e);
          dec();
        },
        w,
        ec);
  }

  /* ---------- pure delegates ---------------------------------------- */

  /**
   * {@inheritDoc}
   *
   * @return a message from the queue if one is available, null iff empty
   */
  @Override
  public E peek() {
    return delegate.peek();
  }

  /**
   * {@inheritDoc}
   *
   * @return a message from the queue if one is available, null if unable to peek
   */
  @Override
  public E relaxedPeek() {
    return delegate.relaxedPeek();
  }

  /**
   * This method's accuracy is subject to concurrent modifications happening as the size is
   * estimated and as such is a best effort rather than absolute value. For some implementations
   * this method may be O(n) rather than O(1).
   *
   * @return number of messages in the queue (debug only)
   */
  @Override
  public int size() {
    return delegate.size();
  }

  /**
   * Removes all items from the queue. Called from the consumer thread subject to the restrictions
   * appropriate to the implementation and according to the {@link java.util.Collection#clear()}
   * interface.
   */
  @Override
  public void clear() {
    delegate.clear();
    current.set(0);
  }

  /**
   * This method's accuracy is subject to concurrent modifications happening as the observation is
   * carried out.
   *
   * @return true if empty, false otherwise
   */
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  /**
   * Returns the capacity of this queue or {@link MessagePassingQueue#UNBOUNDED_CAPACITY} if not
   * bounded.
   *
   * @return the capacity of this queue
   */
  @Override
  public int capacity() {
    return delegate.capacity();
  }

  /* ---------- stats -------------------------------------------------- */

  /**
   * Gets the current number of elements in the queue.
   *
   * @return the exact current size
   */
  public int currentSize() {
    return current.get();
  }

  /**
   * Gets the highest number of elements that have ever been in the queue.
   *
   * @return the high-water mark
   */
  public int highWaterMark() {
    return hwm.get();
  }
}
