/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
