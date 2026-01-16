/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

/** Represents an exception that occurs during JSON serialization or deserialization processes. */
public class JsonSerializationException extends Exception {

  /**
   * Constructs a new JsonSerializationException with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the exception
   * @param cause the underlying cause of the exception
   */
  public JsonSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
