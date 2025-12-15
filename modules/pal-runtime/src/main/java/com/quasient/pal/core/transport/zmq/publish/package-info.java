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
 * ZeroMQ PUB-SUB message publishing for real-time message streaming.
 *
 * <p>{@link MessagePublisher} publishes messages to subscribers via a ZeroMQ PUB socket. This
 * enables external tools to observe message flow in real-time.
 *
 * <ul>
 *   <li>{@link MessagePublisher} - Publishes messages with configurable drop policies
 *   <li>{@link PublishingDropPolicy} - Strategy for handling queue congestion
 *   <li>{@link MessagePublisherConfig} - Publisher configuration
 * </ul>
 */
package com.quasient.pal.core.transport.zmq.publish;
