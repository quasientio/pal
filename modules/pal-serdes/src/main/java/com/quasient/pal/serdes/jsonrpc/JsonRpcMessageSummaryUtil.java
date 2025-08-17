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

import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.getClassName;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.getFieldName;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.getMessageType;

import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.RpcMessageSummaryUtil;

/**
 * Utility class for generating concise one-line summaries of JSON-RPC messages.
 *
 * <p>This class provides methods to create human-readable summaries for the various types of
 * JSON-RPC messages, including requests and responses. It extends {@link RpcMessageSummaryUtil} to
 * leverage common summarization functionality while adding JSON-RPC specific implementations.
 */
public class JsonRpcMessageSummaryUtil extends RpcMessageSummaryUtil {

  /**
   * Retrieves the short class name associated with the given JSON-RPC message.
   *
   * @param msg the JSON-RPC message from which to extract the class name
   * @return the short class name, or an empty string if not available
   */
  private static String classname(JsonRpcMessage msg) {
    return shortClassname(getClassName(msg).orElse(""));
  }

  /**
   * Generates a one-line summary for the specified JSON-RPC message.
   *
   * <p>This method determines the type of the provided message and delegates to the appropriate
   * summarization method. It supports both JSON-RPC requests and responses.
   *
   * @param msg the JSON-RPC message to summarize
   * @return a concise one-line summary of the message
   * @throws IllegalArgumentException if the message type is unsupported
   */
  public static String getOneLinerSummary(JsonRpcMessage msg) {
    if (msg instanceof JsonRpcRequest request) {
      return getOneLinerSummary(request);
    } else if (msg instanceof JsonRpcResponse response) {
      return getOneLinerSummary(response);
    }
    throw new IllegalArgumentException("Unsupported message type: " + msg.getClass().getName());
  }

  /**
   * Generates a one-line summary for the specified JSON-RPC request.
   *
   * <p>The summary is based on the request's message type, detailing the operation such as
   * constructor execution, method invocation, field retrieval, or value assignment.
   *
   * @param msg the JSON-RPC request to summarize
   * @return a concise one-line summary of the request
   * @throws IllegalArgumentException if the request's message type is unsupported
   */
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

  /**
   * Generates a one-line summary for the specified JSON-RPC response.
   *
   * <p>The summary is based on the response's message type, detailing the outcome such as value
   * return, field assignment completion, or exception throwing.
   *
   * @param msg the JSON-RPC response to summarize
   * @return a concise one-line summary of the response
   * @throws IllegalArgumentException if the response type is unsupported
   */
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

  /**
   * Constructs a string representation of the given response object.
   *
   * @param obj the response object to represent
   * @return a string representation of the response object
   */
  private static String getObjRepr(ResponseObject obj) {
    return getObjRepr(obj.isNull(), obj.getValue(), obj.getRef() != null ? obj.getRef() : 0);
  }

  /**
   * Constructs a string representation of the given argument.
   *
   * @param argument the argument to represent
   * @return a string representation of the argument
   */
  private static String getObjRepr(Argument argument) {
    String argValue = argument.getValue() == null ? null : argument.getValue().toString();
    return getObjRepr(
        argument.isNull(), argValue, argument.getRef() != null ? argument.getRef() : 0);
  }
}
