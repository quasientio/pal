/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.zmq.publish;

/**
 * Record of MessagePublisher run stats.
 *
 * @param messagesReceived number of messages received from the {@code pubQueue}
 * @param messagesPublished number of messages published since the socket first failed
 * @param messagesDroppedUnforwarded number of new messages dropped due to internal SPSC queue
 *     congestion when drop policy is {@link PublishingDropPolicy#DROP_NEW}
 * @param messagesDroppedEvicted number of old messages dropped due to internal SPSC queue
 *     congestion when drop policy is {@link PublishingDropPolicy#DROP_OLD}
 * @param messagesInSpsc number of messages in internal SPSC
 * @param messagesDroppedPubFail number of messages dropped due to PUB send unsuccessful
 * @param messagesDroppedSocketErr number of messages dropped due to PUB socket error
 */
public record MessagePublisherStats(
    long messagesReceived,
    long messagesPublished,
    long messagesDroppedUnforwarded,
    long messagesDroppedEvicted,
    long messagesInSpsc,
    long messagesDroppedPubFail,
    long messagesDroppedSocketErr) {}
