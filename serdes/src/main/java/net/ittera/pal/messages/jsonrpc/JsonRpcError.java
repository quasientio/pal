package net.ittera.pal.messages.jsonrpc;

import java.util.Objects;

public class JsonRpcError {

  private int code;

  private String message;

  @javax.annotation.Nullable private JsonRpcErrorData data;

  public JsonRpcError() {}

  public JsonRpcError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public JsonRpcError(int code, String message, @javax.annotation.Nullable JsonRpcErrorData data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @javax.annotation.Nullable
  public JsonRpcErrorData getData() {
    return data;
  }

  public void setData(@javax.annotation.Nullable JsonRpcErrorData data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcError that)) return false;
    return code == that.code
        && Objects.equals(message, that.message)
        && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, data);
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

  public static class Builder {
    private final JsonRpcError error = new JsonRpcError();

    public Builder withCode(int code) {
      error.setCode(code);
      return this;
    }

    public Builder withMessage(String message) {
      error.setMessage(message);
      return this;
    }

    public Builder withData(@javax.annotation.Nullable JsonRpcErrorData data) {
      error.setData(data);
      return this;
    }

    public JsonRpcError build() {
      return error;
    }
  }
}
