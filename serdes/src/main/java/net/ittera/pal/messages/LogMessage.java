/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.messages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.JsonRpcType;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.JsonSerializers;
import net.ittera.pal.serdes.jsonrpc.JsonRpcParameterDeserializer;
import net.ittera.pal.serdes.jsonrpc.JsonRpcRequestDeserializer;
import net.ittera.pal.serdes.jsonrpc.JsonRpcResponseDeserializer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class LogMessage<T> {

  private final String topic;
  private Long offset;
  private final Map<String, String> headers;
  private final T content;
  private static final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(JsonRpcParameter.class, new JsonRpcParameterDeserializer())
          .registerTypeAdapter(JsonRpcRequest.class, new JsonRpcRequestDeserializer())
          .registerTypeAdapter(
              StaticFieldPutDone.class, new JsonSerializers.StaticFieldPutDoneAdapter())
          .registerTypeAdapter(
              InstanceFieldPutDone.class, new JsonSerializers.InstanceFieldPutDoneAdapter())
          .registerTypeAdapter(ReturnValue.class, new JsonSerializers.ReturnValueAdapter())
          .registerTypeAdapter(JsonRpcResponse.class, new JsonRpcResponseDeserializer())
          .create();

  public LogMessage(
      @Nullable String topic, @Nullable Long offset, Map<String, String> headers, T content) {
    if (!(content instanceof Message || content instanceof JsonRpcMessage)) {
      throw new IllegalArgumentException("content must be a Message or JsonRpcMessage");
    }
    this.topic = topic;
    this.offset = offset;
    this.headers = headers;
    this.content = content;
  }

  public static LogMessage<?> newInstance(
      @Nullable String topic, @Nullable Long offset, Headers recordHeaders, byte[] data) {
    if (recordHeaders == null) {
      throw new IllegalArgumentException("Record headers not found");
    }
    MessageFormatType messageFormat = getMessageFormatFromHeader(recordHeaders);
    if (messageFormat == null) {
      throw new IllegalArgumentException("Message format not found in record headers");
    }

    // create headers map
    Map<String, String> headers = new HashMap<>();
    headers.put("message-format", messageFormat.name());

    // add all string headers to headers map
    headers.putAll(getStringHeadersFromRecordHeaders(recordHeaders));

    // add all "-id" headers to headers map
    headers.putAll(getIdHeadersFromRecordHeaders(recordHeaders));

    LogMessage<?> logMessage;
    switch (messageFormat) {
      case COLFER -> {
        // deserialize
        Message message = new Message();
        message.unmarshal(data, 0);

        // get message type and set it in headers
        MessageType messageType = getColferMessageTypeFromHeader(recordHeaders);
        if (messageType != null) {
          headers.put("message-type", messageType.name());
        }
        logMessage = new LogMessage<>(topic, offset, headers, message);
      }
      case JSONRPC -> {
        JsonRpcType jsonRpcMessageType = getJsonRpcMessageTypeFromHeader(recordHeaders);
        if (jsonRpcMessageType == null) {
          throw new IllegalArgumentException("JSON-RPC message type not found in record headers");
        }
        headers.put("message-type", jsonRpcMessageType.name());

        switch (jsonRpcMessageType) {
          case REQUEST -> {
            JsonRpcRequest jsonRpcRequest =
                gson.fromJson(new String(data, StandardCharsets.UTF_8), JsonRpcRequest.class);
            logMessage = new LogMessage<>(topic, offset, headers, jsonRpcRequest);
          }
          case RESPONSE -> {
            JsonRpcResponse jsonRpcResponse =
                gson.fromJson(new String(data, StandardCharsets.UTF_8), JsonRpcResponse.class);
            logMessage = new LogMessage<>(topic, offset, headers, jsonRpcResponse);
          }
          default ->
              throw new IllegalArgumentException(
                  "Unsupported JSON-RPC message type: " + jsonRpcMessageType);
        }
      }
      default -> throw new IllegalArgumentException("Unsupported message format: " + messageFormat);
    }

    return logMessage;
  }

  private static MessageFormatType getMessageFormatFromHeader(Headers headers) {
    for (Header header : headers.headers("message-format")) {
      byte formatByte = header.value()[0];
      return MessageFormatType.fromByte(formatByte);
    }
    return null;
  }

  private static JsonRpcType getJsonRpcMessageTypeFromHeader(Headers headers) {
    for (Header header : headers.headers("message-type")) {
      byte typeByte = header.value()[0];
      return JsonRpcType.fromByte(typeByte);
    }
    return null;
  }

  private static MessageType getColferMessageTypeFromHeader(Headers headers) {
    for (Header header : headers.headers("message-type")) {
      byte typeByte = header.value()[0];
      return MessageType.fromByte(typeByte);
    }
    return null;
  }

  private static Map<String, String> getStringHeadersFromRecordHeaders(Headers recordHeaders) {
    Map<String, String> headers = new HashMap<>();
    for (Header header : recordHeaders) {
      if (header.value().length == 1) {
        // skip single byte headers (e.g. message-format, message-type)
        continue;
      }
      if (header.key().endsWith("-id")) {
        // skip id headers (byte-formatted with UuidUtils)
        continue;
      }

      headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
    }
    return headers;
  }

  private static Map<String, String> getIdHeadersFromRecordHeaders(Headers recordHeaders) {
    Map<String, String> headers = new HashMap<>();
    for (Header header : recordHeaders) {
      if (header.key().endsWith("-id")) {
        headers.put(header.key(), UuidUtils.fromBytes(header.value()).toString());
      }
    }
    return headers;
  }

  public String getTopic() {
    return topic;
  }

  public Long getOffset() {
    return offset;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public T getContent() {
    return content;
  }

  public void setOffset(Long offset) {
    this.offset = offset;
  }

  @Override
  public String toString() {
    return "LogMessage{"
        + "topic='"
        + topic
        + ",offset="
        + offset
        + ", headers="
        + headers
        + ", content="
        + content
        + '}';
  }
}
