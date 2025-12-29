/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.kafka.typed;

import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Serializer for Kafka message keys that maintains the original package and class name.
 *
 * <p>This allows Kafka properties to remain unchanged after Maven shading by relocating
 * dependencies while keeping the serializer's package and class intact.
 */
public final class KafkaKeySerializer extends StringSerializer {

  /** Constructs a new KafkaKeySerializer. */
  public KafkaKeySerializer() {}
}
