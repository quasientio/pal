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
/**
 * Message I/O infrastructure for write-ahead logs and network transport.
 *
 * <p>This package defines abstractions for reading from source logs and writing to write-ahead logs
 * (WAL), with implementations for different backends:
 *
 * <ul>
 *   <li>{@code kafka} - Kafka-based distributed logging
 *   <li>{@code chronicle} - Chronicle Queue for local high-performance logging
 *   <li>{@code gateway} - Outbound message routing
 *   <li>{@code zmq} - ZeroMQ socket utilities
 *   <li>{@code websocket} - WebSocket-based JSON-RPC transport
 * </ul>
 *
 * <h2>Key Abstractions</h2>
 *
 * <ul>
 *   <li>{@link WalWriter} - Interface for writing messages to a write-ahead log
 *   <li>{@link SourceLogReader} - Interface for reading messages from a source log
 *   <li>{@link WalType} - Enum distinguishing KAFKA vs CHRONICLE backends
 * </ul>
 */
package io.quasient.pal.core.transport;
