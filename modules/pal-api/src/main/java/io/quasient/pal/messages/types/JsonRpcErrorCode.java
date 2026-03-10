/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.types;

/**
 * Enumerates standard JSON-RPC error codes with their corresponding messages. Each constant
 * represents a specific error condition as defined by the JSON-RPC 2.0 specification.
 */
public enum JsonRpcErrorCode {

  /**
   * Indicates that the server failed to parse the JSON text. This is typically due to malformed
   * JSON syntax.
   *
   * <p>Error code: -32700
   */
  PARSE_ERROR("Parse error", -32700),

  /**
   * The JSON sent is not a valid Request object.
   *
   * <p>Error code: -32600
   */
  INVALID_REQUEST("Invalid Request", -32600),

  /**
   * The method does not exist or is not available.
   *
   * <p>Error code: -32601
   */
  METHOD_NOT_FOUND("Method not found", -32601),

  /**
   * Invalid method parameter(s).
   *
   * <p>Error code: -32602
   */
  INVALID_PARAMS("Invalid params", -32602),

  /**
   * Internal JSON-RPC error that occurred on the server side.
   *
   * <p>Error code: -32603
   */
  INTERNAL_ERROR("Internal error", -32603),

  /**
   * The requested RPC operation was denied by the peer's RPC access-control policy. Clients can
   * check for this code to distinguish policy denials from other server errors.
   *
   * <p>Error code: -32001
   */
  RPC_ACCESS_DENIED("RPC access denied", -32001),

  /**
   * Generic server-side error not covered by the other codes. The actual error code may vary from
   * -32000 to -32099 (except -32001, which is reserved for {@link #RPC_ACCESS_DENIED}).
   *
   * <p>Error code: -32000
   */
  SERVER_ERROR("Server error", -32000);

  /** The numeric code representing the JSON-RPC error. */
  final int code;

  /** The descriptive message associated with the JSON-RPC error. */
  final String message;

  /**
   * Constructs a {@code JsonRpcErrorCode} with the specified message and code.
   *
   * @param message the descriptive message for the error
   * @param code the numeric code representing the error
   */
  JsonRpcErrorCode(String message, int code) {
    this.code = code;
    this.message = message;
  }

  /**
   * Retrieves the numeric error code associated with this JSON-RPC error.
   *
   * @return the error code
   */
  public int getCode() {
    return code;
  }

  /**
   * Retrieves the descriptive message of this JSON-RPC error.
   *
   * @return the error message
   */
  public String getMessage() {
    return message;
  }
}
