package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.JsonAdapter;
import net.ittera.pal.serdes.jsonrpc.JsonRpcMessageIdAdapter;

public abstract class JsonRpcMessage {
  public static final String JSON_RPC_VERSION = "2.0";

  private String jsonrpc;

  @JsonAdapter(JsonRpcMessageIdAdapter.class) // ensure id is given as a string or number
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

  public void setId(long id) {
    this.id = String.valueOf(id);
  }

  public void setId(int id) {
    this.id = String.valueOf(id);
  }
}
