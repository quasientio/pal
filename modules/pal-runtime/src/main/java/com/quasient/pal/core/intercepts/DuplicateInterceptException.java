/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercepts;

/** Exception thrown when attempting to register an intercept that has already been established. */
public class DuplicateInterceptException extends Exception {

  /**
   * Constructs a new DuplicateInterceptException with the specified detail message.
   *
   * @param message the detail message describing the duplicate intercept scenario
   */
  public DuplicateInterceptException(String message) {
    super(message);
  }
}
