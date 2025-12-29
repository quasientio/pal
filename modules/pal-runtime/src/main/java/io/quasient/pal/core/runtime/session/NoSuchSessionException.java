/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.session;

/**
 * Exception indicating that an operation referenced a session which does not exist.
 *
 * <p>This exception is intended for use in contexts where session management is performed,
 * signaling that a requested session (identified by an ID or other reference) could not be found.
 */
public class NoSuchSessionException extends Exception {

  /**
   * Constructs a new NoSuchSessionException without a detail message.
   *
   * <p>Use this constructor when no additional context is required regarding the missing session.
   */
  public NoSuchSessionException() {}

  /**
   * Constructs a new NoSuchSessionException with the specified detail message.
   *
   * @param message a descriptive message providing context for the exception
   */
  public NoSuchSessionException(String message) {
    super(message);
  }
}
