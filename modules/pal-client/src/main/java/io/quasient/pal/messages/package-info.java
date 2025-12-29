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
 * Client-side message handling utilities.
 *
 * <p>Provides classes for constructing, streaming, and processing PAL messages from a client
 * perspective:
 *
 * <ul>
 *   <li>{@link MessageStreamer} - Stream messages from logs
 *   <li>{@link OutboundMsg} - Builder for outgoing messages
 *   <li>{@link MessageContext} - Message metadata context
 * </ul>
 */
package io.quasient.pal.messages;
