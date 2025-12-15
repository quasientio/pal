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
 * Runtime execution context and message dispatching infrastructure.
 *
 * <p>This package provides the core abstractions for capturing AspectJ join points and dispatching
 * the resulting messages. The {@link Dispatcher} interface is the entry point for the "hot path" -
 * the code that runs for every quantized operation.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link Context} - Execution context extracted from AspectJ join points
 *   <li>{@link Dispatcher} - Interface for message dispatch implementations
 *   <li>{@link ExecPhase} - Lifecycle phases of an operation (DISPATCH, PROCEED, RETURN)
 * </ul>
 */
package com.quasient.pal.common.runtime;
