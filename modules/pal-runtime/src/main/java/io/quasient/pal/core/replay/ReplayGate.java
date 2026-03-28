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
package io.quasient.pal.core.replay;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * WAL-offset-based ordering barrier for cross-thread replay coordination.
 *
 * <p>During deterministic replay, multiple threads may be replaying WAL entries concurrently. The
 * {@code ReplayGate} ensures that entry-point injection threads do not run ahead of the self-caller
 * thread by gating on WAL offsets.
 *
 * <p>Threads call {@link #waitForOffset(long)} to block until all WAL entries prior to their target
 * offset have been processed. The dispatch path calls {@link #advanceTo(long)} after processing
 * each WAL entry, monotonically advancing the gate.
 *
 * <p>When constructed with {@code ordered = false}, the gate is disabled and {@link
 * #waitForOffset(long)} returns immediately without blocking. This corresponds to the {@code
 * --replay-threading=unordered} CLI option.
 *
 * <p>Thread safety: All methods are safe to call concurrently from multiple threads. The gate value
 * is maintained via {@link AtomicLong#updateAndGet}, and spin-waiting uses {@link
 * LockSupport#parkNanos} to balance responsiveness and CPU usage.
 */
public class ReplayGate {

  /** The highest WAL offset that has been fully processed. Initialized to {@code -1}. */
  private final AtomicLong completedOffset;

  /** Whether ordering enforcement is active. When {@code false}, the gate never blocks. */
  private final boolean ordered;

  /**
   * Constructs a new {@code ReplayGate}.
   *
   * @param ordered {@code true} to enforce WAL-offset ordering (blocking mode); {@code false} to
   *     disable ordering (unordered mode, never blocks)
   */
  public ReplayGate(boolean ordered) {
    this.ordered = ordered;
    this.completedOffset = new AtomicLong(-1);
  }

  /**
   * Blocks the calling thread until the gate has advanced to at least {@code targetOffset - 1},
   * meaning all WAL entries prior to the target have been processed.
   *
   * <p>Returns immediately without blocking in any of these cases:
   *
   * <ul>
   *   <li>The gate is in unordered mode ({@code ordered = false})
   *   <li>The target offset is zero or negative (no prior entries to wait for)
   *   <li>The gate has already been advanced past {@code targetOffset - 1}
   * </ul>
   *
   * @param targetOffset the WAL offset that the caller wants to process next; the gate blocks until
   *     all offsets before this one have been completed
   */
  public void waitForOffset(long targetOffset) {
    if (!ordered || targetOffset <= 0) {
      return;
    }
    while (completedOffset.get() < targetOffset - 1) {
      LockSupport.parkNanos(1000); // 1μs spin
    }
  }

  /**
   * Advances the gate to the given offset, indicating that the WAL entry at this offset has been
   * fully processed.
   *
   * <p>The gate only moves forward: if the given offset is less than or equal to the current gate
   * value, this call has no effect. This ensures monotonic advancement even when called
   * concurrently from multiple threads.
   *
   * @param offset the WAL offset that has been completed
   */
  public void advanceTo(long offset) {
    completedOffset.updateAndGet(current -> Math.max(current, offset));
  }

  /**
   * Returns the current completed offset (the highest WAL offset that has been fully processed).
   *
   * @return the current gate value
   */
  public long getCompletedOffset() {
    return completedOffset.get();
  }
}
