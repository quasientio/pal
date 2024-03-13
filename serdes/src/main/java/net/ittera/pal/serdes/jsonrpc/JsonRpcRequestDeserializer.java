package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;

public class JsonRpcRequestDeserializer implements JsonDeserializer<JsonRpcRequest> {
  private final JsonRpcParameterDeserializer parameterDeserializer =
      new JsonRpcParameterDeserializer();

  private static final List<String> VALID_REQUEST_ELEMENTS =
      Arrays.asList("jsonrpc", "method", "params", "id");
  private static final List<String> MANDATORY_ELEMENTS = Arrays.asList("jsonrpc", "method", "id");

  @Override
  public JsonRpcRequest deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    // check that all mandatory elements are present
    for (String element : MANDATORY_ELEMENTS) {
      if (!jsonObject.has(element)
          || jsonObject.get(element).isJsonNull()
          || jsonObject.get(element).getAsString().isEmpty()) {
        throw new InvalidJsonRpcRequestException("Missing required element: " + element);
      }
    }

    // check that no unknown elements are present
    for (String element : jsonObject.keySet()) {
      if (!VALID_REQUEST_ELEMENTS.contains(element)) {
        throw new InvalidJsonRpcRequestException("Unexpected element: " + element);
      }
    }

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
