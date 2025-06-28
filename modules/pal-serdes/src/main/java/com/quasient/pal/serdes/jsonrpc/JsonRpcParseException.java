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
 * Represents an exception that occurs during JSON parsing in JSON-RPC requests. This exception
 * wraps the underlying parsing exception to provide flexibility in changing the underlying JSON
 * library in the future.
 *
 * @see JsonRpcRequestException
 */
public class JsonRpcParseException extends JsonRpcRequestException {

  /** The exception that was thrown during JSON parsing. */
  private final Exception jsonParsingException;

  /**
   * Constructs a new JsonRpcParseException with the specified underlying parsing exception.
   *
   * @param e the exception that was thrown during JSON parsing
   */
  public JsonRpcParseException(Exception e) {
    super(e);
    this.jsonParsingException = e;
  }

  /**
   * Constructs a new JsonRpcParseException with the specified underlying parsing exception and
   * request ID.
   *
   * @param e the exception that was thrown during JSON parsing
   * @param requestId the ID of the JSON-RPC request associated with this exception
   */
  public JsonRpcParseException(Exception e, String requestId) {
    this(e);
    this.requestId = requestId;
  }

  /**
   * Returns the exception that was thrown during JSON parsing.
   *
   * @return the underlying JSON parsing exception
   */
  public Exception getJsonParsingException() {
    return jsonParsingException;
  }
}
