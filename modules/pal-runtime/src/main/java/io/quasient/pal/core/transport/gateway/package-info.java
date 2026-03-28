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
 * Outbound message gateway for routing messages to WAL and publish channels.
 *
 * <p>{@link OutboundMessageGateway} is the central routing point for all outgoing messages. It
 * handles:
 *
 * <ul>
 *   <li>Writing to write-ahead log (WAL)
 *   <li>Publishing to ZeroMQ PUB socket for subscribers
 *   <li>Backpressure management with configurable spin-wait strategies
 *   <li>Queue statistics and monitoring
 * </ul>
 */
package io.quasient.pal.core.transport.gateway;
