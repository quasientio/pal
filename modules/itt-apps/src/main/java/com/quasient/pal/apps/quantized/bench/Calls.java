/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.quantized.bench;

import java.util.Arrays;

/** Simple wrapper around misc calls to JDK classes. */
public interface Calls {

  /**
   * Delegates to {@link String#toUpperCase()}
   *
   * @return the argument to upper case
   */
  String toUpperCase(String str);

  /**
   * Delegates to {@link Arrays#sort(double[])}
   *
   * @param doubles an array of doubles
   */
  void sort(double[] doubles);
}
