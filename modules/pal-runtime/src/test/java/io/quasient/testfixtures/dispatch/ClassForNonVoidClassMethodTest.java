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
