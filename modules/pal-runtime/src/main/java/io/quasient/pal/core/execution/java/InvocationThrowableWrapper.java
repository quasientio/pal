/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

/**
 * Encapsulates a throwable encountered during an invocation.
 *
 * <p>This record wraps a {@link Throwable} encountered during a reflective or remote invocation.
 *
 * @param throwable the throwable instance encountered during the operation, representing the
 *     underlying cause of the failure.
 */
record InvocationThrowableWrapper(Throwable throwable) {}
