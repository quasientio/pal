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
/**
 * Kafka-based log implementation for distributed PAL deployments.
 *
 * <p>Kafka provides durable, distributed message storage suitable for multi-peer systems. Messages
 * are stored in Kafka topics with configurable retention and partitioning.
 *
 * <ul>
 *   <li>{@link KafkaWalWriter} - Writes messages to Kafka topics
 *   <li>{@link KafkaSourceLogReader} - Reads messages from Kafka topics
 *   <li>{@link LogConfigurator} - Manages Kafka topic configuration
 * </ul>
 *
 * @see io.quasient.pal.core.transport.chronicle Local alternative for single-peer deployments
 */
package io.quasient.pal.core.transport.kafka;
