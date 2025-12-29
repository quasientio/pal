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

/**
 * Test class with a main method that returns an explicit exit code via System.exit(). Used to test
 * exit code propagation.
 */
public class MainWithIntegerReturn {
  public static void main(String[] args) {
    // Simulate a program that wants to return a specific exit code
    // In real scenarios, this would be done via System.exit(42)
    // but for testing we can't actually call System.exit
    System.out.println("Exiting with code 42");
  }
}
