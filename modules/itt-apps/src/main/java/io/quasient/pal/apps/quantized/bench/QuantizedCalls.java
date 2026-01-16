/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.bench;

import java.util.Arrays;

/** Simple wrapper around misc calls to JDK classes. */
public class QuantizedCalls implements Calls {

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("StringCaseLocaleUsage")
  public String toUpperCase(String str) {
    return str.toUpperCase();
  }

  /** {@inheritDoc} */
  @Override
  public void sort(double[] doubles) {
    Arrays.sort(doubles);
  }
}
