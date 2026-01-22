/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

/**
 * Unit test specifications for {@link ExceptionPropagationPolicy}.
 *
 * <p>Verifies that the enum contains all expected policy values and supports string serialization
 * for configuration and persistence.
 */
public class ExceptionPropagationPolicyTest {

  /**
   * Tests that ExceptionPropagationPolicy enum contains all expected policy values.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> ExceptionPropagationPolicy enum
   *   <li><b>When:</b> Listing all values via values()
   *   <li><b>Then:</b> The array contains exactly 4 values
   *   <li><b>And:</b> The values are PROPAGATE_ALL, PROPAGATE_EXPLICIT_ONLY, SWALLOW_ALL,
   *       PROPAGATE_CONTROLLED_ONLY
   *   <li><b>And:</b> PROPAGATE_ALL propagates all exceptions from callbacks
   *   <li><b>And:</b> PROPAGATE_EXPLICIT_ONLY propagates only explicitly declared exceptions
   *   <li><b>And:</b> SWALLOW_ALL swallows all exceptions from callbacks
   *   <li><b>And:</b> PROPAGATE_CONTROLLED_ONLY propagates exceptions that are explicitly
   *       controlled
   * </ul>
   */
  @Test
  public void shouldHaveAllExpectedPolicyValues() {
    // Given: ExceptionPropagationPolicy enum
    ExceptionPropagationPolicy[] values = ExceptionPropagationPolicy.values();

    // When: Listing all values
    // Then: Contains exactly 4 values
    assertNotNull(values);
    assertEquals(4, values.length);

    // And: Values are PROPAGATE_ALL, PROPAGATE_EXPLICIT_ONLY, SWALLOW_ALL,
    // PROPAGATE_CONTROLLED_ONLY
    List<ExceptionPropagationPolicy> valueList = Arrays.asList(values);
    assertTrue(
        "Should contain PROPAGATE_ALL",
        valueList.contains(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertTrue(
        "Should contain PROPAGATE_EXPLICIT_ONLY",
        valueList.contains(ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY));
    assertTrue(
        "Should contain SWALLOW_ALL", valueList.contains(ExceptionPropagationPolicy.SWALLOW_ALL));
    assertTrue(
        "Should contain PROPAGATE_CONTROLLED_ONLY",
        valueList.contains(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));

    // And: Can access each value directly
    assertNotNull(ExceptionPropagationPolicy.PROPAGATE_ALL);
    assertNotNull(ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY);
    assertNotNull(ExceptionPropagationPolicy.SWALLOW_ALL);
    assertNotNull(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY);
  }

  /**
   * Tests that ExceptionPropagationPolicy can be serialized to and from string representation.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> Each ExceptionPropagationPolicy value
   *   <li><b>When:</b> Converting to string using toString() or name()
   *   <li><b>And:</b> Converting back using valueOf()
   *   <li><b>Then:</b> Round-trip conversion succeeds for all values
   *   <li><b>And:</b> The original value equals the round-tripped value
   *   <li><b>And:</b> String representation matches expected names: "PROPAGATE_ALL",
   *       "PROPAGATE_EXPLICIT_ONLY", "SWALLOW_ALL", "PROPAGATE_CONTROLLED_ONLY"
   * </ul>
   */
  @Test
  public void shouldSerializeToAndFromString() {
    // Given: Each ExceptionPropagationPolicy value
    for (ExceptionPropagationPolicy policy : ExceptionPropagationPolicy.values()) {

      // When: Converting to string
      String policyString = policy.name();

      // And: Converting back using valueOf()
      ExceptionPropagationPolicy roundTripped = ExceptionPropagationPolicy.valueOf(policyString);

      // Then: Round-trip succeeds
      assertNotNull(roundTripped);
      assertEquals(policy, roundTripped);

      // And: String representation is human-readable
      assertTrue(
          "String should be uppercase constant",
          policyString.equals(policyString.toUpperCase(Locale.ROOT)));
    }

    // Verify specific string representations
    assertEquals("PROPAGATE_ALL", ExceptionPropagationPolicy.PROPAGATE_ALL.name());
    assertEquals(
        "PROPAGATE_EXPLICIT_ONLY", ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY.name());
    assertEquals("SWALLOW_ALL", ExceptionPropagationPolicy.SWALLOW_ALL.name());
    assertEquals(
        "PROPAGATE_CONTROLLED_ONLY", ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY.name());
  }
}
