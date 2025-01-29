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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.JsonRpcType;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import net.ittera.pal.serdes.jsonrpc.JsonRpcSerializer;
import net.ittera.pal.serdes.jsonrpc.JsonSerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public class LogMessage<T> {

  private final String topic;
  private Long offset;
  private final Map<String, String> headers;
  private final T content;

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

  /**
   * Creates a LogMessage by deserializing the byte[] data. Used by KafkaLogMessageDeserializer.
   *
   * @param topic the topic/Log where the message has been read from
   * @param offset the offset of the message
   * @param recordHeaders the headers of the message
   * @param data the data of the message
   * @return the deserialized LogMessage
   */
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

    // get message type and set it in headers
    MessageType messageType = getMessageTypeFromHeader(recordHeaders);
    if (messageType == null) {
      throw new IllegalArgumentException("Message type not found in record headers");
    }
    headers.put("message-type", messageType.name());

    LogMessage<?> logMessage;
    switch (messageFormat) {
      case BINARY -> {
        // deserialize
        Message message = new Message();
        message.unmarshal(data, 0);
        logMessage = new LogMessage<>(topic, offset, headers, message);
      }
      case JSON -> {
        JsonRpcType jsonRpcMessageType = JsonRpcMessageUtils.getJsonRpcType(messageType);
        if (jsonRpcMessageType == null) {
          throw new IllegalArgumentException("JSON-RPC message type not found in record headers");
        }
        switch (jsonRpcMessageType) {
          case REQUEST -> {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonRpcRequest jsonRpcRequest;
            try {
              jsonRpcRequest = JsonRpcSerializer.fromJson(json, JsonRpcRequest.class);
            } catch (JsonSerializationException e) {
              throw new RuntimeException(e);
            }
            logMessage = new LogMessage<>(topic, offset, headers, jsonRpcRequest);
          }
          case RESPONSE -> {
            String json = new String(data, StandardCharsets.UTF_8);
            JsonRpcResponse jsonRpcResponse;
            try {
              jsonRpcResponse = JsonRpcSerializer.fromJson(json, JsonRpcResponse.class);
            } catch (JsonSerializationException e) {
              throw new RuntimeException(e);
            }
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

  private static MessageType getMessageTypeFromHeader(Headers headers) {
    for (Header header : headers.headers("message-type")) {
      byte typeByte = header.value()[0];
      return MessageType.fromId(typeByte);
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
