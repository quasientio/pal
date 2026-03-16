/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

/**
 * Distinguishes method intercepts from field intercepts in the DSL layer.
 *
 * <p>This enum is used by {@link InterceptSpec} to determine whether an intercept targets a method
 * call or a field access operation.
 *
 * @see InterceptSpec
 */
public enum InterceptableKind {

  /** Intercept targets a method call. */
  METHOD,

  /** Intercept targets a field access operation. */
  FIELD
}
