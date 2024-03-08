package net.ittera.pal.serdes.jsonrpc;

import static net.ittera.pal.common.util.Classes.isOneDimensionalPrimitiveArray;
import static net.ittera.pal.common.util.Classes.isOneDimensionalPrimitiveWrapperArray;
import static net.ittera.pal.common.util.Classes.isPrimitive;
import static net.ittera.pal.common.util.Classes.isPrimitiveWrapper;
import static net.ittera.pal.common.util.Classes.isValidClassName;

import com.google.gson.*;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notes about the deserialization of JsonRpcParameter:
 *
 * <pre>
 *   - Refs: if the "type" field exists in the JSON object and it's not empty,
 *       the isRef field of the JsonRpcParameter object is set to true if the type
 *       is "ref" (case-insensitive) or a valid class name. If the type is not
 *       "ref", it's set as the type of the JsonRpcParameter object.
 *
 *   - Primitives:
 *     - strings:
 *        - If the param isRef and the string can be parsed as an integer number,
 *          then value is set as an int.
 *        - If the string length is 1, then value is set as a char.
 *
 *     - numbers:
 *        - If the param isRef and the number is an integer, then value is
 *          set as an int.
 *        - If type is given, then value is set as the given type.
 *        - If type is not given, then value is set as the inferred type:
 *        - If the number is within the range of int, then value is set as an int.
 *        - If the number is outside the range of int, then value is
 *          set as a long.
 *        - If the number is a float or double, then value is set as a double.
 *        - TODO: handle the Big numbers: BigInteger and BigDecimal*
 *
 *  BEWARE of the use of type inference to set the value of the parameter
 *  for integer values, and the conversion from string -> char for strings
 *  of length 1, and from string -> int for strings that can be parsed as
 *  an integer when param is Ref.
 * </pre>
 */
public class JsonRpcParameterDeserializer implements JsonDeserializer<JsonRpcParameter> {
  private static final Logger logger = LoggerFactory.getLogger(JsonRpcParameterDeserializer.class);

  /**
   * Returns true if the given (non-simple) type is a class that can be deserialized. So far this
   * includes arrays that can currently be deserialized from a JsonArray (1-dimensional arrays of:
   * primitives, primitive wrappers or strings).
   *
   * @param type
   * @return
   */
  private boolean isSerializable(String type) {
    return "[Ljava.lang.String;".equalsIgnoreCase(type)
        || isOneDimensionalPrimitiveArray(type)
        || isOneDimensionalPrimitiveWrapperArray(type);
  }

  private boolean isClass(String type) {
    return isValidClassName(type) && !isPrimitive(type);
  }

  @Override
  public JsonRpcParameter deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    JsonRpcParameter param = new JsonRpcParameter();

    if (json.isJsonObject()) {
      JsonObject jsonObject = json.getAsJsonObject();

      // set type and isRef
      if (jsonObject.has("type")) {
        String typeStr = jsonObject.get("type").getAsString();
        if (!typeStr.isEmpty()) {
          param.setIsRef(
              "ref".equalsIgnoreCase(typeStr) || (isClass(typeStr) && !isSerializable(typeStr)));
          if (!"ref".equalsIgnoreCase(typeStr)) {
            param.setType(typeStr);
          }
        }
      }

      // set value
      if (jsonObject.has("value")) {
        JsonElement valueElement = jsonObject.get("value");
        if (valueElement.isJsonPrimitive()) {
          handleJsonPrimitive(
              param, valueElement.getAsJsonPrimitive(), param.getType() != null, param.isRef());
        } else if (valueElement.isJsonArray()) {
          handleJsonArray(param, valueElement.getAsJsonArray(), context);
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
   * Handles the deserialization of a JsonArray. Requires that type is given.
   *
   * <pre>
   * Supports 1-dimensional arrays of:
   *  - primitives
   *  - primitive wrappers
   *  - strings
   * </pre>
   *
   * @param param
   * @param jsonArray
   * @param context
   */
  private void handleJsonArray(
      JsonRpcParameter param, JsonArray jsonArray, JsonDeserializationContext context) {
    final String type = param.getType();
    if (type == null) {
      throw new RuntimeException("Type not given for array param: " + param);
    }

    Class<?> clazz;
    try {
      clazz = Class.forName(type);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Invalid class type: " + type);
    }
    Class<?> componentType = clazz.getComponentType();

    if (clazz.isArray()
        || componentType.equals(String.class)
        || componentType.isPrimitive()
        || isPrimitiveWrapper(componentType)) {
      Object array = Array.newInstance(componentType, jsonArray.size());
      for (int i = 0; i < jsonArray.size(); i++) {
        Array.set(array, i, context.deserialize(jsonArray.get(i), componentType));
      }
      param.setValue(array);
    } else {
      throw new RuntimeException("Unsupported array type: " + type);
    }
  }

  private void handleJsonPrimitive(
      JsonRpcParameter param, JsonPrimitive primitive, boolean typeIsGiven, boolean paramIsRef) {

    // if valueIsRef - set value as Integer if the primitive is integer or a string representing an
    // integer
    if (paramIsRef && (primitive.isNumber() || primitive.isString())) {
      if (primitive.isNumber()) {
        Number num = primitive.getAsNumber();
        boolean isIntegerValue = Math.ceil(num.doubleValue()) == num.longValue();
        if (isIntegerValue) {
          param.setValue(num.intValue());
          return;
        } else {
          throw new RuntimeException("Ref param value is not an integer: " + num);
        }
      } else {
        String numStr = primitive.getAsString();
        try {
          param.setValue(Integer.parseInt(numStr));
          return;
        } catch (NumberFormatException e) {
          throw new RuntimeException("Ref param value is not an integer: " + numStr);
        }
      }
    }

    // not a ref, so continue with the normal deserialization
    if (primitive.isString()) {
      if (primitive.getAsString().length() == 1) {
        // if the string value is a single character, set value of param as char
        param.setValue(primitive.getAsString().charAt(0));
      } else {
        param.setValue(primitive.getAsString());
      }
    } else if (primitive.isNumber()) {
      Number num = primitive.getAsNumber();
      // if type is given, set value field with the given type
      if (typeIsGiven) {
        if (param.getType().equalsIgnoreCase("int")
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
          throw new RuntimeException("Unsupported type for param: " + param);
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
