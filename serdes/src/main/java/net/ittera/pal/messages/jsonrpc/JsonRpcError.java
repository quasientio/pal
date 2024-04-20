package net.ittera.pal.messages.jsonrpc;

public class JsonRpcError {

  // A Number that indicates the error type that occurred. This MUST be an integer.
  private final int code;

  // A string providing a short description of the error. The message SHOULD be limited to a concise
  // single sentence.
  private final String message;

  // A Primitive or Structured value that contains additional information about the error.
  private final Object data;

  public JsonRpcError(int code, String message, Object data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public Object getData() {
    return data;
  }

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
