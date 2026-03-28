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

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.ColferUtils;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes byte arrays from Kafka into {@link LogMessage} instances.
 *
 * <p>This deserializer converts Kafka message bytes into {@code LogMessage<?>} objects, handling
 * both Binary (colfer) RPC and JSON-RPC message content types.
 */
public class KafkaLogMessageDeserializer implements Deserializer<LogMessage<?>> {

  /** Logger instance for logging deserialization events and errors. */
  private static final Logger logger = LoggerFactory.getLogger(KafkaLogMessageDeserializer.class);

  /** Constructs a new {@code KafkaLogMessageDeserializer}. */
  public KafkaLogMessageDeserializer() {}

  /**
   * Deserializes a byte array into a {@code LogMessage<?>} for the given topic.
   *
   * @param topic the Kafka topic from which the data was received
   * @param data the serialized bytes of the {@code LogMessage}
   * @return the deserialized {@code LogMessage<?>}, or {@code null} if {@code data} is {@code null}
   */
  @Override
  public LogMessage<?> deserialize(String topic, byte[] data) {
    return deserialize(topic, null, data);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Deserializes a byte array into a {@code LogMessage<?>} for the given topic and headers.
   *
   * @param topic the Kafka topic from which the data was received
   * @param recordHeaders the headers associated with the Kafka record
   * @param data the serialized bytes of the {@code LogMessage}
   * @return the deserialized {@code LogMessage<?>}, or {@code null} if {@code data} is {@code null}
   * @throws IllegalArgumentException if deserialization fails due to invalid data
   */
  @Override
  public LogMessage<?> deserialize(String topic, Headers recordHeaders, byte[] data)
      throws IllegalArgumentException {
    if (data == null) {
      logger.error("Record data is null. Returning null LogMessage.");
      return null;
    }

    LogMessage<?> logMessage = LogMessage.newInstance(topic, null, recordHeaders, data);
    if (logger.isDebugEnabled()) {
      String contentAsString;
      if (logMessage.getContent() instanceof Message) {
        contentAsString = ColferUtils.toJson((Message) logMessage.getContent(), true);
      } else { // JsonRpcMessage
        contentAsString = logMessage.getContent().toString();
      }
      logger.debug(
          "Deserialized LogMessage with topic: {}, offset: {}, headers:{}, content: {}",
          logMessage.getTopic(),
          logMessage.getOffset(),
          logMessage.getHeaders(),
          contentAsString);
    }
    return logMessage;
  }
}
