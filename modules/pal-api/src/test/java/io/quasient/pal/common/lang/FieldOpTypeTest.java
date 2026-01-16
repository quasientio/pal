/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FieldOpTypeTest {

  @Test
  public void toByte_and_fromByte_roundTrip() {
    assertThat(FieldOpType.fromByte(FieldOpType.GET.toByte()), is(FieldOpType.GET));
    assertThat(FieldOpType.fromByte(FieldOpType.PUT.toByte()), is(FieldOpType.PUT));
  }

  @Test
  public void fromByte_invalid_throws() {
    assertThrows(IllegalArgumentException.class, () -> FieldOpType.fromByte((byte) 0));
    assertThrows(IllegalArgumentException.class, () -> FieldOpType.fromByte((byte) 3));
  }
}
