package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcSerializer {
  private static final Logger logger = LoggerFactory.getLogger(JsonRpcSerializer.class);
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
      String json = gson.toJson(message);
      if (logger.isDebugEnabled()) {
        logger.debug("Serialized JSON-RPC message: {}", json);
      }
      return json;
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
      T message = gson.fromJson(json, clazz);
      if (logger.isDebugEnabled()) {
        logger.debug("Deserialized JSON-RPC message: {}", message);
      }
      return message;
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to deserialize JSON-RPC message", e);
    }
  }
}
