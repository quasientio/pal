/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.kafka;

import java.util.Properties;
import org.apache.kafka.clients.producer.Producer;

/**
 * Functional interface which abstracts away the creation of a concrete Kafka Producer, allowing for
 * easily injecting mock and real producer.
 */
@FunctionalInterface
public interface ProducerFactory {

  /**
   * Create the Kafka Producer from the given props, already containing bootstrap.servers,
   * linger.ms, etc.
   */
  Producer<String, byte[]> create(Properties props);
}
