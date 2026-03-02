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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

/**
 * Unit tests for the {@code entryPoint} boolean field on {@code ExecMessage}.
 *
 * <p>Validates the default value, getter/setter behavior, and Colfer serialization round-trip
 * fidelity of the entry-point marker.
 *
 * @see ExecMessage
 */
public class ExecMessageEntryPointTest {

  /**
   * Verifies that a freshly constructed {@code ExecMessage} defaults {@code entryPoint} to {@code
   * false}.
   */
  @Test
  public void entryPointDefaultsFalse() {
    ExecMessage msg = new ExecMessage();
    assertThat(msg.getEntryPoint(), is(false));
  }

  /**
   * Verifies that an {@code ExecMessage} with {@code entryPoint = true} survives a Colfer
   * marshal/unmarshal round-trip with the value preserved.
   */
  @Test
  public void entryPointRoundTrip() {
    // Given: ExecMessage with entryPoint set to true
    ExecMessage msg = new ExecMessage();
    msg.setEntryPoint(true);

    // When: Serialized and deserialized via Colfer
    byte[] buf = new byte[msg.marshalFit()];
    int length = msg.marshal(buf, 0);

    ExecMessage deserialized = new ExecMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: entryPoint is preserved as true
    assertThat(deserialized.getEntryPoint(), is(true));
  }

  /**
   * Verifies that an {@code ExecMessage} with {@code entryPoint} left as default ({@code false})
   * survives a Colfer marshal/unmarshal round-trip with the value preserved as {@code false}.
   */
  @Test
  public void entryPointFalseRoundTrip() {
    // Given: ExecMessage with entryPoint left as default (false)
    ExecMessage msg = new ExecMessage();

    // When: Serialized and deserialized via Colfer
    byte[] buf = new byte[msg.marshalFit()];
    int length = msg.marshal(buf, 0);

    ExecMessage deserialized = new ExecMessage();
    deserialized.unmarshal(buf, 0, length);

    // Then: entryPoint is preserved as false
    assertThat(deserialized.getEntryPoint(), is(false));
  }
}
