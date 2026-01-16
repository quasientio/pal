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
 * Message routing and invocation infrastructure for incoming messages.
 *
 * <p>This package handles messages arriving via RPC (socket) or from logs (WAL replay). The routing
 * layer dispatches messages to appropriate handlers in {@link io.quasient.pal.core.execution.java}.
 *
 * <h2>Message Flow</h2>
 *
 * <pre>
 * Incoming Message
 *       │
 *       ▼
 * {@link IncomingMessageDispatcher}  ─────► Routes by MessageType
 *       │
 *       ├── EXEC_* messages → execution/java dispatchers
 *       ├── CONTROL messages → {@link ControlMessageDispatcher}
 *       ├── META messages → {@link MetaMessageDispatcher}
 *       └── INTERCEPT callbacks → intercept package
 * </pre>
 *
 * <h2>Two Invocation Paths</h2>
 *
 * <p><b>Socket RPC:</b> {@link SocketRpcInvoker} handles synchronous request-response via ZeroMQ.
 * Each request gets a dedicated thread from {@link SocketRpcExecutor}.
 *
 * <p><b>Log Replay:</b> {@link LogRpcInvoker} replays messages from write-ahead logs (Kafka or
 * Chronicle). Messages are processed sequentially by {@link LogRpcExecutor}.
 *
 * @see IncomingMessageDispatcher Central router for all incoming messages
 * @see io.quasient.pal.core.execution.java Actual operation execution
 */
package io.quasient.pal.core.dispatcher;
