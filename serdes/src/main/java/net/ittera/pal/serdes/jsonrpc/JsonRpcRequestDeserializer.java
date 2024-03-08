package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;

public class JsonRpcRequestDeserializer implements JsonDeserializer<JsonRpcRequest> {
  private final JsonRpcParameterDeserializer parameterDeserializer =
      new JsonRpcParameterDeserializer();

  @Override
  public JsonRpcRequest deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    JsonRpcRequest request = new JsonRpcRequest();
    request.setJsonrpc(jsonObject.get("jsonrpc").getAsString());
    request.setMethod(jsonObject.get("method").getAsString());
    request.setId(jsonObject.get("id").getAsString());

    JsonElement paramsElement = jsonObject.get("params");
    if (paramsElement != null) {
      List<JsonRpcParameter> params = new ArrayList<>();
      for (JsonElement paramElement : paramsElement.getAsJsonArray()) {
        JsonRpcParameter param =
            parameterDeserializer.deserialize(paramElement, JsonRpcParameter.class, context);
        params.add(param);
      }
      request.setParams(params);
    }

    return request;
  }
}
