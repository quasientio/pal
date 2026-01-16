/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
/**
 * WebSocket-based JSON-RPC transport for browser and HTTP clients.
 *
 * <p>Provides an alternative RPC transport using WebSocket and JSON-RPC 2.0 protocol, enabling
 * integration with web applications and tools that cannot use ZeroMQ directly.
 *
 * <ul>
 *   <li>{@link JsonRpcWebSocketServer} - WebSocket server handling JSON-RPC requests
 *   <li>{@link JsonRpcRequestServer} - Request processing and response generation
 * </ul>
 */
package io.quasient.pal.core.transport.websocket;
