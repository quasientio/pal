package net.ittera.pal.messages.types;

/**
 * Enumerates standard JSON-RPC error codes with their corresponding messages. Each constant
 * represents a specific error condition as defined by the JSON-RPC 2.0 specification.
 */
public enum JsonRpcErrorCode {
  PARSE_ERROR("Parse error", -32700),
  INVALID_REQUEST("Invalid Request", -32600),
  METHOD_NOT_FOUND("Method not found", -32601),
  INVALID_PARAMS("Invalid params", -32602),
  INTERNAL_ERROR("Internal error", -32603),
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
