/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.kafka;

import org.apache.kafka.common.serialization.ByteArraySerializer;

/** Serializer for Kafka messages that handles byte array serialization. */
public final class KafkaMessageSerializer extends ByteArraySerializer {

  /** Constructs a new {@code KafkaMessageSerializer}. */
  public KafkaMessageSerializer() {}
}
