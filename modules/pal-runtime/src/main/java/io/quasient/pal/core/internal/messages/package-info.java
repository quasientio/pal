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
