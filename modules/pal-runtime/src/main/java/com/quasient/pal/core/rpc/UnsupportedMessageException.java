/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc;

/**
 * Signals that a provided message type is not supported in the current invocation context.
 *
 * <p>This exception extends {@link RuntimeException} and is thrown when an attempt is made to
 * process a message that is not recognized or supported by the Pal runtime.
 */
public class UnsupportedMessageException extends RuntimeException {

  /**
   * Constructs a new UnsupportedMessageException with the specified detail message.
   *
   * <p>The message parameter should provide details about the unsupported message type or cause.
   *
   * @param message the detail message identifying the unsupported message. May include context for
   *     debugging.
   */
  public UnsupportedMessageException(String message) {
    super(message);
  }
}
