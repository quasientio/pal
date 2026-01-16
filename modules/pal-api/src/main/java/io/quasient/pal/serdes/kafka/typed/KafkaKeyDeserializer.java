/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.kafka.typed;

import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Custom key deserializer for Kafka that maintains the original package and class name after Maven
 * shading.
 *
 * <p>This deserializer extends {@link StringDeserializer} to allow Kafka properties to reference it
 * directly without requiring package name changes. Only dependencies are relocated during shading,
 * ensuring that the deserializer remains in its original location.
 *
 * @see StringDeserializer
 */
public final class KafkaKeyDeserializer extends StringDeserializer {}
