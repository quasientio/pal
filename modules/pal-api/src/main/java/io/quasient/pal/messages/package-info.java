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
