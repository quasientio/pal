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
 * PAL message types and their wire formats.
 *
 * <p>Messages are the core data structures that flow through PAL logs and RPC channels. They
 * represent operations (method calls, field accesses, constructor invocations) and control
 * commands.
 *
 * <p>Subpackages:
 *
 * <ul>
 *   <li>{@code colfer} - Binary message format using Colfer serialization (high performance)
 *   <li>{@code jsonrpc} - JSON-RPC 2.0 message format (human readable, debugging)
 *   <li>{@code types} - Enumeration types shared across message formats
 * </ul>
 *
 * @see LogMessage Wrapper for messages read from logs
 */
package io.quasient.pal.messages;
