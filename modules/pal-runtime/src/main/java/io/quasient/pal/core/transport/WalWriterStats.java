/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport;

/**
 * Record of {@link WalWriter} run stats.
 *
 * @param messagesReceived number of messages received from the {@code walQueue}
 * @param messagesWritten number of messages published since the socket first failed
 * @param messagesDroppedError number of new messages not written due an error
 * @param messagesInFlight number of new messages currently in-flight (sent/written but no ack)
 */
public record WalWriterStats(
    long messagesReceived,
    long messagesWritten,
    long messagesDroppedError,
    long messagesInFlight) {}
