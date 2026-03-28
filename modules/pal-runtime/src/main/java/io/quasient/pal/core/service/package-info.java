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
 * Peer lifecycle management, configuration, and dependency injection.
 *
 * <p>{@link Main} is the entry point for starting a PAL peer. It orchestrates:
 *
 * <ol>
 *   <li>Logging configuration
 *   <li>Property loading and validation
 *   <li>Guice dependency injection setup via {@link PeerWiring}
 *   <li>Service startup (ZMQ sockets, log readers, intercept matcher)
 *   <li>Application main method invocation
 * </ol>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link Main} - Peer entry point and initialization orchestrator
 *   <li>{@link PeerWiring} - Guice module defining component bindings
 *   <li>{@link ConnectedService} - Base class for services with ZMQ lifecycle
 *   <li>{@link PeerException} - Fatal error codes and exception handling
 *   <li>{@link RunOptions} - Runtime configuration flags
 * </ul>
 */
package io.quasient.pal.core.service;
