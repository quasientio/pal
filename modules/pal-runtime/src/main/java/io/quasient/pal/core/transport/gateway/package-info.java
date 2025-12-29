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
 * Outbound message gateway for routing messages to WAL and publish channels.
 *
 * <p>{@link OutboundMessageGateway} is the central routing point for all outgoing messages. It
 * handles:
 *
 * <ul>
 *   <li>Writing to write-ahead log (WAL)
 *   <li>Publishing to ZeroMQ PUB socket for subscribers
 *   <li>Backpressure management with configurable spin-wait strategies
 *   <li>Queue statistics and monitoring
 * </ul>
 */
package io.quasient.pal.core.transport.gateway;
