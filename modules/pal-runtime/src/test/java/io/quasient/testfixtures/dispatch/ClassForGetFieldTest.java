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

import java.util.ArrayList;
import java.util.List;

/** Test fixture class for get-instance-variable dispatcher tests. */
@SuppressWarnings({"unused", "checkstyle:MemberName"})
public class ClassForGetFieldTest {
  public short someShort = 0;
  public byte[] bytes;
  public Integer someInteger;
  public String aString = "I am a normal string";
  public List<?> anObject = new ArrayList<>();
  public Object[] objects = {1, "a", false};
  private final Object[] privateObjects = {0, "b", true};
  public Throwable lastError = new Error("dummy error");
  public Class<?> aNullClass;
}
