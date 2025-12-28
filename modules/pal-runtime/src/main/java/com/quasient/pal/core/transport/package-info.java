/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
package com.quasient.pal.core.transport;
