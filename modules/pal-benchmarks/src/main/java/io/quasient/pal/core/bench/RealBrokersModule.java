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
