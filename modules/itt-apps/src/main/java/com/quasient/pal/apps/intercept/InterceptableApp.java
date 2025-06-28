/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.intercept;

@SuppressWarnings("unused")
public class InterceptableApp {
  volatile Integer counter = 1;

  public void multiplyBy(Integer multiple) {
    int newValue = counter * multiple;
    counter = newValue;
  }

  public void multiplyCounterNTimesBy(Integer n, Integer factor) {
    for (int i = 0; i < n; i++) {
      multiplyBy(factor);
    }
  }
}
