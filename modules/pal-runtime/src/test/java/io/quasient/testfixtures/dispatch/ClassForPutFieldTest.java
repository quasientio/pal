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

/** Test fixture class for set-instance-variable dispatcher tests. */
@SuppressWarnings({"unused", "StaticAssignmentOfThrowable", "MemberName"})
public class ClassForPutFieldTest {
  public short someShort = 4;
  public byte[] bytes;
  public Long aLong = 8238L;
  public String aString = "I am a normal string";
  private final String aPrivateString = "I am a private string";
  public List<?> aList = new ArrayList<>();
  public Object[] objects;
  public Throwable lastError = new Exception("dummy exception");
}
