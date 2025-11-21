/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.intercept;

/**
 * Test fixture application for BEFORE intercept callback integration tests.
 *
 * <p>Provides simple string manipulation methods for testing argument mutation via BEFORE intercept
 * callbacks.
 */
@SuppressWarnings("unused")
public class StringMethods {

  /**
   * Echoes the input string unchanged.
   *
   * <p>Used for testing single-argument mutation via BEFORE callbacks.
   *
   * @param input the input string
   * @return the same string
   */
  public String echo(String input) {
    return input;
  }

  /**
   * Concatenates two strings.
   *
   * <p>Used for testing multi-argument mutation via BEFORE callbacks.
   *
   * @param a first string
   * @param b second string
   * @return concatenated string
   */
  public String concatenate(String a, String b) {
    return a + b;
  }

  /**
   * Multiplies an integer by a factor.
   *
   * <p>Used for testing primitive argument mutation via BEFORE callbacks.
   *
   * @param value the value to multiply
   * @param factor the multiplication factor
   * @return product of value and factor
   */
  public int multiply(int value, int factor) {
    return value * factor;
  }

  /**
   * Returns the length of a string.
   *
   * <p>Used for testing void callback behavior (no mutation).
   *
   * @param input the input string
   * @return length of the string
   */
  public int length(String input) {
    return input.length();
  }
}
