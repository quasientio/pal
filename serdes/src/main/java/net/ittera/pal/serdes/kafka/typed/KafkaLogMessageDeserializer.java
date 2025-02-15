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

package net.ittera.pal.serdes.kafka.typed;

import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes byte arrays from Kafka into {@link LogMessage} instances.
 *
 * <p>This deserializer converts Kafka message bytes into {@code LogMessage<?>} objects, handling
 * both standard and JSON-RPC message content types.
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
