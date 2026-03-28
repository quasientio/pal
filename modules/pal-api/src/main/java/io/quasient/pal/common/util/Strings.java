/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.util;

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
