/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
