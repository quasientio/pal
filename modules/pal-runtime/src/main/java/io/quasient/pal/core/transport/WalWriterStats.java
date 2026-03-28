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
