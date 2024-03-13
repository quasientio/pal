package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.ExecMessageType;

public class JsonRpcMessageUtils {
  private static final List<String> NOT_FOUND_EXCEPTION_TYPES =
      Arrays.asList(
          "java.lang.ClassNotFoundException",
          "java.lang.NoSuchMethodException",
          "java.lang.NoSuchFieldException");

  private static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(JsonRpcParameter.class, new JsonRpcParameterDeserializer())
          .registerTypeAdapter(JsonRpcRequest.class, new JsonRpcRequestDeserializer())
          .create();

  /**
   * Regular expression for a valid class name. This regex ensures that the class name starts with a
   * letter, underscore, or dollar sign and is followed by any combination of letters, digits,
   * underscores, or dollar signs. This includes names with Unicode characters, making it compliant
   * with Java's naming rules for class identifiers.
   */
  private static final String VALID_CLASS_NAME_REGEX = "^[\\p{L}_$][\\p{L}\\p{N}_$]*$";

  private static final Pattern VALID_CLASS_NAME_PATTERN = Pattern.compile(VALID_CLASS_NAME_REGEX);

  private static final List<String> JAVA_RESERVED_KEYWORDS =
      Arrays.asList(
          "class",
          "null",
          "true",
          "false",
          "final",
          "public",
          "private",
          "protected",
          "static",
          "void",
          "int",
          "long",
          "float",
          "double",
          "byte",
          "short",
          "char",
          "boolean",
          "if",
          "else",
          "while",
          "for",
          "do",
          "switch",
          "case",
          "default",
          "break",
          "continue",
          "return",
          "try",
          "catch",
          "finally",
          "throw",
          "throws",
          "new",
          "this",
          "super",
          "extends",
          "implements",
          "interface",
          "package",
          "import",
          "instanceof",
          "enum",
          "assert",
          "abstract",
          "const",
          "goto",
          "native",
          "synchronized",
          "transient",
          "volatile");

  public static JsonRpcRequest parseAndValidateJsonRpcMessage(String jsonRpcMessage)
      throws InvalidJsonRpcRequestException, InvalidJsonRpcParamsException, JsonRpcParseException {

    JsonObject jsonObject;
    String id = null;

    /* We follow a 2-step process to parse the JSON-RPC message: 1. Parse the JSON-RPC message as a
     JSON object and extract the id 2. Parse the JSON-RPC message as a JSON-RPC request and
     validate it.
     This allows us to return a JSON-RPC error that contains the id of the original request, even
     if the request is invalid.
    */

    // Parse the JSON-RPC message as a JSON object and extract the id
    try {
      jsonObject = gson.fromJson(jsonRpcMessage, JsonObject.class);
      JsonElement idElement = jsonObject.get("id");
      if (idElement != null && idElement.isJsonPrimitive()) {
        id = idElement.getAsString();
      }
    } catch (JsonParseException e) {
      throw new JsonRpcParseException(e);
    }

    if (id == null || id.isEmpty()) {
      throw new InvalidJsonRpcRequestException("Missing or blank id");
    }

    // Parse the JSON-RPC message as a JSON-RPC request and validate it
    JsonRpcRequest jsonRpcRequest;
    try {
      jsonRpcRequest = gson.fromJson(jsonObject, JsonRpcRequest.class);
    } catch (JsonParseException e) {
      throw new JsonRpcParseException(e, id);
    } catch (InvalidJsonRpcParamsException e) {
      e.setRequestId(id);
      throw e;
    } catch (Exception e) {
      throw new InvalidJsonRpcRequestException(e.getMessage(), id);
    }

    // Set the ExecMessageType and other fields based on the method field
    try {
      jsonRpcRequest.processMethodParts();
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcRequestException(e, id);
    }

    // Check for empty/illegal RPC version
    if (!("2.0").equals(jsonRpcRequest.getJsonrpc())) {
      throw new InvalidJsonRpcRequestException(
          "Invalid JSON-RPC version: " + jsonRpcRequest.getJsonrpc(), id);
    }

    // Check that params that are Refs have a non-null value and are Integers
    if (jsonRpcRequest.getParams() != null) {
      for (JsonRpcParameter param : jsonRpcRequest.getParams()) {
        if (param.isRef()) {
          if (param.getValue() == null) {
            throw new InvalidJsonRpcParamsException("Ref parameter has a null value: " + param, id);
          }
          if (!(param.getValue() instanceof Integer)) {
            throw new InvalidJsonRpcParamsException(
                "Ref parameter has a non-integer value: " + param.getValue(), id);
          }
        }
      }
    }

    // Check for illegal characters in the class name
    if (!VALID_CLASS_NAME_PATTERN.matcher(jsonRpcRequest.getClassName()).matches()) {
      throw new InvalidJsonRpcRequestException(
          "Invalid characters in class name: " + jsonRpcRequest.getClassName(), id);
    }

    // Check for Java reserved keywords in the class name
    if (JAVA_RESERVED_KEYWORDS.contains(jsonRpcRequest.getClassName())) {
      throw new InvalidJsonRpcRequestException(
          "Class name is a Java reserved keyword: " + jsonRpcRequest.getClassName(), id);
    }

    // Check for parameter consistency in field operations: PUTs should have exactly one parameter
    if (jsonRpcRequest.getExecMessageType() == ExecMessageType.PUT_STATIC
        || jsonRpcRequest.getExecMessageType() == ExecMessageType.PUT_FIELD) {
      if (jsonRpcRequest.getParams().size() != 1) {
        throw new InvalidJsonRpcRequestException(
            "Field put must have exactly one parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)",
            id);
      }
    }

    // Check for parameter consistency in field operations: GETs should have no parameters
    if (jsonRpcRequest.getExecMessageType() == ExecMessageType.GET_STATIC
        || jsonRpcRequest.getExecMessageType() == ExecMessageType.GET_FIELD) {
      if (!jsonRpcRequest.getParams().isEmpty()) {
        throw new InvalidJsonRpcRequestException(
            "Field get cannot have any parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)",
            id);
      }
    }

    return jsonRpcRequest;
  }

  /**
   * Check if the exception type is wrappable inside a JSON-RPC "Method not found" (-32601) so we
   * can return a proper JsonRpcErrorCode.METHOD_NOT_FOUND
   *
   * @param exceptionType
   * @return
   */
  public static boolean isMethodNotFoundError(String exceptionType) {
    return NOT_FOUND_EXCEPTION_TYPES.contains(exceptionType);
  }
}
