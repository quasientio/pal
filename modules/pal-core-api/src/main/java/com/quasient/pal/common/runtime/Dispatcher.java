/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.runtime;

import com.quasient.pal.common.weave.Proceed;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Represents a dispatcher responsible for executing operations from a quantized context.
 *
 * @see Context
 */
public interface Dispatcher {

  /**
   * Entry point for the hot-path, i.e. execution of quantized operations (constructor/method calls
   * and field ops)
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the dispatch operation
   * @throws Throwable if an error occurs during the dispatch process
   */
  <T> T dispatch(ProceedingJoinPoint pjp, Proceed<T> proceed) throws Throwable;
}
