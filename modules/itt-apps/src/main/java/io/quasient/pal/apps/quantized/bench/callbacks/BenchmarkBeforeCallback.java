/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.bench.callbacks;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;

/**
 * No-op BEFORE intercept callback handler for benchmarking.
 *
 * <p>This class provides a minimal callback that returns immediately, measuring the pure overhead
 * of intercept dispatch without any application logic. It is used as the callback target for BEFORE
 * intercept benchmarks in {@code DispatchBenchmark}.
 *
 * <p>This class is compiled with AspectJ weaving (via the {@code itt-apps} module's
 * aspectj-maven-plugin configuration) so that it can be resolved by {@code CallbackResolver} at
 * runtime.
 *
 * @see io.quasient.pal.common.lang.intercept.InterceptContext
 * @see io.quasient.pal.common.lang.intercept.InterceptCallbackResponse
 */
@SuppressWarnings("unused")
public class BenchmarkBeforeCallback {

  /** Default no-arg constructor. */
  public BenchmarkBeforeCallback() {}

  /**
   * No-op BEFORE callback that returns immediately.
   *
   * <p>This method measures the pure dispatch overhead of a BEFORE intercept callback. It performs
   * no work on the {@link InterceptContext} and returns a default {@link
   * InterceptCallbackResponse}, allowing the intercepted method to proceed normally.
   *
   * @param ctx the intercept context (unused)
   * @return a default callback response allowing normal execution
   */
  public static InterceptCallbackResponse onBefore(InterceptContext ctx) {
    return new InterceptCallbackResponse();
  }
}
