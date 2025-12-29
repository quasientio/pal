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
 * Enumeration types for message classification and routing.
 *
 * <p>These enums define the categories and subtypes of messages that flow through PAL:
 *
 * <ul>
 *   <li>{@link MessageType} - Top-level message classification (EXEC, CONTROL, META, etc.)
 *   <li>{@link MessageFormatType} - Wire format (BINARY or JSON)
 *   <li>{@link RpcType} - RPC direction (REQUEST, RESPONSE)
 *   <li>{@link ControlCommandType} - Control plane commands
 * </ul>
 */
package io.quasient.pal.messages.types;
