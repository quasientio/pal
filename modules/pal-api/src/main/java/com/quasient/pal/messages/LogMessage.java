/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.types.JsonRpcType;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Encapsulates a log message with associated metadata and content retrieved from a log backend
 * (either Kafka topic or Chronicle queue). The content can be either a binary {@link Message} or a
 * {@link JsonRpcMessage}.
 *
 * @param <T> the type of the message content
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Message wrapper - headers map is intentionally shared")
public final class LogMessage<T> {

  /** The type of log backend from which the message was retrieved. */
  public enum LogBackendType {
    /** Message retrieved from a Kafka topic. */
    KAFKA,
    /** Message retrieved from a Chronicle queue. */
    CHRONICLE
  }

  /** The type of log backend from which this message was retrieved. */
  private final LogBackendType backendType;

  /** The Kafka topic from which the message was consumed. Null for Chronicle messages. */
  private final String topic;

  /**
   * The offset/index of the message within the log backend. For Kafka: the partition offset. For
   * Chronicle: the queue index.
   */
  private Long offset;

  /** The headers associated with the message, represented as key-value pairs. */
  private final Map<String, String> headers;

  /**
   * The content of the log message, which is either a {@link Message} or a {@link JsonRpcMessage}.
   */
  private final T content;

  /**
   * Constructs a new {@code LogMessage} with the specified topic, offset, headers, and content.
   * This constructor is used for Kafka-based messages.
   *
   * @param topic the Kafka topic from which the message was consumed; may be {@code null}
   * @param offset the offset of the message within the topic; may be {@code null}
   * @param headers the headers associated with the message; must not be {@code null}
   * @param content the content of the message, which must be an instance of {@link Message} or
   *     {@link JsonRpcMessage}
   * @throws IllegalArgumentException if {@code content} is not a {@link Message} or {@link
   *     JsonRpcMessage}
   */
  public LogMessage(
      @Nullable String topic, @Nullable Long offset, Map<String, String> headers, T content) {
    if (!(content instanceof Message || content instanceof JsonRpcMessage)) {
      throw new IllegalArgumentException("content must be a Message or JsonRpcMessage");
    }
    this.backendType = LogBackendType.KAFKA;
    this.topic = topic;
    this.offset = offset;
    this.headers = headers;
    this.content = content;
  }

  /**
   * Constructs a new {@code LogMessage} for Chronicle-based messages without Kafka-specific fields.
   *
   * <p>This constructor is specifically for messages read from Chronicle queues, which don't have a
   * topic concept. The offset represents the Chronicle queue index.
   *
   * @param offset the Chronicle queue index; may be {@code null}
   * @param headers the headers associated with the message; must not be {@code null}
   * @param content the content of the message, which must be an instance of {@link Message} or
   *     {@link JsonRpcMessage}
   * @throws IllegalArgumentException if {@code content} is not a {@link Message} or {@link
   *     JsonRpcMessage}
   */
  public LogMessage(@Nullable Long offset, Map<String, String> headers, T content) {
    if (!(content instanceof Message || content instanceof JsonRpcMessage)) {
      throw new IllegalArgumentException("content must be a Message or JsonRpcMessage");
    }
    this.backendType = LogBackendType.CHRONICLE;
    this.topic = null; // Chronicle messages don't have topics
    this.offset = offset;
    this.headers = headers;
    this.content = content;
  }

  /**
   * Creates a {@code LogMessage} by deserializing the provided data. This method is utilized by
   * {@link KafkaLogMessageDeserializer} to convert raw Kafka records into {@code LogMessage}
   * instances.
   *
   * @param topic the Kafka topic (i.e. log) from which the message was read; may be {@code null}
   * @param offset the offset of the message within the topic; may be {@code null}
   * @param recordHeaders the headers of the Kafka record
   * @param data the raw byte array representing the message content
   * @return the deserialized {@code LogMessage} instance
   * @throws IllegalArgumentException if required headers are missing or the message format/type is
   *     unsupported
   * @throws RuntimeException if JSON deserialization fails
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

  /**
   * Retrieves the {@link MessageFormatType} from the provided headers.
   *
   * @param headers the headers from which to extract the message format
   * @return the corresponding {@code MessageFormatType}, or {@code null} if not found
   */
  private static MessageFormatType getMessageFormatFromHeader(Headers headers) {
    for (Header header : headers.headers("message-format")) {
      byte formatByte = header.value()[0];
      return MessageFormatType.fromByte(formatByte);
    }
    return null;
  }

  /**
   * Retrieves the {@link MessageType} from the provided headers.
   *
   * @param headers the headers from which to extract the message type
   * @return the corresponding {@code MessageType}, or {@code null} if not found
   */
  private static MessageType getMessageTypeFromHeader(Headers headers) {
    for (Header header : headers.headers("message-type")) {
      int typeId = header.value()[0] & 0xFF; // treat as unsigned
      return MessageType.fromId((byte) typeId);
    }
    return null;
  }

  /**
   * Extracts string-based headers from the provided Kafka record headers. Skips headers that are
   * single-byte or end with "-id".
   *
   * @param recordHeaders the Kafka record headers
   * @return a map of header keys to their string values
   */
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

  /**
   * Extracts ID-based headers from the provided Kafka record headers. Only includes headers whose
   * keys end with "-id" and converts their byte values to UUID strings.
   *
   * @param recordHeaders the Kafka record headers
   * @return a map of header keys to their UUID string representations
   */
  private static Map<String, String> getIdHeadersFromRecordHeaders(Headers recordHeaders) {
    Map<String, String> headers = new HashMap<>();
    for (Header header : recordHeaders) {
      if (header.key().endsWith("-id")) {
        headers.put(header.key(), UuidUtils.fromBytes(header.value()).toString());
      }
    }
    return headers;
  }

  /**
   * Retrieves the log backend type for this message.
   *
   * @return the backend type (KAFKA or CHRONICLE)
   */
  public LogBackendType getBackendType() {
    return backendType;
  }

  /**
   * Retrieves the Kafka topic associated with this log message.
   *
   * @return the Kafka topic, or {@code null} if not specified or if this is a Chronicle message
   */
  public String getTopic() {
    return topic;
  }

  /**
   * Retrieves the offset of this log message within the log backend. For Kafka: the partition
   * offset. For Chronicle: the queue index.
   *
   * @return the offset/index, or {@code null} if not specified
   */
  public Long getOffset() {
    return offset;
  }

  /**
   * Retrieves the headers associated with this log message.
   *
   * @return an unmodifiable map of header keys to values
   */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /**
   * Retrieves the content of this log message.
   *
   * @return the message content
   */
  public T getContent() {
    return content;
  }

  /**
   * Updates the offset of this log message.
   *
   * @param offset the new offset value
   */
  public void setOffset(Long offset) {
    this.offset = offset;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "LogMessage{"
        + "backendType="
        + backendType
        + ", topic='"
        + topic
        + "', offset="
        + offset
        + ", headers="
        + headers
        + ", content="
        + content
        + '}';
  }
}
