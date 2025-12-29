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
