/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.internal.concurrent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Tests for {@link AdaptiveSpinParkWaitStrategy}.
 *
 * <p>This test class validates the adaptive spin-park wait strategy used for MPSC consumer threads
 * that need low latency at sustained load while yielding the CPU during idle periods.
 */
public class AdaptiveSpinParkWaitStrategyTest {

  /**
   * Test: Verify that toString returns a descriptive string.
   *
   * <p>This test validates that the toString method returns a meaningful description containing the
   * strategy's configuration parameters (spinIterations and parkNanos).
   *
   * @see AdaptiveSpinParkWaitStrategy#toString()
   */
  @Test
  public void testToString_returnsDescriptiveString() {
    // Given: An AdaptiveSpinParkWaitStrategy instance with known parameters
    int spinIterations = 100;
    long parkNanos = 50_000L;
    AdaptiveSpinParkWaitStrategy strategy =
        new AdaptiveSpinParkWaitStrategy(spinIterations, parkNanos);

    // When: toString() is called
    String result = strategy.toString();

    // Then: Returns a string containing the class name and parameter values
    assertThat(result, containsString("AdaptiveSpinParkWaitStrategy"));
    assertThat(result, containsString("spinIterations=" + spinIterations));
    assertThat(result, containsString("parkNanos=" + parkNanos));
  }

  /**
   * Test: Verify that constructor validates parameters appropriately.
   *
   * <p>This test validates that the constructor throws IllegalArgumentException when invalid
   * parameters are provided (non-positive spinIterations or parkNanos).
   *
   * @see AdaptiveSpinParkWaitStrategy#AdaptiveSpinParkWaitStrategy(int, long)
   */
  @Test
  public void testConstructor_invalidParams_handlesValidation() {
    // Test with spinIterations = 0, expect IllegalArgumentException
    try {
      new AdaptiveSpinParkWaitStrategy(0, 100_000L);
      fail("Expected IllegalArgumentException for spinIterations = 0");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("spinIterations"));
    }

    // Test with spinIterations = -1, expect IllegalArgumentException
    try {
      new AdaptiveSpinParkWaitStrategy(-1, 100_000L);
      fail("Expected IllegalArgumentException for spinIterations = -1");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("spinIterations"));
    }

    // Test with parkNanos = 0, expect IllegalArgumentException
    try {
      new AdaptiveSpinParkWaitStrategy(50, 0L);
      fail("Expected IllegalArgumentException for parkNanos = 0");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("parkNanos"));
    }

    // Test with parkNanos = -1, expect IllegalArgumentException
    try {
      new AdaptiveSpinParkWaitStrategy(50, -1L);
      fail("Expected IllegalArgumentException for parkNanos = -1");
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), containsString("parkNanos"));
    }
  }
}
