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
 * PAL runtime core - the message-passing engine that converts operations into messages.
 *
 * <p>This is the heart of PAL. When AspectJ-woven application code executes, the runtime captures
 * each operation (method call, constructor invocation, field access) and:
 *
 * <ol>
 *   <li>Converts it to an ExecMessage
 *   <li>Writes to write-ahead log (WAL) if configured
 *   <li>Publishes to subscribers if configured
 *   <li>Executes intercept callbacks
 *   <li>Invokes the actual operation
 *   <li>Captures and routes the result
 * </ol>
 *
 * <h2>Package Structure</h2>
 *
 * <ul>
 *   <li>{@code service} - Peer lifecycle and dependency injection
 *   <li>{@code execution.java} - Operation dispatchers (methods, constructors, fields)
 *   <li>{@code dispatcher} - Incoming message routing
 *   <li>{@code intercept} - Dynamic interception system
 *   <li>{@code transport} - Log and network I/O
 *   <li>{@code runtime} - Object and session state management
 *   <li>{@code internal} - Internal utilities
 *   <li>{@code annotations} - Annotation processing
 * </ul>
 *
 * @see io.quasient.pal.core.service.Main Entry point for starting a peer
 * @see io.quasient.pal.core.execution.java.BaseExecMessageDispatcher Core dispatch logic
 */
package io.quasient.pal.core;
