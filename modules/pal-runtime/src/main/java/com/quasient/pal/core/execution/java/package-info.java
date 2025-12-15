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
 * Core execution dispatchers that convert Java operations into PAL messages.
 *
 * <p>This package implements the "operations as messages" abstraction at the heart of PAL. When
 * AspectJ-woven code executes a method call, constructor invocation, or field access, the
 * corresponding dispatcher in this package:
 *
 * <ol>
 *   <li>Creates an ExecMessage representing the operation
 *   <li>Writes to the write-ahead log (WAL) if configured
 *   <li>Publishes to subscribers if configured
 *   <li>Executes intercept callbacks (BEFORE, AROUND, AFTER phases)
 *   <li>Invokes the actual operation (or skips if an AROUND intercept says to)
 *   <li>Handles return values and exceptions
 * </ol>
 *
 * <h2>Dispatcher Hierarchy</h2>
 *
 * <pre>
 * {@link AbstractDispatcher} (DI configuration)
 *   └── {@link BaseExecMessageDispatcher} (complete lifecycle orchestration)
 *         ├── {@link MethodDispatcher} (abstract base for methods)
 *         │     ├── {@link InstanceMethodDispatcher} (obj.method())
 *         │     └── {@link ClassMethodDispatcher} (Class.staticMethod())
 *         ├── {@link ConstructorDispatcher} (new Class())
 *         └── {@link FieldOpDispatcher} (abstract base for fields)
 *               ├── {@link GetFieldDispatcher} (read operations)
 *               │     ├── {@link GetInstanceVariableDispatcher} (obj.field)
 *               │     └── {@link GetClassVariableDispatcher} (Class.staticField)
 *               └── {@link SetFieldDispatcher} (write operations)
 *                     ├── {@link SetInstanceVariableDispatcher} (obj.field = value)
 *                     └── {@link SetClassVariableDispatcher} (Class.staticField = value)
 * </pre>
 *
 * <h2>Two Execution Paths</h2>
 *
 * <p><b>Hot Path (local operations):</b> {@link BaseExecMessageDispatcher#dispatch} is called from
 * AspectJ advice when woven application code executes an operation locally.
 *
 * <p><b>Incoming RPC:</b> {@link BaseExecMessageDispatcher#dispatchIncoming} is called when a
 * message arrives via RPC or from a log, causing the operation to execute on this peer.
 *
 * @see BaseExecMessageDispatcher The central class orchestrating the complete message lifecycle
 * @see com.quasient.pal.core.intercept Interception mechanism integrated into dispatchers
 */
package com.quasient.pal.core.execution.java;
