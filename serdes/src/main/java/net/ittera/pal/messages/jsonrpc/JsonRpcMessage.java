package net.ittera.pal.messages.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.ittera.pal.messages.RpcMessage;

public abstract class JsonRpcMessage implements RpcMessage {
  private static final Gson gson = new GsonBuilder().create();
  private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

  @SerializedName("jsonrpc")
  protected String jsonrpc;

  @SerializedName("id")
  protected String id;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String toJson() {
    return toJson(false);
  }

  public String toJson(boolean pretty) {
    return pretty ? prettyGson.toJson(this) : gson.toJson(this);
  }
}
