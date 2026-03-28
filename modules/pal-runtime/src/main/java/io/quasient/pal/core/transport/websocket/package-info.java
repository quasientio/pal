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
