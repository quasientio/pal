/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

/**
 * Encapsulates an exception encountered during an invocation.
 *
 * <p>This record wraps an {@link Exception} thrown during a reflective or remote invocation,
 * providing a standardized way to relay invocation errors within the PAL runtime.
 *
 * @param exception the exception instance encountered during the operation, representing the
 *     underlying cause of the failure.
 */
record InvocationExceptionWrapper(Exception exception) {}
