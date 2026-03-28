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
