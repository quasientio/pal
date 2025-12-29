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
 * Test class with a main method that returns a non-Integer type. Used to test handling of invalid
 * return types. Note: Standard main() methods are void, but this simulates a scenario where
 * reflection might return an unexpected type.
 */
public class MainWithStringReturn {
  public static void main(String[] args) {
    System.out.println("This main returns void as expected");
  }
}
