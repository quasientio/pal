/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service.testdata;

import com.google.errorprone.annotations.DoNotCall;

/**
 * Test class with a main method that throws an exception. Used to test exception handling and exit
 * code behavior.
 */
public class MainThatThrows {
  @DoNotCall
  public static void main(String[] args) {
    throw new RuntimeException("Intentional test exception");
  }
}
