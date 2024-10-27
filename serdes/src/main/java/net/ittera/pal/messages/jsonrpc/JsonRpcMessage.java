package net.ittera.pal.messages.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ittera.pal.messages.RpcMessage;

public abstract class JsonRpcMessage implements RpcMessage {
  private static final Gson gson = new GsonBuilder().create();
  private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

  public String toJson() {
    return toJson(false);
  }

  public String toJson(boolean pretty) {
    return pretty ? prettyGson.toJson(this) : gson.toJson(this);
  }
}
