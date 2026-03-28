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
