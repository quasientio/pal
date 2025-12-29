/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

/** Thrown to indicate that a specific object was not found. */
public class ObjectNotFoundException extends Exception {

  /**
   * Constructs a new {@code ObjectNotFoundException} with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public ObjectNotFoundException(String message) {
    super(message);
  }
}
