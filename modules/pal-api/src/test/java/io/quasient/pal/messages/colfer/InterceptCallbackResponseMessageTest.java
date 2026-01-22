/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.colfer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test specifications for InterceptCallbackResponseMessage Colfer schema changes.
 *
 * <p>Tests the wire format serialization of the new isApiMisuseError flag field. These tests serve
 * as executable acceptance criteria for the implementation in issue #280.
 */
public class InterceptCallbackResponseMessageTest {

  /**
   * Test specification: shouldSerializeIsApiMisuseErrorFlag
   *
   * <p>Verifies that the isApiMisuseError flag is correctly preserved through a marshal/unmarshal
   * round-trip.
   *
   * <p>Given: Response with isApiMisuseError = true When: Marshaling to bytes and unmarshaling back
   * Then: Flag is preserved after round-trip
   */
  @Test
  public void shouldSerializeIsApiMisuseErrorFlag() throws Exception {
    // Given: Response with isApiMisuseError = true
    InterceptCallbackResponseMessage response = new InterceptCallbackResponseMessage();
    response.setCallbackId("test-callback-id");
    response.setIsApiMisuseError(true);

    // When: Marshaling to bytes and unmarshaling back
    byte[] buf = new byte[response.marshalFit()];
    int length = response.marshal(buf, 0);

    InterceptCallbackResponseMessage deserialized = new InterceptCallbackResponseMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: Flag is preserved after round-trip
    assertTrue(deserialized.getIsApiMisuseError());
  }

  /**
   * Test specification: shouldDefaultIsApiMisuseErrorToFalse
   *
   * <p>Verifies that the isApiMisuseError flag defaults to false when not explicitly set.
   *
   * <p>Given: Default response (no isApiMisuseError set) When: Getting isApiMisuseError Then:
   * Returns false
   */
  @Test
  public void shouldDefaultIsApiMisuseErrorToFalse() {
    // Given: Default response
    InterceptCallbackResponseMessage response = new InterceptCallbackResponseMessage();
    response.setCallbackId("test-callback-id");

    // When: Getting isApiMisuseError
    boolean result = response.getIsApiMisuseError();

    // Then: Returns false
    assertFalse(result);
  }
}
