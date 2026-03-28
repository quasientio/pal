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
package io.quasient.pal.core.transport.gateway;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Stats record - intentionally shared for monitoring")
public record MessageQueueStats(
    long messagesDropped,
    long totalParkedNanos,
    int totalParks,
    int totalFailedOffers,
    List<ThreadWaitSnapshot> perThread) {}
