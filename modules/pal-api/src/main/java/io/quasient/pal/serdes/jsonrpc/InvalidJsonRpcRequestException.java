/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

/** Exception thrown to indicate that a JSON-RPC request is invalid. */
public class InvalidJsonRpcRequestException extends JsonRpcRequestException {

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public InvalidJsonRpcRequestException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified detail message and
   * request ID.
   *
   * @param message the detail message explaining the reason for the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcRequestException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified cause.
   *
   * @param cause the underlying cause of the exception
   */
  public InvalidJsonRpcRequestException(Exception cause) {
    super(cause);
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified cause and request
   * ID.
   *
   * @param cause the underlying cause of the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcRequestException(Exception cause, String requestId) {
    super(cause);
    this.requestId = requestId;
  }
}
