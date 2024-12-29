package net.ittera.pal.serdes.jsonrpc;

import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.Params;
import net.ittera.pal.messages.types.MetaServiceType;

public class JsonRpcRequestValidator {
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

  private static final Set<String> VALID_METHODS = Set.of("new", "call", "get", "put", "meta");

  public static void validate(JsonRpcRequest request)
      throws InvalidJsonRpcRequestException, InvalidJsonRpcParamsException {

    /* 1. Validate the request Id */

    // check for non-empty request id
    String requestId = request.getId();
    if (requestId == null || requestId.isEmpty()) {
      throw new InvalidJsonRpcRequestException("Request Id is missing");
    }

    /* 2. Validate the JSON-RPC version */
    // Check for empty/illegal RPC version
    if (!"2.0".equals(request.getJsonrpc())) {
      throw new InvalidJsonRpcRequestException(
          "Invalid JSON-RPC version: " + request.getJsonrpc(), requestId);
    }

    /* 3. Validate the method name */
    if (request.getMethod() == null || request.getMethod().isEmpty()) {
      throw new InvalidJsonRpcRequestException("Method is missing", requestId);
    }

    if (!VALID_METHODS.contains(request.getMethod())) {
      throw new InvalidJsonRpcRequestException("Invalid method: " + request.getMethod(), requestId);
    }

    /* 4. Validate params */
    Params params = request.getParams();
    if (params == null) {
      throw new InvalidJsonRpcParamsException("Params are missing", requestId);
    }

    // Validate MetaMessage method is known and supported
    if (request.getMethod().equals("meta")) {
      String metaMethodName = request.getParams().getMethod();
      if (metaMethodName == null || metaMethodName.isBlank()) {
        throw new InvalidJsonRpcParamsException(
            "Null or blank Params:Method for 'meta' request", requestId);
      }
      MetaServiceType metaServiceType = MetaServiceType.fromJsonName(metaMethodName);
      if (metaServiceType == null) {
        throw new InvalidJsonRpcParamsException(
            "Invalid or unsupported Params:Method for 'meta' request", requestId);
      }

      // no more validations required for Meta messages
      return;
    }

    /* 4.1 check type (i.e. className) */
    String type = params.getType();
    if (type == null || type.isEmpty()) {
      throw new InvalidJsonRpcParamsException("Type is missing in params", requestId);
    }

    // Check for illegal characters in the class name
    List<String> classNameParts = Splitter.on('.').splitToList(type);
    String simpleClassName = classNameParts.get(classNameParts.size() - 1);

    if (!VALID_CLASS_NAME_PATTERN.matcher(simpleClassName).matches()) {
      throw new InvalidJsonRpcParamsException(
          "Invalid characters in type: " + simpleClassName, requestId);
    }

    // Check for Java reserved keywords
    if (JAVA_RESERVED_KEYWORDS.contains(simpleClassName)) {
      throw new InvalidJsonRpcParamsException(
          "Type name is a Java reserved keyword: " + simpleClassName, requestId);
    }

    /* 4.2 check specific fields that **should be set** based on the method */
    switch (request.getMethod()) {
      case "new":
        break;
      case "call":
        String method = params.getMethod();
        if (method == null || method.isEmpty()) {
          throw new InvalidJsonRpcParamsException("Method is missing in 'call' request", requestId);
        }
        break;
      case "get":
        String getField = params.getField();
        if (getField == null || getField.isEmpty()) {
          throw new InvalidJsonRpcParamsException("Field is missing in 'get' request", requestId);
        }
        break;
      case "put":
        String putField = params.getField();
        if (putField == null || putField.isEmpty()) {
          throw new InvalidJsonRpcParamsException("Field is missing in 'put' request", requestId);
        }
        if (params.getValue() == null) {
          throw new InvalidJsonRpcParamsException("Value is missing in 'put' request", requestId);
        }
        break;
      default:
        throw new InvalidJsonRpcRequestException(
            "Invalid method: " + request.getMethod(), requestId);
    }

    /* 4.3 check specific fields that **should NOT be set** based on the method */
    switch (request.getMethod()) {
      case "new":
        if (params.getInstance() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Instance should be null for 'new' request", requestId);
        }
        if (params.getMethod() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Method should be null for 'new' request", requestId);
        }
        if (params.getField() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Field should be null for 'new' request", requestId);
        }
        if (params.getValue() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Value should be null for 'new' request", requestId);
        }
        break;
      case "call":
        if (params.getField() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Field should be null for 'call' request", requestId);
        }
        if (params.getValue() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Value should be null for 'call' request", requestId);
        }
        break;
      case "get":
        if (params.getMethod() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Method should be null for 'get' request", requestId);
        }
        if (params.getArgs() != null && !params.getArgs().isEmpty()) {
          throw new InvalidJsonRpcParamsException(
              "Params:Args should be null/empty for 'get' request", requestId);
        }
        if (params.getValue() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Value should be null for 'get' request", requestId);
        }
        break;
      case "put":
        if (params.getMethod() != null) {
          throw new InvalidJsonRpcParamsException(
              "Params:Method should be null for 'put' request", requestId);
        }
        if (params.getArgs() != null && !params.getArgs().isEmpty()) {
          throw new InvalidJsonRpcParamsException(
              "Args should be null/empty for 'put' request", requestId);
        }
        break;
      default:
        throw new InvalidJsonRpcRequestException(
            "Invalid method: " + request.getMethod(), requestId);
    }

    /* 4.4 check consistency of args when present
      - ref arguments: type and value should be empty
      - value arguments: ref should be empty, type is optional
    */
    getArguments(request)
        .ifPresent(
            args ->
                args.forEach(
                    arg -> {
                      if (arg != null) {
                        if (arg.getRef() != null) {
                          // for ref arguments: type and value should be empty
                          if (arg.getType() != null && !arg.getType().isEmpty()) {
                            throw new InvalidJsonRpcParamsException(
                                "Type should be null/empty for Ref argument", requestId);
                          }
                          if (arg.getValue() != null) {
                            throw new InvalidJsonRpcParamsException(
                                "Value should be null/empty for Ref argument", requestId);
                          }
                        }
                      }
                    }));
  }

  private static Optional<List<Argument>> getArguments(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new", "call" -> Optional.ofNullable(jsonRpcRequest.getParams().getArgs());
      case "put" -> {
        Argument value = jsonRpcRequest.getParams().getValue();
        if (value != null) {
          yield Optional.of(List.of(value));
        } else {
          yield Optional.empty();
        }
      }
      default -> Optional.empty();
    };
  }
}
