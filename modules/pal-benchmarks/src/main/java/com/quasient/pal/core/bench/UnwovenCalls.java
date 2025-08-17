/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

import com.quasient.pal.bench.Calls;

import java.util.Arrays;
import java.util.Locale;

/**
 * Simple wrapper around misc calls to JDK classes.
 */
public class UnwovenCalls implements Calls {

  /**
   * {@inheritDoc}
   */
  @Override
  public String toUpperCase(String str) {
    return str.toUpperCase();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sort(double[] doubles) {
    Arrays.sort(doubles);
  }
}
