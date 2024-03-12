package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.ExecMessageType;

public class JsonRpcMessageUtils {
  private static Gson gson =
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
      throws InvalidJsonRpcRequestException {
    // Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest;
    try {
      jsonRpcRequest = gson.fromJson(jsonRpcMessage, JsonRpcRequest.class);
    } catch (RuntimeException ex) {
      throw new InvalidJsonRpcRequestException("Invalid JSON-RPC message: " + ex.getMessage());
    }

    // Set the ExecMessageType and other fields based on the method field
    try {
      jsonRpcRequest.processMethodParts();
    } catch (IllegalArgumentException e) {
      throw new InvalidJsonRpcRequestException(e);
    }

    // Check for empty/illegal RPC version
    if (!("2.0").equals(jsonRpcRequest.getJsonrpc())) {
      throw new InvalidJsonRpcRequestException(
          "Invalid JSON-RPC version: " + jsonRpcRequest.getJsonrpc());
    }

    // Check for empty id
    if (jsonRpcRequest.getId() == null || jsonRpcRequest.getId().isEmpty()) {
      throw new InvalidJsonRpcRequestException("Missing or blank JSON-RPC id)");
    }

    // Check that params that are Refs have a non-null value and are Integers
    if (jsonRpcRequest.getParams() != null) {
      for (JsonRpcParameter param : jsonRpcRequest.getParams()) {
        if (param.isRef()) {
          if (param.getValue() == null) {
            throw new InvalidJsonRpcRequestException("Ref parameter has a null value: " + param);
          }
          if (!(param.getValue() instanceof Integer)) {
            throw new InvalidJsonRpcRequestException(
                "Ref parameter has a non-integer value: " + param.getValue());
          }
        }
      }
    }

    // Check for illegal characters in the class name
    if (!VALID_CLASS_NAME_PATTERN.matcher(jsonRpcRequest.getClassName()).matches()) {
      throw new InvalidJsonRpcRequestException(
          "Invalid characters in class name: " + jsonRpcRequest.getClassName());
    }

    // Check for Java reserved keywords in the class name
    if (JAVA_RESERVED_KEYWORDS.contains(jsonRpcRequest.getClassName())) {
      throw new InvalidJsonRpcRequestException(
          "Class name is a Java reserved keyword: " + jsonRpcRequest.getClassName());
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
                + " given)");
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
                + " given)");
      }
    }

    return jsonRpcRequest;
  }
}
