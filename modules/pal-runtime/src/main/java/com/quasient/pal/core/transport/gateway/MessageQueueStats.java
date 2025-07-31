/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.gateway;

import java.util.List;

/**
 * Record of OutboundMessageGateway aggregate run stats (all threads).
 *
 * @param messagesDropped number of messages to be enqueued that were dropped due to congestion.
 * @param totalParkedNanos nanoseconds spent in {@code LockSupport.parkNanos()}
 * @param totalParks number of times a thread was parked
 * @param totalFailedOffers number of times the call to {@code queue.offer(..)} returned false
 * @param perThread list of per-thread {@link ThreadWaitSnapshot}'s
 */
public record MessageQueueStats(
    long messagesDropped,
    long totalParkedNanos,
    int totalParks,
    int totalFailedOffers,
    List<ThreadWaitSnapshot> perThread) {}
