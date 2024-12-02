package net.ittera.pal.messages.jsonrpc;

import java.util.Objects;
import net.ittera.pal.serdes.jsonrpc.JsonRpcRequestValidator;

public class JsonRpcRequest extends JsonRpcMessage {
  private String method;

  private Params params;

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public Params getParams() {
    return params;
  }

  public void setParams(Params params) {
    this.params = params;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcRequest that)) return false;
    return Objects.equals(method, that.method) && Objects.equals(params, that.params);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, params);
  }

  @Override
  public String toString() {
    return "JsonRpcRequest{"
        + "method='"
        + method
        + '\''
        + ", params="
        + params
        + ", id='"
        + id
        + '\''
        + '}';
  }

  public static class Builder {
    private final JsonRpcRequest request = new JsonRpcRequest();

    public Builder withId(String id) {
      request.setId(id);
      return this;
    }

    public Builder withId(long id) {
      request.setId(id);
      return this;
    }

    public Builder withId(int id) {
      request.setId(id);
      return this;
    }

    public Builder withMethod(String method) {
      request.setMethod(method);
      return this;
    }

    public Builder withParams(Params params) {
      request.setParams(params);
      return this;
    }

    public JsonRpcRequest build() {
      if (request.getJsonrpc() == null) {
        request.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
      }
      JsonRpcRequestValidator.validate(request);
      return request;
    }
  }
}
