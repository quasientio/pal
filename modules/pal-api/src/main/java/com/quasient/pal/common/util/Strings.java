/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.util;

/**
 * Provides utility methods for string manipulation, including operations to retrieve substrings
 * before or after specified separators. This class is final and should not be instantiated. Some
 * methods are inspired by the StringUtils class from Apache Commons Lang.
 */
public final class Strings {

  /** Private constructor to prevent instantiation of this utility class. */
  private Strings() {}

  /**
   * Determines whether a string is empty or null.
   *
   * @param str the string to check
   * @return {@code true} if the string is null or empty, {@code false} otherwise
   */
  private static boolean isEmpty(String str) {
    return (str == null || str.isEmpty());
  }

  /**
   * Returns the substring of the given string that precedes the first occurrence of the specified
   * separator.
   *
   * <p>If the input string is null or empty, or if the separator is null, the original string is
   * returned. If the separator is empty, an empty string is returned. If the separator is not found
   * in the string, the original string is returned.
   *
   * @param string the original string
   * @param sep the separator to search for
   * @return the substring before the separator, or the original string if the separator is not
   *     found or if the input string is null/empty
   */
  public static String stringBefore(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (sep.isEmpty()) {
      return "";
    }
    return string.contains(sep) ? string.substring(0, string.indexOf(sep)) : string;
  }

  /**
   * Returns the substring of the given string that follows the first occurrence of the specified
   * separator.
   *
   * <p>If the input string is null or empty, or if the separator is null, the original string is
   * returned. If the separator is empty, an empty string is returned. If the separator is not found
   * in the string, an empty string is returned.
   *
   * @param string the original string
   * @param sep the separator to search for
   * @return the substring after the separator, or an empty string if the separator is not found or
   *     if the input string is null/empty
   */
  public static String stringAfter(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (sep.isEmpty()) {
      return "";
    }
    return string.contains(sep) ? string.substring(string.indexOf(sep) + sep.length()) : "";
  }

  /**
   * Returns the substring of the given string that follows the last occurrence of the specified
   * separator.
   *
   * <p>If the input string is null or empty, or if the separator is null, the original string is
   * returned. If the separator is empty, an empty string is returned. If the separator is not found
   * in the string, an empty string is returned.
   *
   * @param string the original string
   * @param sep the separator to search for
   * @return the substring after the last separator, or an empty string if the separator is not
   *     found or if the input string is null/empty
   */
  public static String stringAfterLast(String string, String sep) {
    if (isEmpty(string) || sep == null) {
      return string;
    }
    if (isEmpty(sep)) {
      return "";
    }
    return string.contains(sep) ? string.substring(string.lastIndexOf(sep) + sep.length()) : "";
  }
}
