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
import java.util.Locale;

@SuppressWarnings("unused")
public class NonVoidStaticMethods {

  private static Thread singleton;

  public static Integer nonVoidSumUpList(List<Integer> listOfIntegers) {
    int sum = 0;
    if (listOfIntegers != null) {
      for (Integer anInt : listOfIntegers) {
        sum += anInt;
      }
    }
    return sum;
  }

  private static String testNonVoidStatic(String arg) {
    return arg.toLowerCase(Locale.getDefault());
  }

  protected static Integer highFive() {
    return 5;
  }

  public static Integer throwMeAnException() {
    throw new RuntimeException("Here you go");
  }

  public static Object giveMeNull() {
    return null;
  }

  static char[] toCharArray(String whateverString) {
    return whateverString.toCharArray();
  }

  public static Long[] giveMeAnEmptyLongArray() {
    return new Long[0];
  }

  public static Boolean[] giveMeNullBoolArray() {
    return null;
  }

  @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
  static Thread getThreadSingleton() {
    if (singleton == null) {
      singleton = new Thread();
    }
    return singleton;
  }

  @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
  static Thread[] getThreadArray() {
    int arraySize = 2;
    Thread[] threads = new Thread[arraySize];
    for (int i = 0; i < arraySize; i++) {
      threads[i] = new Thread();
    }

    return threads;
  }
}
