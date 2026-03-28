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
