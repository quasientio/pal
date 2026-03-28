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
