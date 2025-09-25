/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

public class ListUtilTest {

  @Test
  public void trimTo_and_date_formatters() throws Exception {
    Method trim = List.class.getDeclaredMethod("trimTo", String.class, int.class);
    trim.setAccessible(true);
    assertThat((String) trim.invoke(null, "abcdef", 4), is("ab.."));
    assertThat((String) trim.invoke(null, "abc", 4), is("abc"));

    Method fmtDate = List.class.getDeclaredMethod("getFormattedDate", OffsetDateTime.class);
    fmtDate.setAccessible(true);
    String s =
        (String) fmtDate.invoke(null, OffsetDateTime.of(2025, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC));
    assertThat(s, containsString("Jan"));

    Method fmtUptime = List.class.getDeclaredMethod("getFormattedUptime", OffsetDateTime.class);
    fmtUptime.setAccessible(true);
    String up = (String) fmtUptime.invoke(null, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    assertThat(up, containsString(":"));
  }
}
