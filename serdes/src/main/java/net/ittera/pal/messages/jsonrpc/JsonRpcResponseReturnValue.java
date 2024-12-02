package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class JsonRpcResponseReturnValue {

  @SerializedName("is_void")
  private Boolean isVoid;

  @javax.annotation.Nullable private ResponseObject value;

  public JsonRpcResponseReturnValue() {}

  public boolean getIsVoid() {
    return isVoid;
  }

  public void setIsVoid(boolean isVoid) {
    this.isVoid = isVoid;
  }

  @javax.annotation.Nullable
  public ResponseObject getValue() {
    return value;
  }

  public void setValue(@javax.annotation.Nullable ResponseObject value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcResponseReturnValue that)) return false;
    return Objects.equals(isVoid, that.isVoid) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isVoid, value);
  }

  @Override
  public String toString() {
    return "JsonRpcResponseReturnValue{" + "isVoid=" + isVoid + ", value=" + value + '}';
  }

  public static class Builder {
    private final JsonRpcResponseReturnValue returnValue = new JsonRpcResponseReturnValue();

    public Builder withIsVoid(boolean isVoid) {
      returnValue.setIsVoid(isVoid);
      return this;
    }

    public Builder withValue(@javax.annotation.Nullable ResponseObject value) {
      returnValue.setValue(value);
      return this;
    }

    public JsonRpcResponseReturnValue build() {
      return returnValue;
    }
  }
}
