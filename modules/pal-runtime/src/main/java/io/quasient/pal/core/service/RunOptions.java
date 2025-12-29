/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

/**
 * Enumerates the available runtime configuration options for the Pal runtime.
 *
 * <p>Each constant represents a specific feature that can be enabled to modify system behavior,
 * such as configuring directory connectivity or enabling communication interfaces.
 */
public enum RunOptions {

  /**
   * Enables connection to the PAL directory.
   *
   * <p>When activated, this option allows the system to interface with the PAL directory.
   */
  WITH_PALDIR,

  /**
   * Enables handling of BIN-RPC requests.
   *
   * <p>This option configures the system to listen for and process remote procedure call requests
   * over a ZMQ socket.
   */
  WITH_ZMQ_RPC,

  /**
   * Enables handling of JSON-RPC requests.
   *
   * <p>Activating this option allows the system to receive and process JSON-formatted RPC requests
   * over Websocket.
   */
  WITH_JSON_RPC,

  /**
   * Enables publishing messages via TCP.
   *
   * <p>When selected, this option configures the system to publish messages over a ZMQ TCP PUB
   * socket
   */
  WITH_TCP_PUB,

  /** Allows interception of messages. */
  WITH_INTERCEPTS,

  /**
   * Enables reading and dispatching messages from the configured source Log (event-sourcing,
   * WAL-replay).
   */
  WITH_SOURCE_LOG,

  /** Enables write-ahead publishing of messages to the Log. */
  WITH_WAL,

  /** Enables sessions - automatically set if any RPC interface is enabled */
  WITH_SESSIONS
}
