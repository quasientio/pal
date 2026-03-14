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
