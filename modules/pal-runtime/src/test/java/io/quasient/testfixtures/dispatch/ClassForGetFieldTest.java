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
