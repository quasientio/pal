/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.kafka.typed;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.Marshallable;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import io.quasient.pal.messages.types.MessageFormatType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import io.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes {@link LogMessage} instances for Kafka.
 *
 * <p>This serializer handles the conversion of {@link LogMessage} objects into byte arrays suitable
 * for Kafka. It supports the Binary (Colfer) and JSON (JSON-RPC) message formats. Depending on the
 * content, it serializes the message accordingly and sets appropriate headers for message format
 * and type. This serializer integrates with Kafka's serialization mechanism by implementing the
 * {@link Serializer} interface.
 */
public class KafkaLogMessageSerializer implements Serializer<LogMessage<?>> {

  /** Logger for logging events and errors. */
  private static final Logger logger = LoggerFactory.getLogger(KafkaLogMessageSerializer.class);

  /**
   * {@inheritDoc}
   *
   * <p>This serializer does not require any configuration.
   *
   * @param configs the configuration properties
   * @param isKey whether the serializer is for key or value
   */
  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    // No configuration needed for this serializer
  }

  /**
   * {@inheritDoc}
   *
   * <p>Serializes the given {@code logMessage} and delegates to the {@link #serialize(String,
   * Headers, LogMessage)} method with {@code headers} set to {@code null}.
   *
   * @param topic the Kafka topic associated with the serialized data
   * @param logMessage the log message to serialize
   * @return the serialized byte array, or an empty byte array if {@code logMessage} is {@code null}
   */
  @Override
  public byte[] serialize(String topic, LogMessage<?> logMessage) {
    // Use the serialize method with Headers when possible
    return serialize(topic, null, logMessage);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Serializes the provided {@code logMessage} into a byte array based on its content type.
   * Supports serialization of messages in {@link MessageFormatType#BINARY} (using Colfer) and
   * {@link MessageFormatType#JSON} (using JSON-RPC). Sets appropriate Kafka headers for message
   * format and type, and includes additional headers from the log message.
   *
   * <p>If the {@code headers} parameter is {@code null}, it initializes with an empty {@link
   * RecordHeaders} and logs an error.
   *
   * @param topic the Kafka topic associated with the serialized data
   * @param headers the Kafka record headers to include, or {@code null} to use default headers
   * @param logMessage the log message to serialize
   * @return the serialized byte array, or an empty byte array if {@code logMessage} is {@code null}
   *     or serialization fails
   * @throws IllegalArgumentException if the content type of {@code logMessage} is unsupported
   */
  @Override
  public byte[] serialize(String topic, Headers headers, LogMessage<?> logMessage) {
    if (logMessage == null) {
      return new byte[0];
    }

    Object content = logMessage.getContent();
    MessageFormatType messageFormat;
    byte messageType;

    // this is a hack to get the unit tests to pass since the MockProducer doesn't pass in the
    // headers
    if (headers == null) {
      logger.error(
          "Headers are null! Creating RecordHeaders to avoid NPE but they will not be persisted. If this is not a test, this is a bug.");
      headers = new RecordHeaders();
    }

    byte[] data;

    if (content instanceof Message message) {
      logger.debug("Serializing Message: {}", ColferUtils.toJson(message, true));
      messageFormat = MessageFormatType.BINARY;
      messageType = message.getMessageType();

      // Serialize using Colfer
      data = colferMessageToBytes(message);
    } else if (content instanceof JsonRpcMessage jsonRpcMessage) {
      logger.debug("Serializing json-rpc message: {}", jsonRpcMessage);
      messageFormat = MessageFormatType.JSON;
      messageType = JsonRpcMessageUtils.getMessageType(jsonRpcMessage).getId();

      // Serialize using Gson
      String json;
      try {
        json = JsonRpcSerializer.toJson(jsonRpcMessage);
      } catch (JsonSerializationException e) {
        logger.error(
            "Failed to serialize JsonRpcMessage of type {}",
            jsonRpcMessage.getClass().getSimpleName(),
            e);
        return new byte[0];
      }
      data = json.getBytes(StandardCharsets.UTF_8);
    } else {
      throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
    }

    // set log message headers in the kafka record headers
    headers.add("message-format", new byte[] {messageFormat.toByte()});
    headers.add("message-type", new byte[] {messageType});
    for (Map.Entry<String, String> entry : logMessage.getHeaders().entrySet()) {
      if (entry.getKey().endsWith("-id")) {
        // "-id" headers are byte-serialized UUIDs
        headers.add(entry.getKey(), UuidUtils.toBytes(entry.getValue()));
      } else {
        // All other headers are UTF-8 encoded strings
        headers.add(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
      }
    }

    return data;
  }

  /**
   * Serializes the given {@code Marshallable} message into a byte array using Colfer serialization.
   *
   * <p>If the {@code message} is {@code null}, returns an empty byte array. Otherwise, it
   * determines the required buffer size, marshals the message into the buffer, and returns the
   * resulting byte array trimmed to the actual data size.
   *
   * @param message the message to serialize
   * @return the serialized byte array, or an empty byte array if {@code message} is {@code null}
   */
  private static byte[] colferMessageToBytes(Marshallable message) {
    if (message == null) {
      return new byte[0];
    }

    final int maxSize = message.marshalFit();
    final byte[] buf = new byte[maxSize];
    final int finalIdx = message.marshal(buf, 0);
    if (finalIdx < maxSize) {
      byte[] trimmed = new byte[finalIdx];
      System.arraycopy(buf, 0, trimmed, 0, finalIdx);
      return trimmed;
    }
    return buf;
  }

  /**
   * {@inheritDoc}
   *
   * <p>No resources are held by this serializer.
   */
  @Override
  public void close() {
    // No resources to close
  }
}
