package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class JsonRpcMessageIdAdapter implements JsonDeserializer<String>, JsonSerializer<String> {
  @Override
  public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonPrimitive()
        || !(json.getAsJsonPrimitive().isString() || json.getAsJsonPrimitive().isNumber())) {
      throw new JsonParseException("id must be a number or string");
    }
    return json.getAsString();
  }

  @Override
  public JsonElement serialize(String src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src);
  }
}
