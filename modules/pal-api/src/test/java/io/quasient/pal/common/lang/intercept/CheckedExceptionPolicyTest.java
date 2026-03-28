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
 * Unit test specifications for {@link CheckedExceptionPolicy}.
 *
 * <p>Verifies that the enum contains all expected policy values (WRAP, REJECT, ALLOW_ALL) and
 * supports string serialization for configuration and persistence.
 */
public class CheckedExceptionPolicyTest {

  /**
   * Tests that CheckedExceptionPolicy enum contains all expected policy values.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> CheckedExceptionPolicy enum
   *   <li><b>When:</b> Listing all values via values()
   *   <li><b>Then:</b> The array contains exactly 3 values
   *   <li><b>And:</b> The values are WRAP, REJECT, ALLOW_ALL
   *   <li><b>And:</b> WRAP policy wraps checked exceptions in runtime exceptions
   *   <li><b>And:</b> REJECT policy rejects callbacks that throw non-declared checked exceptions
   *   <li><b>And:</b> ALLOW_ALL policy allows all checked exceptions
   * </ul>
   */
  @Test
  public void shouldHaveAllExpectedPolicyValues() {
    // Given: CheckedExceptionPolicy enum
    CheckedExceptionPolicy[] values = CheckedExceptionPolicy.values();

    // When: Listing all values
    // Then: Contains exactly 3 values
    assertNotNull(values);
    assertEquals(3, values.length);

    // And: Values are WRAP, REJECT, ALLOW_ALL
    List<CheckedExceptionPolicy> valueList = Arrays.asList(values);
    assertTrue("Should contain WRAP", valueList.contains(CheckedExceptionPolicy.WRAP));
    assertTrue("Should contain REJECT", valueList.contains(CheckedExceptionPolicy.REJECT));
    assertTrue("Should contain ALLOW_ALL", valueList.contains(CheckedExceptionPolicy.ALLOW_ALL));

    // And: Can access each value directly
    assertNotNull(CheckedExceptionPolicy.WRAP);
    assertNotNull(CheckedExceptionPolicy.REJECT);
    assertNotNull(CheckedExceptionPolicy.ALLOW_ALL);
  }

  /**
   * Tests that CheckedExceptionPolicy can be serialized to and from string representation.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> Each CheckedExceptionPolicy value (WRAP, REJECT, ALLOW_ALL)
   *   <li><b>When:</b> Converting to string using toString() or name()
   *   <li><b>And:</b> Converting back using valueOf()
   *   <li><b>Then:</b> Round-trip conversion succeeds for all values
   *   <li><b>And:</b> The original value equals the round-tripped value
   *   <li><b>And:</b> String representation is human-readable (e.g., "WRAP", "REJECT", "ALLOW_ALL")
   * </ul>
   */
  @Test
  public void shouldSerializeToAndFromString() {
    // Given: Each CheckedExceptionPolicy value
    for (CheckedExceptionPolicy policy : CheckedExceptionPolicy.values()) {

      // When: Converting to string
      String policyString = policy.name();

      // And: Converting back using valueOf()
      CheckedExceptionPolicy roundTripped = CheckedExceptionPolicy.valueOf(policyString);

      // Then: Round-trip succeeds
      assertNotNull(roundTripped);
      assertEquals(policy, roundTripped);

      // And: String representation is human-readable
      assertTrue(
          "String should be uppercase constant",
          policyString.equals(policyString.toUpperCase(Locale.ROOT)));
    }

    // Verify specific string representations
    assertEquals("WRAP", CheckedExceptionPolicy.WRAP.name());
    assertEquals("REJECT", CheckedExceptionPolicy.REJECT.name());
    assertEquals("ALLOW_ALL", CheckedExceptionPolicy.ALLOW_ALL.name());
  }
}
