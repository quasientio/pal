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
 * JSON-RPC implementation of the PAL RPC DSL.
 *
 * <p>Provides a fluent API for building and executing JSON-RPC call chains:
 *
 * <ul>
 *   <li>{@link RpcChain} - Builder for constructing RPC sequences
 *   <li>{@link RpcChainInstance} - Executable chain instance
 *   <li>{@link RpcChainResult} - Result of chain execution
 *   <li>{@link DeferredOperation} - Lazy operation for deferred execution
 * </ul>
 */
package com.quasient.pal.dsl.jsonrpc;
