package net.ittera.pal.serdes.colfer;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.ittera.pal.messages.colfer.ConstructorCall;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;

public class JsonRpcToExecMessageConverter {
  private Gson gson = new Gson();

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

  JsonRpcRequest parseAndValidateJsonRpcMessage(String jsonRpcMessage)
      throws IllegalArgumentException {
    // Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = gson.fromJson(jsonRpcMessage, JsonRpcRequest.class);

    // Set the ExecMessageType and other fields based on the method field
    jsonRpcRequest.processMethodParts();

    // Check for illegal characters in the class name
    if (!VALID_CLASS_NAME_PATTERN.matcher(jsonRpcRequest.getClassName()).matches()) {
      throw new IllegalArgumentException(
          "Invalid characters in class name: " + jsonRpcRequest.getClassName());
    }

    // Check for Java reserved keywords in the class name
    if (JAVA_RESERVED_KEYWORDS.contains(jsonRpcRequest.getClassName())) {
      throw new IllegalArgumentException(
          "Class name is a Java reserved keyword: " + jsonRpcRequest.getClassName());
    }

    // Check for parameter consistency in field operations: PUTs should have exactly one parameter
    if (jsonRpcRequest.getExecMessageType() == JsonRpcRequest.ExecMessageType.PUT_STATIC
        || jsonRpcRequest.getExecMessageType()
            == JsonRpcRequest.ExecMessageType.PUT_INSTANCE_FIELD) {
      if (jsonRpcRequest.getParams().size() != 1) {
        throw new IllegalArgumentException(
            "Field put must have exactly one parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)");
      }
    }

    // Check for parameter consistency in field operations: GETs should have no parameters
    if (jsonRpcRequest.getExecMessageType() == JsonRpcRequest.ExecMessageType.GET_STATIC
        || jsonRpcRequest.getExecMessageType()
            == JsonRpcRequest.ExecMessageType.GET_INSTANCE_FIELD) {
      if (!jsonRpcRequest.getParams().isEmpty()) {
        throw new IllegalArgumentException(
            "Field get cannot have any parameter: "
                + jsonRpcRequest.getMethod()
                + " ("
                + jsonRpcRequest.getParams().size()
                + " given)");
      }
    }

    return jsonRpcRequest;
  }

  public ExecMessage convertJsonRpcToExecMessage(String jsonRpcMessage, UUID fromPeerUuid) {
    // 1. Parse the JSON-RPC message
    JsonRpcRequest jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonRpcMessage);

    // 2. Create an instance of ExecMessage and initialize required fields
    ExecMessage execMessage = new ExecMessage();
    execMessage.setPeerUuid(fromPeerUuid.toString());
    execMessage.setMessageUuid(UUID.randomUUID().toString());
    // execMessage.setExecMessageType((byte) 0); TODO
    // currentTime TODO

    // threadName ?
    // dispatchSeq ?
    // builderSeq ?
    // followingUuid ?

    // 3. Create a ConstructorCall instance and fill it based on the JSON-RPC request
    ConstructorCall constructorCall = new ConstructorCall();
    //        constructorCall.setClazz(new Class().withName(jsonRpcRequest.getClassName()));
    constructorCall.setParameters(convertJsonParamsToColferParams(jsonRpcRequest.getParams()));
    // TODO modifiers
    // TODO context

    // 4. Set the constructorCall in execMessage
    execMessage.setConstructorCall(constructorCall);

    return execMessage;
  }

  private Parameter[] convertJsonParamsToColferParams(List<JsonRpcParameter> jsonParams) {
    // Convert JSON-RPC parameters to Colfer Parameter objects
    // Loop through jsonParams and create Parameter objects
    return null;
  }
}
