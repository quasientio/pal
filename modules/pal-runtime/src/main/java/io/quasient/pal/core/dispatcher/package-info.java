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
