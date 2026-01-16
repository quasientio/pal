/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.gateway;

/**
 * Per-thread counters tracking how much a producer had to back off when the queue was full.
 *
 * <ul>
 *   <li>{@code parkedNanos} – total nanoseconds spent in {@code LockSupport.parkNanos()}.
 *   <li>{@code parks} – number of times the thread actually parked.
 *   <li>{@code failedOffers} – how many {@code queue.offer(..)} calls returned {@code false}.
 * </ul>
 *
 * <p>All fields are intentionally package-visible to avoid the overhead of getters in the hot path.
 */
public class WaitStats {

  /** total nanoseconds spent in {@code LockSupport.parkNanos()} */
  long parkedNanos;

  /** number of times thread was parked */
  int parks;

  /** how many times the call to offer() returned false */
  int failedOffers;
}
