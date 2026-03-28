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
package io.quasient.pal.messages.jsonrpc;
