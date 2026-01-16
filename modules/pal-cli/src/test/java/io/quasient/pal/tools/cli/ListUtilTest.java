/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

public class ListUtilTest {

  @Test
  public void optionallyTrim_withTrimmingEnabled() throws Exception {
    // Create List instance with trimming enabled (default)
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);
    assertThat((String) trim.invoke(listInstance, "abcdef", 4), is("ab.."));
    assertThat((String) trim.invoke(listInstance, "abc", 4), is("abc"));
  }

  @Test
  public void optionallyTrim_withNoTrimmingEnabled() throws Exception {
    // Create List instance with --no-trim flag enabled
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, true);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);
    // With no-trim flag, strings should not be trimmed
    assertThat((String) trim.invoke(listInstance, "abcdef", 4), is("abcdef"));
    assertThat((String) trim.invoke(listInstance, "abc", 4), is("abc"));
  }

  @Test
  public void date_formatters() throws Exception {
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
