/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

/**
 * Specifies the policy for wrapping objects during serialization. Determines how object references
 * and values are managed to maintain referential integrity and performance.
 */
public enum WrapPolicy {

  /**
   * Prefers wrapping objects by reference to reduce redundancy and handle shared instances
   * efficiently. Suitable when maintaining object relationships and avoiding duplicate data is
   * essential.
   */
  PREFER_REFERENCE,

  /**
   * Forces wrapping objects by value, ensuring each object is serialized independently. Ideal when
   * object identity is not a concern and independent copies are required.
   */
  FORCE_BY_VALUE,

  /**
   * Automatically detects the optimal wrapping strategy based on the context and object structure.
   * Provides flexibility by selecting the most appropriate method at runtime.
   */
  DETECT
}
