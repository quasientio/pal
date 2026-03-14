/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.testfixtures.dispatch;

import java.util.List;

/** Test fixture class for void class method dispatcher tests. */
@SuppressWarnings({"unused", "MemberName"})
public class ClassForVoidClassMethodTest {
  public static boolean slept;
  public static Long millisSlept;
  public static Object verified;

  static {
    resetStaticVars();
  }

  public static void sleep() {
    slept = true;
  }

  public static void sleep(Long millis) {
    millisSlept = millis;
  }

  public static void sleepUnboxed(long millis) {
    millisSlept = millis;
  }

  public static void verify(Object toVerify) {
    verified = toVerify;
  }

  public static void add(List<Long> sumContainer, long... parts) {
    // add it manually, (use streams for verification)
    long sum = 0;
    for (long part : parts) {
      sum += part;
    }
    sumContainer.add(sum);
  }

  public static void addPositive(List<Long> someList, long chunk) {
    if (chunk > 0) {
      someList.add(chunk);
    }
  }

  private static void nap() {
    sleep(30 * 60 * 1000L);
  }

  /** Resets all static variables to their default test values. */
  public static void resetStaticVars() {
    verified = "blah";
    slept = false;
    millisSlept = 0L;
  }
}
