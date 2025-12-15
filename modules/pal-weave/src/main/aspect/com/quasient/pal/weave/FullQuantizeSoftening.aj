/**
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.weave;

import com.quasient.pal.common.runtime.DispatchForwarder;

/**
 * Companion aspect that softens checked exceptions from {@link DispatchForwarder} calls.
 *
 * <p>AspectJ's "declare soft" mechanism wraps checked exceptions in {@link
 * org.aspectj.lang.SoftException}, allowing the {@link FullQuantizeAspect} advice to propagate
 * exceptions without requiring explicit throws clauses on every intercepted method.
 *
 * <p>This aspect must be woven together with {@link FullQuantizeAspect} for proper operation.
 */
public aspect FullQuantizeSoftening {
  declare soft: Throwable : call (Object DispatchForwarder.constructor(..));
  declare soft: Throwable : call (void DispatchForwarder.voidInstanceMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidInstanceMethod(..));
  declare soft: Throwable : call (void DispatchForwarder.voidClassMethod(..));
  declare soft: Throwable : call (Object DispatchForwarder.nonVoidClassMethod(..));
}