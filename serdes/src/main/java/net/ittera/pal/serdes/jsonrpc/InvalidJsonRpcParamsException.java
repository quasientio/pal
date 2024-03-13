package net.ittera.pal.serdes.jsonrpc;

public class InvalidJsonRpcParamsException extends JsonRpcRequestException {

  public InvalidJsonRpcParamsException(String message) {
    super(message);
  }

  public InvalidJsonRpcParamsException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }
}
