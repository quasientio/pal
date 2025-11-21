/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

/**
 * Represents exceptions that occur during the parsing and validation of JSON-RPC 2.0 requests.
 * Serves as the base class for all specific JSON-RPC request-related exceptions.
 */
public abstract class JsonRpcRequestException extends RuntimeException {

  /**
   * The identifier of the JSON-RPC request that caused the exception. Used to correlate the
   * exception with the originating request.
   */
  protected String requestId;

  /**
   * Constructs a new JsonRpcRequestException with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  protected JsonRpcRequestException(String message) {
    super(message);
  }

  /**
   * Constructs a new JsonRpcRequestException with the specified cause.
   *
   * @param cause the underlying exception that triggered this JsonRpcRequestException
   */
  protected JsonRpcRequestException(Exception cause) {
    super(cause);
  }

  /**
   * Retrieves the identifier of the JSON-RPC request associated with this exception.
   *
   * @return the request identifier, or {@code null} if not set
   */
  public String getRequestId() {
    return requestId;
  }

  /**
   * Sets the identifier of the JSON-RPC request associated with this exception.
   *
   * @param requestId the request identifier to associate with this exception
   */
  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }
}
