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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

/**
 * Edge case tests for List utility methods.
 *
 * <p>Tests string trimming, formatting, and edge cases including very long strings, special
 * characters, empty strings, large numeric values, and boundary conditions.
 */
public class ListUtilEdgeCaseTest {

  /**
   * Tests trimming of very long strings that significantly exceed the trim length.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_veryLongString() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // String much longer than trim length
    String veryLong = "a".repeat(1000);
    String result = (String) trim.invoke(listInstance, veryLong, 10);

    // Should be trimmed to length 10 with ".." suffix
    assertThat(result.length(), is(10));
    assertThat(result, endsWith(".."));
  }

  /**
   * Tests trimming with empty string.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_emptyString() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    String result = (String) trim.invoke(listInstance, "", 10);
    assertThat(result, is(""));
  }

  /**
   * Tests trimming with special characters (Unicode, newlines, tabs).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_specialCharacters() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // String with Unicode characters
    String unicode = "日本語テキスト";
    String result = (String) trim.invoke(listInstance, unicode, 5);
    assertThat(result.length(), is(5));

    // String with newlines and tabs
    String withWhitespace = "hello\n\tworld";
    String result2 = (String) trim.invoke(listInstance, withWhitespace, 8);
    // Should handle whitespace characters
    assertThat(result2.length(), is(8));
  }

  /**
   * Tests trimming at exact boundary (string length equals trim length).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_exactBoundary() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // String exactly at trim length should not be trimmed
    String exact = "exactly10!";
    String result = (String) trim.invoke(listInstance, exact, 10);
    assertThat(result, is(exact));
    assertThat(result, not(endsWith("..")));
  }

  /**
   * Tests trimming with very small trim length.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_verySmallTrimLength() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // Trim to very small length
    String result = (String) trim.invoke(listInstance, "hello world", 2);
    assertThat(result.length(), is(2));
    // With length 2, may just be ".." or handle specially
  }

  /**
   * Tests trimming with zero-length trim (edge case).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_zeroLength() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // Edge case: trim length 0
    String result = (String) trim.invoke(listInstance, "anything", 0);
    // Should handle gracefully (may return empty or minimal string)
    assertThat(result.length(), is(0));
  }

  /**
   * Tests date formatting with extreme dates.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void dateFormatter_extremeDates() throws Exception {
    Method fmtDate = List.class.getDeclaredMethod("getFormattedDate", OffsetDateTime.class);
    fmtDate.setAccessible(true);

    // Very old date
    String old =
        (String) fmtDate.invoke(null, OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    assertThat(old, containsString("Jan"));

    // Future date
    String future =
        (String)
            fmtDate.invoke(null, OffsetDateTime.of(2099, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC));
    assertThat(future, containsString("Dec"));
  }

  /**
   * Tests uptime formatting with various durations.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void uptimeFormatter_variousDurations() throws Exception {
    Method fmtUptime = List.class.getDeclaredMethod("getFormattedUptime", OffsetDateTime.class);
    fmtUptime.setAccessible(true);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // Very short uptime (seconds)
    String seconds = (String) fmtUptime.invoke(null, now.minusSeconds(30));
    assertThat(seconds, containsString(":")); // Should still format as time

    // Exactly 1 hour
    String oneHour = (String) fmtUptime.invoke(null, now.minusHours(1));
    assertThat(oneHour, containsString(":"));

    // Multiple days
    fmtUptime.invoke(null, now.minusDays(5).minusHours(3));
    // Should handle multi-day uptime without throwing

    // Very long uptime
    fmtUptime.invoke(null, now.minusDays(365));
    // Should handle year+ uptime without throwing
  }

  /**
   * Tests date formatting with different timezones.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void dateFormatter_differentTimezones() throws Exception {
    Method fmtDate = List.class.getDeclaredMethod("getFormattedDate", OffsetDateTime.class);
    fmtDate.setAccessible(true);

    // UTC
    String utc =
        (String) fmtDate.invoke(null, OffsetDateTime.of(2025, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC));
    assertThat(utc, containsString("Jun"));

    // Different offset
    fmtDate.invoke(null, OffsetDateTime.of(2025, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(5)));
    // Should handle timezone offsets without throwing
  }

  /**
   * Tests trimming preserves string integrity for exact length strings.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void optionallyTrim_preservesIntegrityAtBoundary() throws Exception {
    List listInstance = new List();
    Field noTrimmingField = List.class.getDeclaredField("noTrimming");
    noTrimmingField.setAccessible(true);
    noTrimmingField.setBoolean(listInstance, false);

    Method trim = List.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);

    // String with length exactly trim+1 (should be trimmed)
    String justOver = "12345678901"; // length 11
    String result = (String) trim.invoke(listInstance, justOver, 10);
    assertThat(result.length(), is(10));
    assertThat(result, endsWith(".."));

    // String with length exactly trim-1 (should not be trimmed)
    String justUnder = "123456789"; // length 9
    String result2 = (String) trim.invoke(listInstance, justUnder, 10);
    assertThat(result2, is(justUnder));
    assertThat(result2, not(endsWith("..")));
  }
}
