/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.kafka;

import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Custom Kafka key serializer that extends {@link StringSerializer} to preserve package naming
 * after mvn-shading. This ensures Kafka properties can reference this serializer without requiring
 * changes to its package name, as only external dependencies are relocated during the shading
 * process. See issue #168 for more details.
 *
 * @see StringSerializer
 */
public final class KafkaKeySerializer extends StringSerializer {

  /** Constructs a new {@code KafkaKeySerializer}. */
  public KafkaKeySerializer() {}
}
