package net.ittera.pal.serdes.jsonrpc;

public class InvalidJsonRpcRequestException extends JsonRpcRequestException {

  public InvalidJsonRpcRequestException(String message) {
    super(message);
  }

  public InvalidJsonRpcRequestException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }

  public InvalidJsonRpcRequestException(Exception cause) {
    super(cause);
  }

  public InvalidJsonRpcRequestException(Exception cause, String requestId) {
    super(cause);
    this.requestId = requestId;
  }
}
