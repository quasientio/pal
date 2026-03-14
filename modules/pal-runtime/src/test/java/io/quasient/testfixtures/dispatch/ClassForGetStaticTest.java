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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Test fixture class for get-class-variable dispatcher tests. */
@SuppressWarnings({"unused", "StaticAssignmentOfThrowable"})
public class ClassForGetStaticTest {
  public static short someShort = 4;
  public static byte[] bytes = "Some".getBytes(StandardCharsets.UTF_8);
  public static Integer someInteger = 965235;
  public static String aString = "I am a normal string";
  public static List<?> anObject = new ArrayList<>();
  public static Object[] objects = {1, "a", false};
  private static final Object[] privateObjects = new Object[] {0, "b", true};
  public static Throwable lastError = new Exception("dummy exception");
  public static Map<?, ?> aNullMap;
}
