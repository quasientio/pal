package net.ittera.pal.messages.jsonrpc;

public class InvalidJsonRpcRequestException extends Exception {
  public InvalidJsonRpcRequestException(String message) {
    super(message);
  }

  public InvalidJsonRpcRequestException(Exception cause) {
    super(cause);
  }
}
