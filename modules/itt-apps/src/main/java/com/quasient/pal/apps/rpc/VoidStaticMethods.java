/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.rpc;

import java.util.List;

@SuppressWarnings("unused")
public class VoidStaticMethods {

  private static void testVoidStatic(String arg) {
    System.out.println(arg);
  }

  private static void printArg(int argIdx, String arg) {
    System.out.printf("Argument #%d to printArg: %s%n", argIdx, arg);
  }

  static void doSomethingStatically() {
    System.out.println("whatever");
  }

  static void throwRuntimeException() {
    throw new RuntimeException("Bastards threw me out!");
  }

  public static void sumUpList(List<Integer> listOfIntegers) {
    int sum = listOfIntegers.stream().reduce(0, Integer::sum);
    System.out.printf("The sum of integers = %d%n", sum);
  }

  public static void main(String[] args) {}
}
