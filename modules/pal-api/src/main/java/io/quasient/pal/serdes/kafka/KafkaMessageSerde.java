/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.kafka;

import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Provides a serializer and deserializer for byte arrays to be used with Kafka.
 *
 * <p>This serde delegates the serialization and deserialization processes to {@link
 * KafkaMessageSerializer} and {@link KafkaMessageDeserializer} respectively.
 */
public class KafkaMessageSerde implements Serde<byte[]> {

  /** The internal serde that handles the actual serialization and deserialization logic. */
  private final Serde<byte[]> inner;

  /**
   * Constructs a new {@code KafkaMessageSerde} with default {@code KafkaMessageSerializer} and
   * {@code KafkaMessageDeserializer}.
   */
  public KafkaMessageSerde() {
    inner = Serdes.serdeFrom(new KafkaMessageSerializer(), new KafkaMessageDeserializer());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Configures the serializer and deserializer with the given configuration.
   *
   * @param map the configuration settings
   * @param b indicates whether this serde is for keys
   */
  @Override
  public void configure(Map<String, ?> map, boolean b) {
    inner.serializer().configure(map, b);
    inner.deserializer().configure(map, b);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the serializer and deserializer, releasing any held resources.
   */
  @Override
  public void close() {
    inner.serializer().close();
    inner.deserializer().close();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the serializer for byte arrays.
   *
   * @return the byte array serializer
   */
  @Override
  public Serializer<byte[]> serializer() {
    return inner.serializer();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the deserializer for byte arrays.
   *
   * @return the byte array deserializer
   */
  @Override
  public Deserializer<byte[]> deserializer() {
    return inner.deserializer();
  }
}
