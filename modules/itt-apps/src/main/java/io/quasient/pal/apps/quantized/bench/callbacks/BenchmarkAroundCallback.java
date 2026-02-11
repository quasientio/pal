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
 * Proceed-only AROUND intercept callback handler for benchmarking.
 *
 * <p>This class provides a minimal AROUND callback that calls {@link InterceptContext#proceed()}
 * immediately, measuring the overhead of the AROUND intercept chain without any application logic.
 * It is used as the callback target for AROUND intercept benchmarks in {@code DispatchBenchmark}.
 *
 * <p>This class is compiled with AspectJ weaving (via the {@code itt-apps} module's
 * aspectj-maven-plugin configuration) so that it can be resolved by {@code CallbackResolver} at
 * runtime.
 *
 * @see io.quasient.pal.common.lang.intercept.InterceptContext
 * @see io.quasient.pal.common.lang.intercept.InterceptCallbackResponse
 */
@SuppressWarnings("unused")
public class BenchmarkAroundCallback {

  /** Default no-arg constructor. */
  public BenchmarkAroundCallback() {}

  /**
   * Proceed-only AROUND callback that delegates to the intercepted method immediately.
   *
   * <p>This method measures the overhead of the AROUND intercept chain. It calls {@link
   * InterceptContext#proceed()} to execute the intercepted method and returns a default {@link
   * InterceptCallbackResponse} without modifying arguments or return values.
   *
   * @param ctx the intercept context used to proceed with the intercepted method
   * @return a default callback response with no modifications
   * @throws Exception if the intercepted method throws an exception via {@code proceed()}
   */
  public static InterceptCallbackResponse onAround(InterceptContext ctx) throws Exception {
    ctx.proceed();
    return new InterceptCallbackResponse();
  }
}
