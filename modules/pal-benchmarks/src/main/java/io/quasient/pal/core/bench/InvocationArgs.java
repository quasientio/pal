/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Encapsulates the arguments for calls being benchmarked.
 *
 * @param target the target object on which the method is invoked; null for static method calls
 * @param args the arguments for the method
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Benchmark data class - args array shared for performance")
@SuppressWarnings("ArrayRecordComponent")
public record InvocationArgs(Object target, Object[] args) {}
