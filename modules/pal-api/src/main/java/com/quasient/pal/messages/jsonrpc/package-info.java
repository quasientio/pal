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
 * JSON-RPC 2.0 message format for PAL operations.
 *
 * <p>This format is used for human-readable message transport and debugging. While less efficient
 * than the binary Colfer format, JSON-RPC messages are easier to inspect and integrate with
 * external tools.
 *
 * <p>Key classes:
 *
 * <ul>
 *   <li>{@link JsonRpcRequest} - RPC request with method and parameters
 *   <li>{@link JsonRpcResponse} - RPC response with result or error
 *   <li>{@link JsonRpcError} - Error details following JSON-RPC 2.0 spec
 * </ul>
 */
package com.quasient.pal.messages.jsonrpc;
