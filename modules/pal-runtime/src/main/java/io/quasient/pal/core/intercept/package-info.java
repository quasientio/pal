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
 * Runtime interception infrastructure that enables dynamic behavior modification.
 *
 * <p>This package implements the interception mechanism that allows external code to observe and
 * modify operations at runtime. Intercepts are registered via etcd and matched against operations
 * by class/method patterns.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link InterceptMatcher} - Manages intercept registration and matching against operations
 *   <li>{@link InterceptChecker} - Fast path checking for intercepts without creating ExecMessage
 *   <li>{@link InterceptCallbackDispatcher} - Sends callbacks to remote intercepting peers
 *   <li>{@link LocalInterceptCallbackDispatcher} - Handles callbacks to local (same-JVM) handlers
 *   <li>{@link IncomingInterceptCallbackDispatcher} - Receives callbacks from intercepted peers
 * </ul>
 *
 * <h2>Intercept Flow</h2>
 *
 * <ol>
 *   <li>Peer registers intercept via etcd ({@link InterceptMatcher#registerInterceptRequest})
 *   <li>When operation executes, {@link InterceptChecker} checks for matching intercepts
 *   <li>If matches found, callback dispatchers invoke the intercepting peer's handler
 *   <li>Handler can inspect/modify arguments, skip execution, or modify return values
 * </ol>
 *
 * @see io.quasient.pal.common.lang.intercept.InterceptType
 * @see io.quasient.pal.common.lang.intercept.InterceptCallback
 */
package io.quasient.pal.core.intercept;
