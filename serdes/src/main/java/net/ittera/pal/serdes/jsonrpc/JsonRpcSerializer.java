package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.Params;

public class JsonRpcSerializer {
  private static final Gson gson;
  private static final Gson prettyGson;

  static {
    // initialize Gson with custom deserializer(s)
    gson = new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();
    prettyGson =
        new GsonBuilder()
            .registerTypeAdapter(Params.class, new ParamsDeserializer())
            .setPrettyPrinting()
            .create();
  }

  public static String toJson(JsonRpcMessage message) throws JsonSerializationException {
    try {
      return gson.toJson(message);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to serialize JSON-RPC message", e);
    }
  }

  public static String toPrettyJson(JsonRpcMessage message) throws JsonSerializationException {
    try {
      return prettyGson.toJson(message);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to serialize JSON-RPC message", e);
    }
  }

  public static <T extends JsonRpcMessage> T fromJson(String json, Class<T> clazz)
      throws JsonSerializationException {
    try {
      return gson.fromJson(json, clazz);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to deserialize JSON-RPC message", e);
    }
  }
}
