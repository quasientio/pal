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

import java.util.Random;
import java.util.stream.DoubleStream;

/** Test fixture class for non-void class method dispatcher tests. */
@SuppressWarnings("unused")
public class ClassForNonVoidClassMethodTest {
  private static final Random random = new Random();

  public static short getRandomMinute() {
    return (short) random.nextInt(60);
  }

  public static Double max(Double a, Double b) {
    return Math.max(a, b);
  }

  public static double max(double... doubles) {
    return DoubleStream.of(doubles).max().orElseThrow();
  }

  public static double min(double a, double b) {
    return Math.min(a, b);
  }

  @SuppressWarnings("NarrowCalculation")
  public static double divBy(int number, int divisor) {
    return number / divisor;
  }

  public static int somePublicMethod() {
    return add(4, 5);
  }

  private static Integer add(Integer a, Integer b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }
}
