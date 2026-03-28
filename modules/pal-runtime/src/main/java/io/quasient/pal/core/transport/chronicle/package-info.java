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
 * Chronicle Queue-based log implementation for local, high-performance logging.
 *
 * <p>Chronicle Queue provides memory-mapped file-based message storage ideal for single-peer
 * deployments or local development without requiring Kafka infrastructure.
 *
 * <ul>
 *   <li>{@link ChronicleWalWriter} - Writes messages to Chronicle Queue
 *   <li>{@link ChronicleSourceLogReader} - Reads messages from Chronicle Queue
 *   <li>{@link ChronicleQueueFactory} - Creates Chronicle Queue instances
 * </ul>
 *
 * @see io.quasient.pal.core.transport.kafka Kafka alternative for distributed deployments
 */
package io.quasient.pal.core.transport.chronicle;
