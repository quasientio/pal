package net.ittera.pal.messages.jsonrpc;

import com.google.gson.*;
import java.lang.reflect.Type;

public class JsonRpcParameterDeserializer implements JsonDeserializer<JsonRpcParameter> {
  @Override
  public JsonRpcParameter deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    JsonRpcParameter parameter = new JsonRpcParameter();

    // Handle value field with type inference
    JsonElement valueElement = jsonObject.get("value");
    if (valueElement != null && valueElement.isJsonPrimitive()) {
      JsonPrimitive valuePrimitive = valueElement.getAsJsonPrimitive();
      if (valuePrimitive.isString() && valuePrimitive.getAsString().length() == 1) {
        parameter.setValue(valuePrimitive.getAsString().charAt(0));
      } else if (valuePrimitive.isNumber()) {
        Number num = valuePrimitive.getAsNumber();
        if (Math.ceil(num.doubleValue()) != num.longValue()) {
          parameter.setValue(num.doubleValue());
        } else {
          if (num.longValue() <= Integer.MAX_VALUE && num.longValue() >= Integer.MIN_VALUE) {
            parameter.setValue(num.intValue());
          } else {
            parameter.setValue(num.longValue());
          }
        }
      } else {
        parameter.setValue(context.deserialize(valueElement, Object.class));
      }
    }

    // Deserialize other fields normally
    parameter.setType(context.deserialize(jsonObject.get("type"), String.class));

    return parameter;
  }
}
