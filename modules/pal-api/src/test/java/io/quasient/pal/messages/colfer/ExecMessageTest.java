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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test specifications for ExecMessage Colfer schema changes.
 *
 * <p>Tests the wire format serialization of the new declaredExceptions array field. These tests
 * serve as executable acceptance criteria for the implementation in issue #280.
 */
public class ExecMessageTest {

  /**
   * Test specification: shouldSerializeDeclaredExceptions
   *
   * <p>Verifies that the declaredExceptions array is correctly preserved through a
   * marshal/unmarshal round-trip.
   *
   * <p>Given: ExecMessage with declaredExceptions = ["java.io.IOException",
   * "java.sql.SQLException"] When: Marshaling to bytes and unmarshaling back Then: Array is
   * preserved after round-trip
   */
  @Test
  public void shouldSerializeDeclaredExceptions() throws Exception {
    // Given: ExecMessage with declaredExceptions
    ExecMessage message = new ExecMessage();
    message.setPeerUuid("test-peer-uuid");
    message.setMessageId("test-message-id");
    String[] exceptions = new String[] {"java.io.IOException", "java.sql.SQLException"};
    message.setDeclaredExceptions(exceptions);

    // When: Marshaling to bytes and unmarshaling back
    byte[] buf = new byte[message.marshalFit()];
    int length = message.marshal(buf, 0);

    ExecMessage deserialized = new ExecMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: Array is preserved after round-trip
    assertNotNull(deserialized.getDeclaredExceptions());
    assertArrayEquals(exceptions, deserialized.getDeclaredExceptions());
  }

  /**
   * Test specification: shouldHandleNullDeclaredExceptions
   *
   * <p>Verifies that null or unset declaredExceptions are handled correctly.
   *
   * <p>Given: ExecMessage without declaredExceptions set When: Getting declaredExceptions Then:
   * Returns null or empty array
   */
  @Test
  public void shouldHandleNullDeclaredExceptions() {
    // Given: ExecMessage without declaredExceptions set
    ExecMessage message = new ExecMessage();
    message.setPeerUuid("test-peer-uuid");
    message.setMessageId("test-message-id");

    // When: Getting declaredExceptions
    String[] result = message.getDeclaredExceptions();

    // Then: Returns null or empty array (depending on Colfer generation behavior)
    assertTrue(result == null || result.length == 0);
  }
}
