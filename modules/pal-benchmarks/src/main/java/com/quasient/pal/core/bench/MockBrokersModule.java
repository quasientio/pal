/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.quasient.pal.core.transport.kafka.ProducerFactory;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * Guice wiring module for running the benchmark in {@link IoProfile#MOCK}.
 */
public final class MockBrokersModule extends AbstractModule {

  /**
   * Configures dependency injection.
   */
  @Override protected void configure() {
    bind(MessagePublisher.class).to(DummyMessagePublisher.class).in(Singleton.class);

    /*
    // turn off ZMQ offset publishing by KafkaWalWriter
    bind(Boolean.class)
            .annotatedWith(com.google.inject.name.Names.named("publishOffsets"))
            .toInstance(false);
    */
  }

  /**
   * Provides a {@link ProducerFactory} that returns a mock producer
   * for {@link IoProfile#MOCK} runs, which measure CPU, not the broker/network.
   *
   * @return an instance of {@link MockProducer}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  ProducerFactory mockFactory() {
    return props -> new MockProducer<>(
            true,                      // auto-complete futures
            new StringSerializer(),
            new ByteArraySerializer());
  }
}
