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
