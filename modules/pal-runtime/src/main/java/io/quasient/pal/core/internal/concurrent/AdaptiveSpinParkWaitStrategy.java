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

import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MessagePassingQueue;

/**
 * An adaptive {@link MessagePassingQueue.WaitStrategy} that
 *
 * <ol>
 *   <li><strong>busy-spins</strong> for {@code spinIterations} loops (≈ 100 ns per loop on modern
 *       x86), then
 *   <li>falls back to {@link LockSupport#parkNanos(long)} for {@code parkNanos} ns while the queue
 *       is still empty.
 * </ol>
 *
 * <p>Intended for MPSC consumer threads that need &lt; 1 µs latency at sustained load, yet should
 * yield the CPU during lengthy idle periods.
 *
 * <h3>Contract</h3>
 *
 * Implements the JCTools {@code WaitStrategy} contract:
 *
 * <pre>{@code
 * int idle(int idleCounter) {
 *     // … do something …
 *     return idleCounter + 1;   // so drain() sees an ever-growing counter
 * }
 * }</pre>
 *
 * The counter is reset to 0 by JCTools whenever work is found, so the strategy starts spinning
 * again for the next burst.
 */
public final class AdaptiveSpinParkWaitStrategy implements MessagePassingQueue.WaitStrategy {

  /** Default value for {@code spinIterations} when using the no-args ctor. */
  public static final int SPIN_ITERATIONS_DEFAULT = 50;

  /** Default value for {@code parkNanos} when using the no-args ctor. */
  public static final long PARK_NANOS_DEFAULT = 100_000L;

  /** number of consecutive busy retries before the strategy parks the thread. */
  private final int spinIterations;

  /** duration of the {@code parkNanos} call once the spin budget is exhausted. */
  private final long parkNanos;

  /**
   * @param spinIterations number of consecutive <em>busy</em> retries before the strategy parks the
   *     thread (must be &gt; 0)
   * @param parkNanos duration of the {@code parkNanos} call once the spin budget is exhausted (must
   *     be &gt; 0)
   * @throws IllegalArgumentException if either argument is non-positive
   */
  public AdaptiveSpinParkWaitStrategy(int spinIterations, long parkNanos) {
    if (spinIterations <= 0) {
      throw new IllegalArgumentException("spinIterations must be > 0");
    }
    if (parkNanos <= 0) {
      throw new IllegalArgumentException("parkNanos must be > 0");
    }
    this.spinIterations = spinIterations;
    this.parkNanos = parkNanos;
  }

  /**
   * Convenience ctor that uses the values defined in {@code SPIN_ITERATIONS_DEFAULT} and {@code
   * PARK_NANOS_DEFAULT}.
   */
  public AdaptiveSpinParkWaitStrategy() {
    this(SPIN_ITERATIONS_DEFAULT, PARK_NANOS_DEFAULT);
  }

  @Override
  public int idle(int idleCounter) {
    if (idleCounter > spinIterations) {
      LockSupport.parkNanos(parkNanos);
    } else {
      Thread.onSpinWait(); // JDK 9+ CPU hint
    }
    return idleCounter + 1;
  }

  @Override
  public String toString() {
    return "AdaptiveSpinParkWaitStrategy("
        + "spinIterations="
        + spinIterations
        + ", parkNanos="
        + parkNanos
        + ')';
  }
}
