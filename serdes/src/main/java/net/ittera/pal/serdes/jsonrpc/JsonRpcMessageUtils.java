package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.MessageType;

public class JsonRpcMessageUtils {

  private static final List<String> NOT_FOUND_EXCEPTION_TYPES =
      Arrays.asList(
          "java.lang.ClassNotFoundException",
          "java.lang.NoSuchMethodException",
          "java.lang.NoSuchFieldException");

  public static JsonRpcRequest parseAndValidateJsonRpcMessage(String jsonRpcMessage)
      throws InvalidJsonRpcRequestException, InvalidJsonRpcParamsException, JsonRpcParseException {

    // Parse the JSON-RPC message as a JSON-RPC request and validate it
    JsonRpcRequest jsonRpcRequest;
    try {
      jsonRpcRequest = JsonRpcSerializer.fromJson(jsonRpcMessage, JsonRpcRequest.class);
    } catch (JsonSerializationException e) {
      // try to extract the id
      Object extractedId = GsonUtils.extractId(jsonRpcMessage);
      if (extractedId != null) {
        throw new JsonRpcParseException(e, extractedId.toString());
      }
      throw new JsonRpcParseException(e);
    } catch (Exception e) {
      throw new InvalidJsonRpcRequestException(e);
    }

    // Validate the JSON-RPC request
    JsonRpcRequestValidator.validate(jsonRpcRequest);

    return jsonRpcRequest;
  }

  /**
   * Check if the exception type is wrappable inside a JSON-RPC "Method not found" (-32601) so we
   * can return a proper JsonRpcErrorCode.METHOD_NOT_FOUND
   *
   * @param exceptionType the exception type to check
   * @return true if the exception type is a not found exception, false otherwise
   */
  public static boolean isMethodNotFoundError(String exceptionType) {
    return NOT_FOUND_EXCEPTION_TYPES.contains(exceptionType);
  }

  public static Optional<String> getClassName(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new", "call", "get", "put" -> Optional.of(jsonRpcRequest.getParams().getType());
      default -> Optional.empty();
    };
  }

  public static Optional<String> getFieldName(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "get", "put" -> Optional.of(jsonRpcRequest.getParams().getField());
      default -> Optional.empty();
    };
  }

  public static MessageType getMessageType(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new", "call", "get", "put" -> MessageType.EXEC_MESSAGE;
      default ->
          throw new IllegalArgumentException("Unsupported method: " + jsonRpcRequest.getMethod());
    };
  }

  public static ExecMessageType getExecMessageType(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new" -> ExecMessageType.CONSTRUCTOR;
      case "call" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield ExecMessageType.CLASS_METHOD;
        } else {
          yield ExecMessageType.INSTANCE_METHOD;
        }
      }
      case "get" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield ExecMessageType.GET_STATIC;
        } else {
          yield ExecMessageType.GET_FIELD;
        }
      }
      case "put" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield ExecMessageType.PUT_STATIC;
        } else {
          yield ExecMessageType.PUT_FIELD;
        }
      }
      default ->
          throw new IllegalArgumentException("Unsupported method: " + jsonRpcRequest.getMethod());
    };
  }

  private static class GsonUtils {

    public static Object extractId(String json) {
      JsonReader reader = new JsonReader(new StringReader(json));
      reader.setStrictness(Strictness.LENIENT); // Enable lenient parsing to handle malformed JSON
      try {
        return extractId(reader);
      } catch (Exception e) {
        // If extraction fails, return null
        return null;
      }
    }

    private static Object extractId(JsonReader reader) throws Exception {
      reader.beginObject();

      while (reader.hasNext()) {
        String field = reader.nextName();
        if ("id".equals(field)) {
          return readIdValue(reader);
        } else {
          reader.skipValue(); // Skip other fields
        }
      }

      reader.endObject();
      // 'id' field not found
      return null;
    }

    private static Object readIdValue(JsonReader reader) throws Exception {
      return switch (reader.peek()) {
        case STRING -> reader.nextString(); // 'id' is a string
        case NUMBER -> reader.nextDouble(); // 'id' is a number
        case NULL -> {
          reader.nextNull();
          yield null;
        }
        default -> {
          reader.skipValue(); // Unknown type, skip it
          yield null;
        }
      };
    }
  }
}
