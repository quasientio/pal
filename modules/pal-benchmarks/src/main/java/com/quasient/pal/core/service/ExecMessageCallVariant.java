/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

package com.quasient.pal.core.service;

/**
 * Which combination of subsystems the gateway should activate.
 */
public enum ExecMessageCallVariant {
  /** No Intercepts, no WAL, no TCP-PUB.        */
  NOOP,

  /** Intercepts only.      */
  INTERCEPTS,

  /** PUB only.      */
  PUB,

  /** WAL only.      */
  WAL,

  /** PUB + WAL.      */
  PUB_WAL,

  /** Intercepts + TCP-PUB.            */
  INTERCEPTS_PUB,

  /** Intercepts + WAL.            */
  INTERCEPTS_WAL,

  /** Intercepts + TCP-PUB + WAL (full hot path). */
  INTERCEPTS_PUB_WAL,
}
