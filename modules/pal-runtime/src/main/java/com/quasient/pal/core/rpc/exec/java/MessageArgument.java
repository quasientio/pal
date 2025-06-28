/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

/**
 * Encapsulates a message argument for remote procedure calls.
 *
 * <p>This record carries an argument value along with an indicator whether the argument should be
 * passed by reference or by value. The {@code object} field holds the argument's value, and the
 * {@code byReference} flag determines the passing mechanism.
 *
 * @param object the argument value, which can be any object used in the RPC context
 * @param byReference true if the argument should be treated as passed by reference; false if passed
 *     by value
 */
public record MessageArgument(Object object, boolean byReference) {}
