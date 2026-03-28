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
