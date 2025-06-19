package com.quasient.pal.serdes.jsonrpc;

/**
 * Represents an exception that is thrown when the parameters of a JSON-RPC request are invalid.
 * This exception indicates that the provided parameters are either malformed or do not meet the
 * expected criteria.
 */
public class InvalidJsonRpcParamsException extends JsonRpcRequestException {

  /**
   * Constructs a new InvalidJsonRpcParamsException with the specified detail message and request
   * identifier.
   *
   * @param message the detail message explaining the reason for the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcParamsException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }
}
