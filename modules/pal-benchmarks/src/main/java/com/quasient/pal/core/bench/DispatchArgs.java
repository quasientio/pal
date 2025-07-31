/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.DispatchForwarder;

/**
 * Encapsulates the arguments for calls to {@link DispatchForwarder}
 *
 * @param ctx the execution context
 * @param sender the object initiating the call
 * @param target the target object on which the method is invoked; null for static method calls
 * @param args the arguments for the method
 */
public record DispatchArgs(Context ctx, Object sender, Object target, Object[] args) {
}
