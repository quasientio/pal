/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

/**
 * Provides a mechanism for generating unique identifiers.
 *
 * <p>Implementations of this interface should ensure that each ID generated is unique across the
 * system. This is useful for identifying entities, messages, or other components that require
 * distinct identification.
 */
public interface IdGenerator {
  /**
   * Generates the next unique identifier.
   *
   * @return a unique identifier as a {@code String}.
   */
  String nextId();
}
