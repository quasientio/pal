package net.ittera.pal.serdes.jsonrpc;

public class JsonRpcError {

  private int
      code; // A Number that indicates the error type that occurred. This MUST be an integer.
  private String
      message; // A string providing a short description of the error. The message SHOULD be limited
  // to a concise single sentence.

  private Object
      data; // A Primitive or Structured value that contains additional information about the error.

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
