/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.apps.quantized.bench.Calls;
import java.util.Arrays;

/** Simple wrapper around misc calls to JDK classes. */
@SuppressFBWarnings(
    value = "DM_CONVERT_CASE",
    justification = "Benchmark - locale-independent case conversion for consistent results")
public class UnwovenCalls implements Calls {

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
