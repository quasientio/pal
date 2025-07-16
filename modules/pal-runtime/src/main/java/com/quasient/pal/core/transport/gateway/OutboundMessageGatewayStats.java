/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.gateway;

/**
 * Record of OutboundMessageGateway run stats.
 *
 * @param messagesDroppedPub number of messages to be published that were dropped due to queue
 *     congestion (aggregate of all threads).
 */
public record OutboundMessageGatewayStats(long messagesDroppedPub) {}
