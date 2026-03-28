/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.foobar.apps.quantized.bench.callbacks;

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
