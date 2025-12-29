/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.gateway;

/**
 * Immutable snapshot of queue backpressure metrics for a single producer thread.
 *
 * @param threadId JVM thread id ({@link Thread#getId()}).
 * @param threadName Human-friendly name of the thread ({@link Thread#getName()}), captured at
 *     snapshot time.
 * @param parkedNanos Total nanoseconds this thread spent parked (via {@code LockSupport.parkNanos})
 *     while retrying offers.
 * @param parks Number of times the thread actually parked (i.e. back-off iterations that called
 *     {@code parkNanos(..)}).
 * @param failedOffers How many {@code walQueue.offer(..)} attempts returned {@code false} before
 *     succeeding.
 */
public record ThreadWaitSnapshot(
    long threadId, String threadName, long parkedNanos, int parks, int failedOffers) {}
