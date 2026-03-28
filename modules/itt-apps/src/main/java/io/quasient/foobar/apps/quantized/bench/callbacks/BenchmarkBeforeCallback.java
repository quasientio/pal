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
