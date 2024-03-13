package net.ittera.pal.serdes.jsonrpc;

/** Base class for all JSON-RPC 2.0 parsing and validation exceptions. */
public abstract class JsonRpcRequestException extends RuntimeException {

  protected String requestId;

  protected JsonRpcRequestException(String message) {
    super(message);
  }

  protected JsonRpcRequestException(Exception cause) {
    super(cause);
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }
}
