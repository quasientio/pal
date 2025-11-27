/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class JsonUtilTest {

  static class Bean {
    public String a = "x";
  }

  @Test
  public void toJson_and_fromJson_roundTrip() {
    Bean b = new Bean();
    String json = JsonUtil.toJson(b);
    assertThat(json, containsString("\"a\":\"x\""));
    Bean parsed = JsonUtil.fromJson(json, Bean.class);
    assertEquals("x", parsed.a);
  }

  @Test
  public void fromJson_throwsOnInvalid() {
    assertThrows(RuntimeException.class, () -> JsonUtil.fromJson("{oops:", Bean.class));
  }
}
