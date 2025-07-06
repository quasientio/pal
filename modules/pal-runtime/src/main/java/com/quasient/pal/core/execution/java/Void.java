/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

/**
 * Singleton utility class representing an absence of value.
 *
 * <p>This class provides a single, shared instance used in contexts where a "void" equivalent is
 * required, such as in generic RPC operations where a specific type must be returned instead of
 * Java's primitive void.
 */
@SuppressWarnings({"UtilityClass", "JavaLangClash"})
public class Void {

  /**
   * The unique singleton instance of this class.
   *
   * <p>This constant is initialized once and reused across the system to represent an empty or void
   * value in operations where a concrete instance is required.
   */
  private static final Void instance = new Void();

  /**
   * Private constructor to enforce the singleton pattern.
   *
   * <p>Instantiating this class externally is prevented to ensure that only one instance exists for
   * representing the absence of a meaningful value.
   */
  private Void() {}

  /**
   * Returns the singleton instance of {@code Void}.
   *
   * <p>This method provides access to the unique instance of this class, ensuring that the same
   * "void" value is used consistently across the system.
   *
   * @return the unique singleton instance of {@code Void}
   */
  static Void getInstance() {
    return instance;
  }
}
