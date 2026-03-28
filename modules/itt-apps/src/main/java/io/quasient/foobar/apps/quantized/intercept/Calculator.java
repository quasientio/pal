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
package io.quasient.foobar.apps.quantized.intercept;

/**
 * Simple calculator application for testing instance method intercepts.
 *
 * <p>Provides wrapper methods that internally call target methods to satisfy call-site weaving
 * requirements for intercept testing.
 */
public class Calculator {

  /**
   * Multiplies the given value by a multiplier.
   *
   * @param multiplier the multiplier
   * @return the result
   */
  public int multiplyBy(Integer multiplier) {
    return multiplier * 10; // Simple implementation
  }

  /**
   * Wrapper method that calls multiplyBy internally.
   *
   * <p>CRITICAL: Direct RPC to multiplyBy bypasses call-site weaving. Tests must call this wrapper
   * instead.
   *
   * @param multiplier the multiplier
   * @return the result from multiplyBy
   */
  public int callMultiplyBy(Integer multiplier) {
    return multiplyBy(multiplier);
  }
}
