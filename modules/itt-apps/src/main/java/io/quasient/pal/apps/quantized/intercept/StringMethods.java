/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.intercept;

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

  /**
   * Wrapper method that calls echo(String).
   *
   * <p>Used for testing single-argument mutation via call-site weaving. The echo() call within this
   * method is the intercepted call site.
   *
   * @param input the input string
   * @return result of echo(input)
   */
  public String callEcho(String input) {
    return echo(input); // <-- Call site for interception
  }

  /**
   * Wrapper method that calls concatenate(String, String).
   *
   * <p>Used for testing multi-argument mutation via call-site weaving. The concatenate() call
   * within this method is the intercepted call site.
   *
   * @param a first string
   * @param b second string
   * @return result of concatenate(a, b)
   */
  public String callConcatenate(String a, String b) {
    return concatenate(a, b); // <-- Call site for interception
  }

  /**
   * Wrapper method that calls multiply(int, int).
   *
   * <p>Used for testing primitive argument mutation via call-site weaving. The multiply() call
   * within this method is the intercepted call site.
   *
   * @param value the value to multiply
   * @param factor the multiplication factor
   * @return result of multiply(value, factor)
   */
  public int callMultiply(int value, int factor) {
    return multiply(value, factor); // <-- Call site for interception
  }

  /**
   * Wrapper method that calls length(String).
   *
   * <p>Used for testing void callback behavior via call-site weaving. The length() call within this
   * method is the intercepted call site.
   *
   * @param input the input string
   * @return result of length(input)
   */
  public int callLength(String input) {
    return length(input); // <-- Call site for interception
  }

  /**
   * Void method that prints a message.
   *
   * <p>Used for testing AFTER intercepts on void methods.
   *
   * @param message the message to print
   */
  public void printMessage(String message) {
    System.out.println(message);
  }

  /**
   * Wrapper method that calls printMessage(String).
   *
   * <p>Used for testing AFTER intercepts on void methods via call-site weaving. The printMessage()
   * call within this method is the intercepted call site.
   *
   * @param message the message to print
   */
  public void callPrintMessage(String message) {
    printMessage(message); // <-- Call site for interception
  }
}
