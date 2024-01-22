package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;

public class JsonRpcParameter {
  @SerializedName("value")
  private Object value;

  @SerializedName("type")
  private String type;

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
}
