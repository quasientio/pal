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
 * @see com.quasient.pal.core.service.Main Entry point for starting a peer
 * @see com.quasient.pal.core.execution.java.BaseExecMessageDispatcher Core dispatch logic
 */
package com.quasient.pal.core;
