package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.JsonParseException;

/**
 * This class is used to wrap a (gson) JsonParseException, giving us more flexibility to change the
 * underlying JSON library in the future.
 */
public class JsonRpcParseException extends JsonRpcRequestException {
  private final JsonParseException jsonParseException;

  public JsonRpcParseException(JsonParseException e) {
    super(e);
    this.jsonParseException = e;
  }

  public JsonRpcParseException(JsonParseException e, String requestId) {
    this(e);
    this.requestId = requestId;
  }

  public JsonParseException getJsonParseException() {
    return jsonParseException;
  }
}
