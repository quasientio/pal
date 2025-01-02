package net.ittera.pal.serdes.jsonrpc;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.ittera.pal.messages.jsonrpc.Executable;
import net.ittera.pal.messages.jsonrpc.JsonRpcError;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.types.JsonRpcType;
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

  public static Optional<String> getClassName(JsonRpcMessage jsonRpcMessage) {
    if (jsonRpcMessage instanceof JsonRpcRequest request) {
      return getClassName(request);
    } else if (jsonRpcMessage instanceof JsonRpcResponse response) {
      return getClassName(response);
    } else {
      throw new IllegalArgumentException(
          "Unsupported message type: " + jsonRpcMessage.getClass().getName());
    }
  }

  public static Optional<String> getClassName(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new", "call", "get", "put" -> Optional.of(jsonRpcRequest.getParams().getType());
      default -> Optional.empty();
    };
  }

  @SuppressWarnings("checkstyle:FallThrough")
  public static Optional<String> getClassName(JsonRpcResponse jsonRpcResponse) {
    MessageType jsonRpcResponseType = getJsonRpcResponseType(jsonRpcResponse);
    boolean isFieldPutDone = false;
    switch (jsonRpcResponseType) {
      case EXEC_THROWABLE:
        JsonRpcError error = jsonRpcResponse.getError();
        if (error == null || error.getData() == null) {
          return Optional.empty();
        }
        return Optional.of(error.getData().getThrowableType());
      case EXEC_PUT_STATIC_DONE:
      case EXEC_PUT_FIELD_DONE:
        isFieldPutDone = true;
        // fall through
      case EXEC_RETURN_VALUE:
        JsonRpcResponseReturnValue returnValue = jsonRpcResponse.getResult();
        if (returnValue == null || returnValue.getFrom() == null) {
          return Optional.empty();
        }
        String valueType;
        if (isFieldPutDone) {
          valueType = returnValue.getFrom().getClassName();
        } else {
          valueType =
              returnValue.getValue() != null ? returnValue.getValue().getType() : "<unknown>";
        }
        return Optional.ofNullable(valueType);
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported JSON-RPC response type: %s", jsonRpcResponseType));
    }
  }

  public static Optional<String> getFieldName(JsonRpcResponse jsonRpcResponse) {
    MessageType jsonRpcResponseType = getJsonRpcResponseType(jsonRpcResponse);
    JsonRpcResponseReturnValue returnValue = jsonRpcResponse.getResult();
    switch (jsonRpcResponseType) {
      case EXEC_PUT_STATIC_DONE:
      case EXEC_PUT_FIELD_DONE:
        if (returnValue == null || returnValue.getFrom() == null) {
          return Optional.empty();
        }
        return Optional.ofNullable(returnValue.getFrom().getFieldName());
      default:
        return Optional.empty();
    }
  }

  public static Optional<String> getFieldName(JsonRpcRequest jsonRpcRequest) {
    assert jsonRpcRequest.getParams().getField() != null;
    return switch (jsonRpcRequest.getMethod()) {
      case "get", "put" -> Optional.of(jsonRpcRequest.getParams().getField());
      default -> Optional.empty();
    };
  }

  public static MessageType getMessageType(JsonRpcMessage jsonRpcMessage) {
    if (jsonRpcMessage instanceof JsonRpcRequest request) {
      return getMessageType(request);
    } else if (jsonRpcMessage instanceof JsonRpcResponse response) {
      return getMessageType(response);
    } else {
      throw new IllegalArgumentException(
          "Unsupported message type: " + jsonRpcMessage.getClass().getName());
    }
  }

  public static MessageType getMessageType(JsonRpcRequest jsonRpcRequest) {
    return switch (jsonRpcRequest.getMethod()) {
      case "new" -> MessageType.EXEC_CONSTRUCTOR;
      case "call" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield MessageType.EXEC_CLASS_METHOD;
        } else {
          yield MessageType.EXEC_INSTANCE_METHOD;
        }
      }
      case "get" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield MessageType.EXEC_GET_STATIC;
        } else {
          yield MessageType.EXEC_GET_FIELD;
        }
      }
      case "put" -> {
        if (jsonRpcRequest.getParams().getInstance() == null) {
          yield MessageType.EXEC_PUT_STATIC;
        } else {
          yield MessageType.EXEC_PUT_FIELD;
        }
      }
      case "meta" -> MessageType.META_MESSAGE_REQUEST;
      case "control" -> MessageType.CONTROL_MESSAGE_REQUEST;
      default ->
          throw new IllegalArgumentException("Unsupported method: " + jsonRpcRequest.getMethod());
    };
  }

  public static MessageType getMessageType(JsonRpcResponse jsonRpcResponse) {
    return getJsonRpcResponseType(jsonRpcResponse);
  }

  public static JsonRpcType getJsonRpcType(JsonRpcMessage jsonRpcMessage) {
    return getJsonRpcType(getMessageType(jsonRpcMessage));
  }

  public static JsonRpcType getJsonRpcType(MessageType messageType) {
    return switch (messageType) {
      case EXEC_CONSTRUCTOR,
              EXEC_INSTANCE_METHOD,
              EXEC_CLASS_METHOD,
              EXEC_GET_STATIC,
              EXEC_GET_FIELD,
              EXEC_PUT_STATIC,
              EXEC_PUT_FIELD ->
          JsonRpcType.REQUEST;
      case EXEC_PUT_STATIC_DONE, EXEC_PUT_FIELD_DONE, EXEC_RETURN_VALUE, EXEC_THROWABLE ->
          JsonRpcType.RESPONSE;
      default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
    };
  }

  private static MessageType getJsonRpcResponseType(JsonRpcResponse jsonRpcResponse) {
    if (jsonRpcResponse.getError() != null) {
      return MessageType.EXEC_THROWABLE;
    } else if (jsonRpcResponse.getResult() != null) {
      JsonRpcResponseReturnValue returnValue = jsonRpcResponse.getResult();
      Executable from = returnValue.getFrom();
      boolean isVoid = returnValue.getIsVoid();
      // a void, field operation can only be the result of a field put
      if (isVoid && from.getFieldName() != null && !from.getFieldName().isEmpty()) {
        int fieldModifiers = from.getModifiers();
        return Modifier.isStatic(fieldModifiers)
            ? MessageType.EXEC_PUT_STATIC_DONE
            : MessageType.EXEC_PUT_FIELD_DONE;
      } else {
        return MessageType.EXEC_RETURN_VALUE;
      }
    } else {
      throw new IllegalArgumentException("Unsupported JSON-RPC response type");
    }
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
