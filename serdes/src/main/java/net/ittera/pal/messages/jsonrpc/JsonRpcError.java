package net.ittera.pal.messages.jsonrpc;

/**
 * When a rpc call encounters an error, the Response Object MUST contain the error member with a
 * value that is an Object with the following members.
 *
 * @param code A Number that indicates the error type that occurred. This MUST be an integer.
 * @param message A string providing a short description of the error. The message SHOULD be limited
 *     to a concise single sentence.
 * @param data A Primitive or Structured value that contains additional information about the error.
 */
public record JsonRpcError(int code, String message, Object data) {

  @Override
  public String toString() {
    return "JsonRpcError{"
        + "code="
        + code
        + ", message='"
        + message
        + '\''
        + ", data="
        + data
        + '}';
  }
}
