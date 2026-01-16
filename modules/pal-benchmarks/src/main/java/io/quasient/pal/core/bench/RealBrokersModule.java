/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.quasient.pal.core.transport.kafka.ProducerFactory;
import org.apache.kafka.clients.producer.KafkaProducer;

/** Guice wiring module for benchmark runs in {@link IoProfile#REAL} mode. */
public final class RealBrokersModule extends AbstractModule {

  /**
   * Provides a {@link ProducerFactory} that returns a real Kafka Producer.
   *
   * @return an instance of {@link KafkaProducer}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  ProducerFactory realFactory() {
    return KafkaProducer::new; // real network client
  }
}
