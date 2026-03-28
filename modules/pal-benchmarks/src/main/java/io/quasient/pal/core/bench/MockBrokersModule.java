/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.bench;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.quasient.pal.core.bench.dummies.DrainingMockProducer;
import io.quasient.pal.core.transport.kafka.ProducerFactory;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Guice wiring module for running the benchmark in {@link IoProfile#MOCK}. */
public final class MockBrokersModule extends AbstractModule {

  /** Configures dependency injection. */
  @Override
  protected void configure() {
    bind(MessagePublisher.class).to(DummyMessagePublisher.class).in(Singleton.class);
  }

  /**
   * Provides a {@link ProducerFactory} that returns a mock producer for {@link IoProfile#MOCK}
   * runs, which measure CPU, not the broker/network.
   *
   * @return an instance of {@link MockProducer}
   */
  @SuppressWarnings("unused")
  @Provides
  @Singleton
  ProducerFactory mockFactory() {
    return props -> new DrainingMockProducer<>(new StringSerializer(), new ByteArraySerializer());
  }
}
