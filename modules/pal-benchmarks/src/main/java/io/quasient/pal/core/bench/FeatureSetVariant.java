/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import io.quasient.pal.core.service.RunOptions;
import java.util.EnumSet;

/** Which combination of subsystems the gateway should activate. */
public enum FeatureSetVariant {
  /** Call to non-woven method. */
  NOWEAVE,

  /** No Intercepts, no WAL, no TCP-PUB. */
  NOOP,

  /** Intercepts only. */
  INTERCEPTS,

  /** PUB only. */
  PUB,

  /** WAL only. */
  WAL,

  /** PUB + WAL. */
  PUB_WAL,

  /** Intercepts + TCP-PUB. */
  INTERCEPTS_PUB,

  /** Intercepts + WAL. */
  INTERCEPTS_WAL,

  /** Intercepts + TCP-PUB + WAL (full hot path). */
  INTERCEPTS_PUB_WAL;

  /**
   * Return the {@link RunOptions} equivalent to this variant.
   *
   * @return enum set of run options.
   */
  public EnumSet<RunOptions> toRunOptions() {
    final EnumSet<RunOptions> runOpts = EnumSet.noneOf(RunOptions.class);
    switch (this) {
      case INTERCEPTS -> runOpts.add(RunOptions.WITH_INTERCEPTS);
      case PUB -> runOpts.add(RunOptions.WITH_TCP_PUB);
      case WAL -> runOpts.add(RunOptions.WITH_WAL);
      case PUB_WAL -> {
        runOpts.add(RunOptions.WITH_TCP_PUB);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
      }
      case INTERCEPTS_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case NOOP, NOWEAVE -> {}
    }
    return runOpts;
  }
}
