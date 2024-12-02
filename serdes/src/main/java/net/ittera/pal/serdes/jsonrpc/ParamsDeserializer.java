package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.Params;

public class ParamsDeserializer implements JsonDeserializer<Params> {

  @Override
  public Params deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Expected a JSON object for Params");
    }

    JsonObject jsonObject = json.getAsJsonObject();

    // Create a new Params instance
    Params params = new Params();

    // Deserialize fields individually
    if (jsonObject.has("type")) {
      params.setType(context.deserialize(jsonObject.get("type"), String.class));
    }
    if (jsonObject.has("method")) {
      params.setMethod(context.deserialize(jsonObject.get("method"), String.class));
    }
    if (jsonObject.has("field")) {
      params.setField(context.deserialize(jsonObject.get("field"), String.class));
    }
    if (jsonObject.has("instance")) {
      params.setInstance(context.deserialize(jsonObject.get("instance"), Integer.class));
    }

    // Special handling for the `value` field
    if (jsonObject.has("value")) {
      JsonElement valueElement = jsonObject.get("value");
      // Pass true to return null for empty inputs
      Argument valueArgument = deserializeArgument(valueElement, true);
      params.setValue(valueArgument);
    } else {
      params.setValue(null); // Set to null if `value` is missing
    }

    // Special handling for the `args` field
    if (jsonObject.has("args")) {
      JsonElement argsElement = jsonObject.get("args");
      if (argsElement.isJsonArray()) {
        List<Argument> args = new ArrayList<>();
        for (JsonElement element : argsElement.getAsJsonArray()) {
          // Pass false to keep existing behavior for `args`
          args.add(deserializeArgument(element, false));
        }
        params.setArgs(args);
      } else {
        throw new JsonParseException("Expected 'args' to be an array.");
      }
    } else {
      params.setArgs(new ArrayList<>()); // Default empty list if `args` is missing
    }

    return params;
  }

  private Argument deserializeArgument(JsonElement element, boolean returnNullForEmpty) {
    // Handle null or empty object cases
    if (element.isJsonNull()
        || (element.isJsonObject() && element.getAsJsonObject().entrySet().isEmpty())) {
      if (returnNullForEmpty) {
        return null; // Return null for `value` field when empty
      } else {
        return new Argument(); // Return Argument instance with null fields for `args`
      }
    }

    Argument argument = new Argument();

    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        double numValue = primitive.getAsDouble();
        if (numValue == (int) numValue) {
          argument.setValue((int) numValue); // Preserve integer type
        } else {
          argument.setValue(numValue); // Otherwise, it's a double
        }
      } else if (primitive.isBoolean()) {
        argument.setValue(primitive.getAsBoolean());
      } else if (primitive.isString()) {
        argument.setValue(primitive.getAsString());
      }
    } else if (element.isJsonObject()) {
      // Directly map fields if it's a JSON object
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("value")) {
        argument.setValue(deserializeArgument(obj.get("value"), returnNullForEmpty).getValue());
      }
      if (obj.has("type")) {
        argument.setType(obj.get("type").getAsString());
      }
      if (obj.has("ref")) {
        argument.setRef(obj.get("ref").getAsInt());
      }
    } else {
      throw new JsonParseException("Unsupported Argument format: " + element);
    }

    return argument;
  }
}
