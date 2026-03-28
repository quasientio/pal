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
