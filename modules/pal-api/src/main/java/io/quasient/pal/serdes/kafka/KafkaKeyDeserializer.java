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

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Custom deserializer for Kafka keys that maintains the original fully qualified class name after
 * Maven shading. This allows Kafka properties to reference the deserializer without requiring
 * changes to the package name, ensuring compatibility and ease of configuration. See issue #168 for
 * more details.
 *
 * @see StringDeserializer
 */
public final class KafkaKeyDeserializer extends StringDeserializer {}
