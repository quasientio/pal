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

import java.util.ArrayDeque;

/** Test fixture class for set-class-variable dispatcher tests. */
@SuppressWarnings({"unused", "StaticAssignmentOfThrowable"})
public class ClassForPutStaticTest {

  static {
    resetStaticVars();
  }

  public static short someShort;
  public static byte[] bytes;
  public static Boolean someBoolean;
  public static String aString;
  private static String secretString;
  public static ArrayDeque<?> aCollection;
  public static Object[] objects;
  public static Throwable lastError;

  /** Resets all static variables to their default test values. */
  public static void resetStaticVars() {
    someShort = 4;
    bytes = null;
    someBoolean = false;
    aString = "I am a normal string";
    aCollection = new ArrayDeque<>();
    objects = null;
    lastError = new Exception("dummy exception");
  }
}
