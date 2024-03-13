package net.ittera.pal.messages.types;

public enum JsonRpcErrorCode {
  PARSE_ERROR("Parse error", -32700),
  INVALID_REQUEST("Invalid Request", -32600),
  METHOD_NOT_FOUND("Method not found", -32601),
  INVALID_PARAMS("Invalid params", -32602),
  INTERNAL_ERROR("Internal error", -32603),
  SERVER_ERROR("Server error", -32000);

  final int code;
  final String message;

  JsonRpcErrorCode(String message, int code) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
