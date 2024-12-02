package net.ittera.pal.messages.jsonrpc;

import java.util.Objects;

public class JsonRpcResponse extends JsonRpcMessage {
  @javax.annotation.Nullable private JsonRpcResponseReturnValue result;

  @javax.annotation.Nullable private JsonRpcError error;

  @javax.annotation.Nullable
  public JsonRpcResponseReturnValue getResult() {
    return result;
  }

  public void setResult(@javax.annotation.Nullable JsonRpcResponseReturnValue result) {
    this.result = result;
  }

  @javax.annotation.Nullable
  public JsonRpcError getError() {
    return error;
  }

  public void setError(@javax.annotation.Nullable JsonRpcError error) {
    this.error = error;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcResponse that)) return false;
    return Objects.equals(result, that.result) && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, error);
  }

  @Override
  public String toString() {
    return "JsonRpcResponse{"
        + "error="
        + error
        + ", result="
        + result
        + ", id='"
        + id
        + '\''
        + '}';
  }

  public static class Builder {
    private final JsonRpcResponse response = new JsonRpcResponse();

    public Builder withId(String id) {
      response.setId(id);
      return this;
    }

    public Builder withId(Long id) {
      response.setId(id);
      return this;
    }

    public Builder withId(int id) {
      response.setId(id);
      return this;
    }

    public Builder withResult(@javax.annotation.Nullable JsonRpcResponseReturnValue result) {
      response.setResult(result);
      return this;
    }

    public Builder withError(@javax.annotation.Nullable JsonRpcError error) {
      response.setError(error);
      return this;
    }

    public JsonRpcResponse build() {
      if (response.getJsonrpc() == null) {
        response.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
      }
      return response;
    }
  }
}
