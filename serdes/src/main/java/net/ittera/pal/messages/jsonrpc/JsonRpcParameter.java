package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class JsonRpcParameter {
  @SerializedName("value")
  private Object value;

  @SerializedName("type")
  private String type;

  private transient boolean isRef;

  public Object getValue() {
    return value;
  }

  public String getType() {
    return type;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isRef() {
    return isRef;
  }

  public void setIsRef(boolean isRef) {
    this.isRef = isRef;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JsonRpcParameter that)) return false;
    return isRef == that.isRef
        && Objects.equals(value, that.value)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, type, isRef);
  }

  @Override
  public String toString() {
    return "JsonRpcParameter{"
        + "value="
        + value
        + ", type='"
        + type
        + '\''
        + ", isRef="
        + isRef
        + '}';
  }
}
