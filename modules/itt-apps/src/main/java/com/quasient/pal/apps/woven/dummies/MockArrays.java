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

import java.util.Arrays;

/** Simple wrapper around some calls to {@link Arrays}. */
public class MockArrays {

  /**
   * Delegates to {@link Arrays#sort(double[])}
   *
   * @param doubles an array of doubles
   */
  public static void sort(double[] doubles) {
    Arrays.sort(doubles);
  }
}
