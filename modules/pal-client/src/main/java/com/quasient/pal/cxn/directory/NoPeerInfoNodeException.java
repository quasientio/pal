/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

/** Represents an exception that is thrown when no peer information node is available. */
public class NoPeerInfoNodeException extends Exception {

  /**
   * Constructs a new NoPeerInfoNodeException with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public NoPeerInfoNodeException(String message) {
    super(message);
  }
}
