package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResult;
import net.ittera.pal.messages.types.JsonRpcResultType;
import net.ittera.pal.serdes.colfer.JSONSerializers;

public class JsonRpcResponseDeserializer implements JsonDeserializer<JsonRpcResponse> {
  private final Gson gson = new Gson();

  private final Map<JsonRpcResultType, JsonDeserializer<?>> objectDeserializers;

  public JsonRpcResponseDeserializer(
      Map<JsonRpcResultType, JsonDeserializer<?>> objectDeserializers) {
    this.objectDeserializers = objectDeserializers;
  }

  public JsonRpcResponseDeserializer() {
    Map<JsonRpcResultType, JsonDeserializer<?>> objectDeserializers = new HashMap<>();
    objectDeserializers.put(
        JsonRpcResultType.STATIC_FIELDPUT_DONE, new JSONSerializers.StaticFieldPutDoneAdapter());
    objectDeserializers.put(
        JsonRpcResultType.INSTANCE_FIELDPUT_DONE,
        new JSONSerializers.InstanceFieldPutDoneAdapter());
    objectDeserializers.put(
        JsonRpcResultType.RETURN_VALUE, new JSONSerializers.ReturnValueAdapter());
    this.objectDeserializers = objectDeserializers;
  }

  @Override
  public JsonRpcResponse deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    // Deserialize the result field manually
    JsonElement resultElement = jsonObject.get("result");
    if (resultElement != null && !resultElement.isJsonNull()) {
      JsonObject resultObject = resultElement.getAsJsonObject();

      // Get the result type
      JsonRpcResultType resultType =
          JsonRpcResultType.valueOf(resultObject.get("type").getAsString());

      // Get the appropriate deserializer for the result type
      JsonDeserializer<?> objectDeserializer = objectDeserializers.get(resultType);
      if (objectDeserializer == null) {
        throw new IllegalArgumentException(
            "No deserializer registered for result type: " + resultType);
      }

      // Deserialize the object field manually using the appropriate deserializer
      JsonElement objectElement = resultObject.get("object");
      if (objectElement != null && !objectElement.isJsonNull()) {
        Object object = objectDeserializer.deserialize(objectElement, Object.class, context);

        // Create a new JsonRpcResponse and set the result object manually
        JsonRpcResponse response = gson.fromJson(jsonObject, JsonRpcResponse.class);
        response.setResult(new JsonRpcResult(object));
        return response;
      }
    }

    // If the result field is null or JSON null, deserialize the JSON normally
    return gson.fromJson(jsonObject, JsonRpcResponse.class);
  }
}
