package net.ittera.pal.serdes.jsonrpc;

/**
 * This class is used to wrap a RpcJson parsing exception, giving us more flexibility to change the
 * underlying JSON library in the future.
 */
public class JsonRpcParseException extends JsonRpcRequestException {
  private final Exception jsonParsingException;

  public JsonRpcParseException(Exception e) {
    super(e);
    this.jsonParsingException = e;
  }

  public JsonRpcParseException(Exception e, String requestId) {
    this(e);
    this.requestId = requestId;
  }

  public Exception getJsonParsingException() {
    return jsonParsingException;
  }
}
