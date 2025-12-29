/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;
import org.junit.Test;

public class UuidUtilsTest {

  @Test
  public void toBytesFromBytes() {
    UUID uuid = UUID.randomUUID();
    // 8 + 8 bytes
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid);

    assertThat(uuidAsBytes.length, is(16));
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }

  @Test
  public void toBytes() {
    UUID uuid = UUID.randomUUID();
    byte[] uuidAsBytes = UuidUtils.toBytes(uuid.toString());
    assertThat(UuidUtils.fromBytes(uuidAsBytes), is(uuid));
  }
}
