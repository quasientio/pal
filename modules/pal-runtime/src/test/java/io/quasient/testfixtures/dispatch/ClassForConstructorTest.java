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

/** Test fixture class for constructor dispatcher tests. */
@SuppressWarnings({"unused", "checkstyle:MemberName"})
public class ClassForConstructorTest {
  public Integer someInteger;
  public String joinedVarArgs;
  public long aLong;

  public ClassForConstructorTest() {}

  public ClassForConstructorTest(boolean plusOne, long someLong) {
    this.aLong = plusOne ? someLong + 1 : someLong;
  }

  public ClassForConstructorTest(Integer someInteger) {
    this.someInteger = someInteger;
  }

  public ClassForConstructorTest(String someMalformedNumber) {
    this.someInteger = Integer.valueOf(someMalformedNumber);
  }

  public ClassForConstructorTest(String... args) {
    this.joinedVarArgs = String.join("", args);
  }

  // for visibility tests
  public ClassForConstructorTest(int i) {}

  protected ClassForConstructorTest(int i, int j) {}

  private ClassForConstructorTest(int i, int j, int k) {}
}
