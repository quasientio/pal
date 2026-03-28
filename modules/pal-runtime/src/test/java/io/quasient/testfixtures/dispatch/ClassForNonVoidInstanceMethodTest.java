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

import java.util.Locale;

/** Test fixture class for non-void instance method dispatcher tests. */
@SuppressWarnings("unused")
public class ClassForNonVoidInstanceMethodTest {
  private String value;

  public ClassForNonVoidInstanceMethodTest() {}

  public ClassForNonVoidInstanceMethodTest(String value) {
    this.value = value;
  }

  public String floatAsString(float someFloat) {
    return String.valueOf(someFloat);
  }

  public String toUpperCase() {
    return value.toUpperCase(Locale.getDefault());
  }

  public String append(String value) {
    if (value == null) {
      return this.value;
    }
    return this.value.concat(value);
  }

  public String join(String joiner, String... values) {
    return String.join(joiner, values);
  }
}
