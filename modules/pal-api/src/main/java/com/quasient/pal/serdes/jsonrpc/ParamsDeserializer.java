/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import static com.quasient.pal.common.util.Classes.mapTypeStringToComponentClass;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.Params;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes JSON elements into {@link Params} instances according to JSON-RPC specifications.
 * Implements {@link JsonDeserializer} to provide custom deserialization logic for {@link Params}.
 */
public class ParamsDeserializer implements JsonDeserializer<Params> {

  /** Logger instance for logging deserialization. */
  protected static final Logger logger = LoggerFactory.getLogger(ParamsDeserializer.class);

  /**
   * {@inheritDoc}
   *
   * <p>Deserializes a JSON element into a {@link Params} object. It processes each field
   * individually and handles special cases for 'value' and 'args' fields, ensuring proper type
   * conversions and error handling.
   *
   * @param json the JSON data being deserialized
   * @param typeOfT the type of the Object to deserialize to
   * @param context the deserialization context
   * @return the deserialized {@link Params} instance
   * @throws JsonParseException if the JSON is not a valid representation for a {@link Params}
   *     object
   */
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
      Argument valueArgument = deserializeArgument(valueElement, context);
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
          args.add(deserializeArgument(element, context));
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

  /**
   * Deserializes a JSON element into an {@link Argument} object, handling various data types and
   * structures, including primitives, arrays, and objects with type information.
   *
   * @param element the JSON element representing the argument
   * @param context the deserialization context
   * @return the deserialized {@link Argument} instance
   * @throws JsonParseException if the JSON element cannot be properly deserialized into an {@link
   *     Argument}
   */
  private Argument deserializeArgument(JsonElement element, JsonDeserializationContext context) {
    // Handle null or empty object cases
    if (element.isJsonNull()
        || (element.isJsonObject() && element.getAsJsonObject().entrySet().isEmpty())) {
      // Empty argument
      return Argument.NULL;
    }

    Argument argument = new Argument();

    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        double numValue = primitive.getAsDouble();
        if (numValue == (int) numValue) {
          argument.setValue((int) numValue);
        } else {
          argument.setValue(numValue);
        }
      } else if (primitive.isBoolean()) {
        argument.setValue(primitive.getAsBoolean());
      } else if (primitive.isString()) {
        argument.setValue(primitive.getAsString());
      }
      return argument;
    } else if (element.isJsonArray()) {
      // Bare JSON array with no "type" object
      JsonArray arr = element.getAsJsonArray();
      Object[] arrayValues = parseJsonArrayToInferredArrayType(arr);
      argument.setValue(arrayValues);
      // Since no explicit type was given, we set argument.type based on inference
      String inferredType = inferArrayTypeNameFromObjectArray(arrayValues);
      argument.setType(inferredType);
      return argument;
    } else if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("name")) {
        argument.setName(obj.get("name").getAsString());
      }

      if (obj.has("ref")) {
        // If ref inside object is found, currently we are not handling arrays of refs.
        argument.setRef(obj.get("ref").getAsInt());
      }

      String givenType = null;
      if (obj.has("type")) {
        givenType = obj.get("type").getAsString();
        argument.setType(givenType);
      }

      if (obj.has("value") && obj.has("type")) {
        // Typed scenario:
        assert givenType != null;
        boolean isArray = isArrayType(givenType);
        JsonElement valueElem = obj.get("value");

        if (valueElem.isJsonNull()) {
          argument.setValue(null);
          return argument;
        }
        if (isArray) {
          if (!valueElem.isJsonArray()) {
            throw new JsonParseException("Expected 'value' to be an array for type: " + givenType);
          }
          Object typedArray =
              parseJsonArrayToSpecificArrayType(valueElem.getAsJsonArray(), givenType);
          argument.setValue(typedArray);
          return argument;
        } else {
          if (valueElem.isJsonPrimitive()) {
            try {
              Object val =
                  convertJsonPrimitiveToSingleType(valueElem.getAsJsonPrimitive(), givenType);
              argument.setValue(val);
              return argument;
            } catch (JsonParseException knownTypeEx) {
              // if not recognized, fallback to reflection-based approach
            }
          }
          // fallback for arbitrary complex objects
          try {
            Class<?> clazz = Class.forName(givenType);
            Object deserialized = context.deserialize(valueElem, clazz);
            argument.setValue(deserialized);
            return argument;
          } catch (ClassNotFoundException ex) {
            throw new JsonParseException("Cannot load class: " + givenType, ex);
          }
        }
      } else if (obj.has("value") && !obj.has("type")) {
        // Object with "value": if value is array, infer type
        JsonElement valueElem = obj.get("value");
        if (valueElem.isJsonNull()) {
          argument.setValue(null);
          return argument;
        }
        if (!valueElem.isJsonArray()) {
          // If value is not array, fallback to existing logic for single object
          Argument innerArg = deserializeArgument(valueElem, context);
          argument.setValue(innerArg.getValue());
          argument.setType(innerArg.getType());
          argument.setRef(innerArg.getRef());
          return argument;
        }

        // It's a bare array inside "value" without type
        JsonArray arr = valueElem.getAsJsonArray();
        Object[] arrayValues = parseJsonArrayToInferredArrayType(arr);
        argument.setValue(arrayValues);
        String inferredType = inferArrayTypeNameFromObjectArray(arrayValues);
        argument.setType(inferredType);
        return argument;
      }
    }

    return argument;
  }

  /**
   * Determines if a given type string represents an array type.
   *
   * @param givenType the type string to evaluate
   * @return {@code true} if the type is an array type; {@code false} otherwise
   */
  private boolean isArrayType(String givenType) {
    // If givenType starts with "[" or ends with "[]", treat it as array type
    return givenType.startsWith("[") || givenType.endsWith("[]");
  }

  /**
   * Converts a {@link JsonPrimitive} to a specific single Java type based on the provided type
   * string.
   *
   * @param p the JSON primitive to convert
   * @param givenType the target type as a string
   * @return the converted value as an {@link Object}
   * @throws JsonParseException if the conversion fails or the type is unsupported
   */
  private Object convertJsonPrimitiveToSingleType(JsonPrimitive p, String givenType) {

    // Handle common single-element wrapper types and String.
    // If givenType not recognized, throw exception.

    switch (givenType) {
      case "java.lang.String", "String" -> {
        if (!p.isString()) {
          throw new JsonParseException("Expected a string for type: " + givenType);
        }
        return p.getAsString();
      }
      case "java.lang.Boolean", "boolean" -> {
        if (p.isBoolean()) {
          return p.getAsBoolean();
        } else if (p.isString()) {
          String s = p.getAsString().toLowerCase(java.util.Locale.ROOT);
          if ("true".equals(s)) {
            return true;
          }
          if ("false".equals(s)) {
            return false;
          }
          throw new JsonParseException("Expected boolean value for type: " + givenType);
        } else {
          throw new JsonParseException("Expected boolean/string-boolean for type: " + givenType);
        }
      }
      case "java.lang.Integer", "int" -> {
        if (p.isNumber()) {
          double num = p.getAsDouble();
          if (num == (int) num) {
            return (int) num;
          } else {
            throw new JsonParseException("Non-integer numeric value for Integer type");
          }
        } else if (p.isString()) {
          String s = p.getAsString().trim();
          try {
            return Integer.parseInt(s);
          } catch (NumberFormatException e) {
            throw new JsonParseException("Non-integer numeric value for Integer type");
          }
        } else {
          throw new JsonParseException(
              "Expected a number or numeric string for type: " + givenType);
        }
      }
      case "java.lang.Double", "double" -> {
        if (p.isNumber()) {
          try {
            return p.getAsDouble();
          } catch (NumberFormatException e) {
            // fallback to string parsing
          }
        }
        if (p.isString()) {
          String s = p.getAsString().trim();
          if (s.isEmpty()) {
            throw new JsonParseException("Empty string for double type");
          }
          char lastChar = Character.toLowerCase(s.charAt(s.length() - 1));
          if (lastChar == 'd') {
            s = s.substring(0, s.length() - 1);
          }
          try {
            return Double.parseDouble(s);
          } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid double value: " + p.getAsString());
          }
        }
        throw new JsonParseException("Expected a number or numeric string for type: " + givenType);
      }
      case "java.lang.Long", "long" -> {
        if (p.isNumber()) {
          try {
            return p.getAsNumber().longValue();
          } catch (NumberFormatException e) {
            // fallback to string
          }
        }
        if (p.isString()) {
          String s = p.getAsString().trim();
          if (s.isEmpty()) {
            throw new JsonParseException("Empty string for long type");
          }
          char lastChar = Character.toLowerCase(s.charAt(s.length() - 1));
          if (lastChar == 'l') {
            s = s.substring(0, s.length() - 1);
          }
          try {
            return Long.parseLong(s);
          } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid long value: " + p.getAsString());
          }
        }
        throw new JsonParseException("Expected a number or numeric string for type: " + givenType);
      }
      case "java.lang.Float", "float" -> {
        if (p.isNumber()) {
          try {
            return p.getAsNumber().floatValue();
          } catch (NumberFormatException e) {
            // fallback to string
          }
        }
        if (p.isString()) {
          String s = p.getAsString().trim();
          if (s.isEmpty()) {
            throw new JsonParseException("Empty string for float type");
          }
          char lastChar = Character.toLowerCase(s.charAt(s.length() - 1));
          if (lastChar == 'f') {
            s = s.substring(0, s.length() - 1);
          }
          try {
            return Float.parseFloat(s);
          } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid float value: " + p.getAsString());
          }
        }
        throw new JsonParseException("Expected a number or numeric string for type: " + givenType);
      }
      case "java.lang.Short", "short" -> {
        if (p.isNumber()) {
          int iv = p.getAsNumber().intValue();
          if (iv < Short.MIN_VALUE || iv > Short.MAX_VALUE) {
            throw new JsonParseException("Value out of range for Short");
          }
          return (short) iv;
        } else if (p.isString()) {
          String s = p.getAsString().trim();
          try {
            int iv = Integer.parseInt(s);
            if (iv < Short.MIN_VALUE || iv > Short.MAX_VALUE) {
              throw new JsonParseException("Value out of range for Short");
            }
            return (short) iv;
          } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid short value: " + p.getAsString());
          }
        } else {
          throw new JsonParseException(
              "Expected a number or numeric string for type: " + givenType);
        }
      }
      case "java.lang.Byte", "byte" -> {
        if (p.isNumber()) {
          int bv = p.getAsNumber().intValue();
          if (bv < Byte.MIN_VALUE || bv > Byte.MAX_VALUE) {
            throw new JsonParseException("Value out of range for Byte");
          }
          return (byte) bv;
        } else if (p.isString()) {
          String s = p.getAsString().trim();
          try {
            int bv = Integer.parseInt(s);
            if (bv < Byte.MIN_VALUE || bv > Byte.MAX_VALUE) {
              throw new JsonParseException("Value out of range for Byte");
            }
            return (byte) bv;
          } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid byte value: " + p.getAsString());
          }
        } else {
          throw new JsonParseException(
              "Expected a number or numeric string for type: " + givenType);
        }
      }
      case "java.lang.Character", "char" -> {
        if (p.isString()) {
          String s = p.getAsString();
          if (s.length() == 1) {
            return s.charAt(0);
          } else {
            throw new JsonParseException("String length not 1 for char type");
          }
        } else {
          throw new JsonParseException("Expected a string for Character type");
        }
      }
      default -> throw new JsonParseException("Unsupported single type: " + givenType);
    }
  }

  /**
   * Parses a {@link JsonArray} and infers the appropriate Java array type based on its elements.
   *
   * @param arr the JSON array to parse
   * @return an array of {@link Object} with elements converted to their inferred types
   * @throws JsonParseException if the array contains unsupported elements or nested structures
   */
  private Object[] parseJsonArrayToInferredArrayType(JsonArray arr) {

    // If the array is empty, return an empty Object array
    if (arr.isEmpty()) {
      return new Object[0];
    }

    List<Object> elements = new ArrayList<>();
    boolean hasString = false;
    boolean hasNonString = false;

    for (JsonElement e : arr) {
      if (e.isJsonNull()) {
        // null is allowed in wrapper arrays or Object arrays
        elements.add(null);
      } else if (e.isJsonPrimitive()) {
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) {
          elements.add(p.getAsBoolean());
          hasNonString = true;
        } else if (p.isNumber()) {
          double num = p.getAsDouble();
          elements.add(num != (int) num ? num : (int) num);
          hasNonString = true;
        } else if (p.isString()) {
          String s = p.getAsString();
          // Handle numeric suffixes
          if (s.toLowerCase(java.util.Locale.ROOT).endsWith("d")) {
            try {
              elements.add(Double.parseDouble(s.substring(0, s.length() - 1)));
              hasNonString = true;
            } catch (NumberFormatException ex) {
              // Fallback to treating it as a string
              elements.add(s);
              hasString = true;
            }
          } else if (s.toLowerCase(java.util.Locale.ROOT).endsWith("f")) {
            try {
              elements.add(Float.parseFloat(s.substring(0, s.length() - 1)));
              hasNonString = true;
            } catch (NumberFormatException ex) {
              // Fallback to treating it as a string
              elements.add(s);
              hasString = true;
            }
          } else if (s.toLowerCase(java.util.Locale.ROOT).endsWith("l")) {
            try {
              elements.add(Long.parseLong(s.substring(0, s.length() - 1)));
              hasNonString = true;
            } catch (NumberFormatException ex) {
              // Fallback to treating it as a string
              elements.add(s);
              hasString = true;
            }
          } else {
            // Regular string without suffix
            elements.add(s);
            hasString = true;
          }
        }
      } else {
        // If we encounter complex object or array (multi-d?), fail without explicit type
        throw new JsonParseException(
            "Nested arrays or complex objects in array not supported without 'type'");
      }
    }

    // Determine the final array type:
    if (elements.isEmpty()) {
      return new Object[0]; // Empty array defaults to Object[]
    }

    if (hasString && hasNonString) {
      // Mixed types: Fallback to Object[]
      return elements.toArray();
    }

    if (hasString) {
      // All elements are strings or null because we already dealt with mixed in previous If
      return elements.toArray(new String[0]);
    }

    // Cast inferred numeric types (all booleans, all doubles, etc.)
    return castInferredArrayToObjectArray(elements);
  }

  /**
   * Casts a list of elements to a specific array type based on the inferred types of the elements.
   *
   * @param elements the list of elements to cast
   * @return an array of {@link Object} with elements of the inferred type
   */
  private Object[] castInferredArrayToObjectArray(List<Object> elements) {
    boolean allBoolean = true;
    boolean allIntegral = true;
    boolean allString = true;
    boolean anyDouble = false;

    for (Object o : elements) {
      if (o == null) {
        continue;
      }
      if (o instanceof Boolean) {
        allIntegral = false;
        allString = false;
      } else if (o instanceof Integer) {
        allBoolean = false;
        allString = false;
      } else if (o instanceof Double) {
        anyDouble = true;
        allBoolean = false;
        allIntegral = false;
        allString = false;
      } else if (o instanceof String) {
        allBoolean = false;
        allIntegral = false;
      } else {
        // Some other type encountered
        allBoolean = false;
        allIntegral = false;
        allString = false;
      }
    }

    if (anyDouble) {
      // Double[]
      Double[] arr = new Double[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
        Object val = elements.get(i);
        arr[i] = (val == null) ? null : ((Number) val).doubleValue();
      }
      return arr;
    } else if (allBoolean) {
      // Boolean[]
      Boolean[] arr = new Boolean[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
        Object val = elements.get(i);
        arr[i] = (val == null) ? null : (Boolean) val;
      }
      return arr;
    } else if (allIntegral) {
      // Integer[]
      Integer[] arr = new Integer[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
        Object val = elements.get(i);
        arr[i] = (val == null) ? null : ((Number) val).intValue();
      }
      return arr;
    } else if (allString) {
      // String[]
      String[] arr = new String[elements.size()];
      for (int i = 0; i < elements.size(); i++) {
        Object val = elements.get(i);
        arr[i] = (val == null) ? null : (String) val;
      }
      return arr;
    } else {
      // Mixed types => Object[]
      return elements.toArray();
    }
  }

  /**
   * Parses a {@link JsonArray} into a specific Java array type based on the given type string.
   *
   * @param arr the JSON array to parse
   * @param givenType the target array type as a string
   * @return the converted Java array
   * @throws JsonParseException if the array contains invalid elements or the type is unsupported
   */
  private Object parseJsonArrayToSpecificArrayType(JsonArray arr, String givenType) {
    // givenType could be "[I", "[Z", "[Ljava.lang.String;", "int[]", "Integer[]", etc.
    Class<?> componentType = mapTypeStringToComponentClass(givenType);
    if (componentType == null) {
      throw new JsonParseException("Unsupported type: " + givenType);
    }

    // Parse elements with respect to componentType
    // If primitive: must not encounter null, else throw exception
    // Convert each element to the appropriate type (boolean, int, double, string)
    // If arr is empty => zero-length array
    // If an element can't be converted, throw exception

    List<Object> elements = new ArrayList<>();
    for (JsonElement e : arr) {
      if (e.isJsonNull()) {
        if (componentType.isPrimitive()) {
          throw new JsonParseException("Null element in primitive array not allowed");
        }
        elements.add(null);
      } else if (e.isJsonPrimitive()) {
        Object val = convertJsonPrimitiveToType(e.getAsJsonPrimitive(), componentType);
        elements.add(val);
      } else {
        // If non-primitive element found (like nested array or object) => fail
        throw new JsonParseException("Complex element not supported in typed array: " + givenType);
      }
    }

    // Create array of correct type
    Object array = java.lang.reflect.Array.newInstance(componentType, elements.size());
    for (int i = 0; i < elements.size(); i++) {
      java.lang.reflect.Array.set(array, i, elements.get(i));
    }
    return array;
  }

  /**
   * Converts a {@link JsonPrimitive} to the specified Java type.
   *
   * @param p the JSON primitive to convert
   * @param targetType the target Java {@link Class} type
   * @return the converted value as an {@link Object}
   * @throws JsonParseException if the conversion fails or the type is unsupported
   */
  private Object convertJsonPrimitiveToType(JsonPrimitive p, Class<?> targetType) {
    if (logger.isTraceEnabled()) {
      logger.trace("Converting JsonPrimitive p: {} to type: {}", p, targetType.getName());
    }

    if (p.isBoolean()) {
      if (targetType == boolean.class || targetType == Boolean.class) {
        return p.getAsBoolean();
      }
      throw new JsonParseException("Expected " + targetType.getSimpleName() + " got boolean");
    } else if (p.isNumber()) {
      Number n = p.getAsNumber();
      // Handle numeric types
      if (targetType == int.class || targetType == Integer.class) {
        double d = n.doubleValue();
        if (d == (int) d) {
          return n.intValue();
        } else {
          throw new JsonParseException("Non-integer value in int array");
        }
      } else if (targetType == double.class || targetType == Double.class) {
        return n.doubleValue();
      } else if (targetType == long.class || targetType == Long.class) {
        return n.longValue();
      } else if (targetType == float.class || targetType == Float.class) {
        return n.floatValue();
      } else if (targetType == short.class || targetType == Short.class) {
        int v = n.intValue();
        if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
          return (short) v;
        } else {
          throw new JsonParseException("Value out of range for short array");
        }
      } else if (targetType == byte.class || targetType == Byte.class) {
        int v = n.intValue();
        if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
          return (byte) v;
        } else {
          throw new JsonParseException("Value out of range for byte array");
        }
      }
    } else if (p.isString()) {
      String s = p.getAsString();
      if (targetType == double.class || targetType == Double.class) {
        // If "d" suffix is present, remove it; otherwise parse directly
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith("d")) {
          s = s.substring(0, s.length() - 1);
        }
        return Double.parseDouble(s);
      } else if (targetType == float.class || targetType == Float.class) {
        // If "f" suffix is present, remove it; otherwise parse directly
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith("f")) {
          s = s.substring(0, s.length() - 1);
        }
        return Float.parseFloat(s);
      } else if (targetType == long.class || targetType == Long.class) {
        // If "l" suffix is present, remove it; otherwise parse directly
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith("l")) {
          s = s.substring(0, s.length() - 1);
        }
        return Long.parseLong(s);
      } else if (targetType == String.class) {
        return s; // Keep as string for String type
      } else if (targetType == char.class || targetType == Character.class) {
        if (s.length() == 1) {
          return s.charAt(0);
        } else {
          throw new JsonParseException("String length not 1 for char array element");
        }
      }
    }

    throw new JsonParseException("Unsupported JsonPrimitive in convertJsonPrimitiveToType");
  }

  /**
   * Infers the array type name based on the elements of an object array.
   *
   * @param array the object array to examine
   * @return the inferred type name in JVM array type descriptor format
   */
  private String inferArrayTypeNameFromObjectArray(Object[] array) {
    // If empty array, "Object[]"
    if (array.length == 0) {
      return "[Ljava.lang.Object;";
    }
    Object firstNonNull = null;
    for (Object o : array) {
      if (o != null) {
        firstNonNull = o;
        break;
      }
    }
    if (firstNonNull == null) {
      // all null => choose Object[]
      return "[Ljava.lang.Object;";
    }
    if (firstNonNull instanceof Boolean) {
      return "[Ljava.lang.Boolean;";
    }
    if (firstNonNull instanceof Integer) {
      return "[Ljava.lang.Integer;";
    }
    if (firstNonNull instanceof Double) {
      return "[Ljava.lang.Double;";
    }
    if (firstNonNull instanceof String) {
      return "[Ljava.lang.String;";
    }
    if (firstNonNull instanceof Character) {
      return "[Ljava.lang.Character;";
    }
    if (firstNonNull instanceof Long) {
      return "[Ljava.lang.Long;";
    }
    if (firstNonNull instanceof Float) {
      return "[Ljava.lang.Float;";
    }
    if (firstNonNull instanceof Short) {
      return "[Ljava.lang.Short;";
    }
    if (firstNonNull instanceof Byte) {
      return "[Ljava.lang.Byte;";
    }

    // fallback
    return "[Ljava.lang.Object;";
  }
}
