/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
