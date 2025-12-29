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
 * Internal message types for communication between runtime components.
 *
 * <p>These messages are used for internal routing and coordination, not for external wire protocol.
 *
 * <ul>
 *   <li>{@link InboundLogMsg} - Messages read from write-ahead logs
 *   <li>{@link InboundJsonRpcRequestMsg} - Incoming JSON-RPC requests
 *   <li>{@link OutboundJsonRpcResponseMsg} - Outgoing JSON-RPC responses
 *   <li>{@link InterceptEventMsg} - Intercept registration events
 *   <li>{@link SessionCommandMsg} - Session control commands
 * </ul>
 */
package io.quasient.pal.core.internal.messages;
