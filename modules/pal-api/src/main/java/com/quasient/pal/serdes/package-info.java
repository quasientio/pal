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
 * Serialization and deserialization utilities for PAL messages.
 *
 * <p>Subpackages provide format-specific implementations:
 *
 * <ul>
 *   <li>{@code colfer} - Binary serialization using Colfer codec (high performance, compact)
 *   <li>{@code jsonrpc} - JSON serialization for JSON-RPC messages
 * </ul>
 */
package com.quasient.pal.serdes;
