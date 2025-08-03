/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.woven.dummies;

import java.util.Locale;

/** Simple wrapper around some calls to {@link String}. */
public class MockString {

  /**
   * Delegates to {@link String#toUpperCase()}
   *
   * @return the argument to upper case
   */
  public static String toUpperCase(String str) {
    return str.toUpperCase(Locale.ENGLISH);
  }
}
