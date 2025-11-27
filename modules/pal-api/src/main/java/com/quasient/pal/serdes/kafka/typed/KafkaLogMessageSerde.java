/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.kafka.typed;

import com.quasient.pal.messages.LogMessage;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A SerDe (Serializer/Deserializer) for {@link LogMessage} objects used with Apache Kafka.
 *
 * <p>This class provides serialization and deserialization functionality for {@code LogMessage<?>}
 * instances, enabling them to be transmitted through Kafka topics.
 */
public class KafkaLogMessageSerde implements Serde<LogMessage<?>> {

  /** Serializer instance for {@code LogMessage<?>} objects. */
  private final Serializer<LogMessage<?>> serializer;

  /** Deserializer instance for {@code LogMessage<?>} objects. */
  private final Deserializer<LogMessage<?>> deserializer;

  /**
   * Constructs a {@code KafkaLogMessageSerde} with default serializer and deserializer
   * implementations.
   */
  public KafkaLogMessageSerde() {
    this.serializer = new KafkaLogMessageSerializer();
    this.deserializer = new KafkaLogMessageDeserializer();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the serializer for {@code LogMessage<?>}.
   *
   * @return the serializer instance used for serializing {@code LogMessage<?>} objects.
   */
  @Override
  public Serializer<LogMessage<?>> serializer() {
    return serializer;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the deserializer for {@code LogMessage<?>}.
   *
   * @return the deserializer instance used for deserializing {@code LogMessage<?>} objects.
   */
  @Override
  public Deserializer<LogMessage<?>> deserializer() {
    return deserializer;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Configures the SerDe with the provided configuration.
   *
   * <p>This method configures both the serializer and deserializer with the given settings.
   *
   * @param configs the configuration properties.
   * @param isKey whether the SerDe is for key or value.
   */
  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    serializer.configure(configs, isKey);
    deserializer.configure(configs, isKey);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the SerDe and releases any held resources.
   *
   * <p>This method closes both the serializer and deserializer to clean up resources.
   */
  @Override
  public void close() {
    serializer.close();
    deserializer.close();
  }
}
