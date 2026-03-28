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
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit tests for {@link InterceptPhase}.
 *
 * <p>Verifies byte conversion logic for serialization/deserialization.
 */
public class InterceptPhaseTest {

  /**
   * Tests that {@link InterceptPhase#toByte()} returns correct byte values.
   *
   * <p>BEFORE should map to byte 1, AFTER should map to byte 2.
   */
  @Test
  public void testToByte() {
    assertEquals((byte) 1, InterceptPhase.BEFORE.toByte());
    assertEquals((byte) 2, InterceptPhase.AFTER.toByte());
  }

  /**
   * Tests that {@link InterceptPhase#fromByte(byte)} correctly converts bytes to enum values.
   *
   * <p>Byte 1 should map to BEFORE, byte 2 should map to AFTER.
   */
  @Test
  public void testFromByte() {
    assertEquals(InterceptPhase.BEFORE, InterceptPhase.fromByte((byte) 1));
    assertEquals(InterceptPhase.AFTER, InterceptPhase.fromByte((byte) 2));
  }

  /**
   * Tests that {@link InterceptPhase#fromByte(byte)} throws IllegalArgumentException for invalid
   * bytes.
   *
   * <p>Values outside the range [1, 2] should be rejected.
   */
  @Test
  public void testFromByteInvalid() {
    try {
      InterceptPhase.fromByte((byte) 0);
      fail("Expected IllegalArgumentException for byte 0");
    } catch (IllegalArgumentException e) {
      assertEquals("Unknown intercept phase: 0", e.getMessage());
    }

    try {
      InterceptPhase.fromByte((byte) 3);
      fail("Expected IllegalArgumentException for byte 3");
    } catch (IllegalArgumentException e) {
      assertEquals("Unknown intercept phase: 3", e.getMessage());
    }

    try {
      InterceptPhase.fromByte((byte) 99);
      fail("Expected IllegalArgumentException for byte 99");
    } catch (IllegalArgumentException e) {
      assertEquals("Unknown intercept phase: 99", e.getMessage());
    }
  }

  /**
   * Tests round-trip conversion: enum → byte → enum.
   *
   * <p>Verifies that converting to byte and back yields the original value.
   */
  @Test
  public void testRoundTripConversion() {
    for (InterceptPhase phase : InterceptPhase.values()) {
      byte phaseAsByte = phase.toByte();
      InterceptPhase roundTripped = InterceptPhase.fromByte(phaseAsByte);
      assertEquals(phase, roundTripped);
    }
  }
}
