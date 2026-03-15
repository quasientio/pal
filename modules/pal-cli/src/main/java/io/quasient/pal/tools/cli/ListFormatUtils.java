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

import static java.lang.String.format;

import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Shared formatting utilities used by {@link PeerList}, {@link LogList}, and {@link InterceptList}.
 *
 * <p>Consolidates the common string trimming and date formatting logic that each list command
 * needs.
 */
final class ListFormatUtils {

  /** Prevents instantiation of this utility class. */
  private ListFormatUtils() {}

  /**
   * Optionally trims the given string to the specified maximum length, appending ".." if trimmed.
   * If {@code noTrimming} is true, returns the string unchanged.
   *
   * @param astring the string to trim
   * @param maxLength the maximum allowed length
   * @param noTrimming if true, disables trimming
   * @return the trimmed string if necessary, otherwise the original string
   */
  static String optionallyTrim(String astring, int maxLength, boolean noTrimming) {
    if (noTrimming || astring.length() <= maxLength) {
      return astring;
    }
    return astring.substring(0, maxLength - 2) + "..";
  }

  /**
   * Formats the given date and time.
   *
   * @param dateTime the date and time to format
   * @return a formatted date string in "MMM dd HH:mm" format, or "??" if null
   */
  static String getFormattedDate(OffsetDateTime dateTime) {
    if (dateTime == null) {
      return "??";
    }
    return format(
        "%s %02d %02d:%02d",
        dateTime.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute());
  }
}
