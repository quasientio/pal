package net.ittera.pal.messages.jsonrpc;

import com.google.gson.*;
import java.lang.reflect.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcParameterDeserializer implements JsonDeserializer<JsonRpcParameter> {
  private static final Logger logger = LoggerFactory.getLogger(JsonRpcParameterDeserializer.class);

  @Override
  public JsonRpcParameter deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonRpcParameter param = new JsonRpcParameter();

    if (json.isJsonObject()) {
      JsonObject jsonObject = json.getAsJsonObject();

      boolean typeIsGiven = false;
      if (jsonObject.has("type") && !jsonObject.get("type").getAsString().isEmpty()) {
        final String typeStr = jsonObject.get("type").getAsString();
        param.setType(typeStr);
        typeIsGiven = true;
        param.setIsRef("ref".equalsIgnoreCase(typeStr));
      }

      if (jsonObject.has("value")) {
        JsonElement valueElement = jsonObject.get("value");
        if (valueElement.isJsonPrimitive()) {
          handleJsonPrimitive(param, valueElement.getAsJsonPrimitive(), typeIsGiven, param.isRef());
        } else {
          param.setValue(context.deserialize(valueElement, Object.class));
        }
      }
    } else if (json.isJsonPrimitive()) {
      handleJsonPrimitive(param, json.getAsJsonPrimitive(), false, false);
    }

    return param;
  }

  /**
   * Handles the deserialization of JsonPrimitive. Uses type inference to set the value of the
   * parameter
   *
   * <pre>
   *  For strings:
   *  - If the string length is 1, the value is set as a char.
   *
   *  For numbers:
   *  - If type is given, the value is set as the given type.
   *  - If type is not given, the value is set as the inferred type:
   *    - If the number is within the range of int, the value is set as an int.
   *    - If the number is outside the range of int, the value is set as a long.
   *    - If the number is a float or double, the value is set as a double.
   *    - TODO: handle the Big numbers: BigInteger and BigDecimal
   *  </pre>
   *
   * @param param
   * @param primitive
   * @param typeIsGiven
   * @param valueIsRef
   */
  private void handleJsonPrimitive(
      JsonRpcParameter param, JsonPrimitive primitive, boolean typeIsGiven, boolean valueIsRef) {
    if (primitive.isString()) {
      if (primitive.getAsString().length() == 1) {
        // if the string value is a single character, set value of param as char
        param.setValue(primitive.getAsString().charAt(0));
      } else {
        param.setValue(primitive.getAsString());
      }
    } else if (primitive.isNumber()) {
      Number num = primitive.getAsNumber();
      if (valueIsRef) { // objectRef's are always integers
        param.setValue(num.intValue());
        return;
      }
      if (typeIsGiven) {
        // if type is given, set value field with the given type
        if (param.getType().equalsIgnoreCase("int")
            || param.getType().equalsIgnoreCase("integer")
            || param.getType().equalsIgnoreCase("java.lang.Integer")) {
          param.setValue(num.intValue());
        } else if (param.getType().equalsIgnoreCase("long")
            || param.getType().equalsIgnoreCase("java.lang.Long")) {
          param.setValue(num.longValue());
        } else if (param.getType().equalsIgnoreCase("float")
            || param.getType().equalsIgnoreCase("java.lang.Float")) {
          param.setValue(num.floatValue());
        } else if (param.getType().equalsIgnoreCase("double")
            || param.getType().equalsIgnoreCase("java.lang.Double")) {
          param.setValue(num.doubleValue());
        } else if (param.getType().equalsIgnoreCase("byte")
            || param.getType().equalsIgnoreCase("java.lang.Byte")) {
          param.setValue(num.byteValue());
        } else if (param.getType().equalsIgnoreCase("short")
            || param.getType().equalsIgnoreCase("java.lang.Short")) {
          param.setValue(num.shortValue());
        } else if (param.getType().equalsIgnoreCase("BigInteger")
            || param.getType().equalsIgnoreCase("java.math.BigInteger")) {
          param.setValue(primitive.getAsBigInteger());
        } else if (param.getType().equalsIgnoreCase("BigDecimal")
            || param.getType().equalsIgnoreCase("java.math.BigDecimal")) {
          param.setValue(primitive.getAsBigDecimal());
        } else {
          logger.warn("Unsupported type for param: " + param.getType());
          param.setValue(num);
        }
      } else { // type not given, set value field with type inference
        if (Math.ceil(num.doubleValue()) != num.longValue()) {
          // floats and doubles are treated as double as by default
          param.setValue(num.doubleValue());
        } else {
          if (num.longValue() >= Integer.MIN_VALUE && num.longValue() <= Integer.MAX_VALUE) {
            // if the number is within the range of int, set value of param as int
            param.setValue(num.intValue());
          } else {
            // if the number is outside the range of int, set value of param as long
            param.setValue(num.longValue());
          }
        }
        // TODO handle the Big numbers: BigInteger and BigDecimal
      }
    } else if (primitive.isBoolean()) {
      param.setValue(primitive.getAsBoolean());
    }
  }
}
