/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport;

/**
 * Record of {@link WalWriter} run stats.
 *
 * @param messagesReceived number of messages received from the {@code walQueue}
 * @param messagesWritten number of messages published since the socket first failed
 * @param messagesDroppedError number of new messages not written due an error
 */
public record WalWriterStats(
    long messagesReceived, long messagesWritten, long messagesDroppedError) {}
