package net.ittera.pal.serdes.jsonrpc;

import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.getClassName;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.getFieldName;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.getMessageType;

import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.ResponseObject;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.RpcMessageSummaryUtil;

public class JsonRpcMessageSummaryUtil extends RpcMessageSummaryUtil {

  // Helper method to get the class name based on the message type
  private static String classname(JsonRpcMessage msg) {
    return shortClassname(getClassName(msg).orElse(""));
  }

  public static String getOneLinerSummary(JsonRpcMessage msg) {
    if (msg instanceof JsonRpcRequest request) {
      return getOneLinerSummary(request);
    } else if (msg instanceof JsonRpcResponse response) {
      return getOneLinerSummary(response);
    }
    throw new IllegalArgumentException("Unsupported message type: " + msg.getClass().getName());
  }

  public static String getOneLinerSummary(JsonRpcRequest msg) {
    MessageType messageType = getMessageType(msg);
    return switch (messageType) {
      case EXEC_CONSTRUCTOR -> "new " + classname(msg);
      case EXEC_INSTANCE_METHOD ->
          String.format(
              "call %s.%s@%s",
              classname(msg), msg.getParams().getMethod(), msg.getParams().getInstance());
      case EXEC_CLASS_METHOD ->
          String.format("call %s.%s", classname(msg), msg.getParams().getMethod());
      case EXEC_GET_STATIC ->
          String.format("get %s.%s", classname(msg), msg.getParams().getField());
      case EXEC_GET_FIELD ->
          String.format(
              "get %s.%s@%s",
              classname(msg), msg.getParams().getField(), msg.getParams().getInstance());
      case EXEC_PUT_STATIC -> {
        assert msg.getParams().getValue() != null;
        yield String.format(
            "put %s.%s ⇦ %s",
            classname(msg), msg.getParams().getField(), getObjRepr(msg.getParams().getValue()));
      }
      case EXEC_PUT_FIELD -> {
        assert msg.getParams().getValue() != null;
        yield String.format(
            "put %s.%s@%s ⇦ %s",
            classname(msg),
            msg.getParams().getField(),
            msg.getParams().getInstance(),
            getObjRepr(msg.getParams().getValue()));
      }
      default -> throw new IllegalArgumentException("Unsupported request type: " + messageType);
    };
  }

  public static String getOneLinerSummary(JsonRpcResponse msg) {
    MessageType responseType = getMessageType(msg);
    switch (responseType) {
      case EXEC_PUT_STATIC_DONE:
      case EXEC_PUT_FIELD_DONE:
        return String.format("put_done %s.%s", classname(msg), getFieldName(msg).orElse(""));
      case EXEC_RETURN_VALUE:
        assert msg.getResult() != null;
        if (msg.getResult().getIsVoid()) {
          return "return void";
        } else {
          assert msg.getResult().getValue() != null;
          if (msg.getResult().getFrom().isConstructor()) {
            return String.format(
                "return new %s%s", classname(msg), getObjRepr(msg.getResult().getValue()));
          } else if (msg.getResult().getFrom().getFieldName() != null) {
            String fieldName = msg.getResult().getFrom().getFieldName();
            if (fieldName != null && !fieldName.isEmpty()) {
              fieldName = "(" + fieldName + ")";
            }
            return String.format(
                    "return %s%s (%s)",
                    classname(msg),
                    getObjRepr(msg.getResult().getValue()),
                    fieldName != null ? fieldName : "")
                .trim();
          }
          // default (return value from method)
          return String.format(
              "return %s%s", classname(msg), getObjRepr(msg.getResult().getValue()));
        }
      case EXEC_THROWABLE:
        String message =
            msg.getError() == null
                ? ""
                : msg.getError().getData() == null ? "" : msg.getError().getData().getMessage();
        return String.format("throw %s: \"%s\"", classname(msg), message);
      default:
        throw new IllegalArgumentException("Unsupported response type: " + responseType);
    }
  }

  private static String getObjRepr(ResponseObject obj) {
    return getObjRepr(obj.isNull(), obj.getValue(), String.valueOf(obj.getRef()));
  }

  private static String getObjRepr(Argument argument) {
    String argValue = argument.getValue() == null ? null : argument.getValue().toString();
    return getObjRepr(argument.isNull(), argValue, String.valueOf(argument.getRef()));
  }
}
