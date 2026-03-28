/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
package io.quasient.pal.dsl.jsonrpc;
