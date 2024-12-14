package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import javax.annotation.Nullable;

public class JsonRpcResponseReturnValue {

  @SerializedName("is_void")
  private Boolean isVoid;

  @Nullable private ResponseObject value;

  private Executable from;

  public JsonRpcResponseReturnValue() {}

  public boolean getIsVoid() {
    return isVoid;
  }

  public void setIsVoid(boolean isVoid) {
    this.isVoid = isVoid;
  }

  @Nullable
  public ResponseObject getValue() {
    return value;
  }

  public void setValue(@Nullable ResponseObject value) {
    this.value = value;
  }

  public Executable getFrom() {
    return from;
  }

  public void setFrom(Executable from) {
    this.from = from;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcResponseReturnValue that)) {
      return false;
    }
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

    public Builder withValue(@Nullable ResponseObject value) {
      returnValue.setValue(value);
      return this;
    }

    public Builder withFrom(Executable from) {
      returnValue.setFrom(from);
      return this;
    }

    public JsonRpcResponseReturnValue build() {
      return returnValue;
    }
  }
}
