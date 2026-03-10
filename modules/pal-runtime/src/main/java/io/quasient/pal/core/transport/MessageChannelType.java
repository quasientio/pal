/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport;

/** Enumeration representing the type of messaging channel used to receive/dispatch a message. */
public enum MessageChannelType {
  /** Zmq Socket RPC channel. */
  ZMQ_SOCKET_RPC("ZMQ_SOCKET_RPC"),

  /** Websocket RPC channel. */
  WEBSOCKET_RPC("WEBSOCKET_RPC"),

  /** Log RPC channel (typically Kafka). */
  LOG_RPC("LOG_RPC"),

  /** CLI (typically for bootstrapping, by calling main or similar CLI utility). */
  CLI_RPC("CLI_RPC"),

  /**
   * Replay injection channel (used by {@link io.quasient.pal.core.replay.ReplayInputInjector} to
   * inject entry points during replay).
   */
  REPLAY_INJECTION("REPLAY_INJECTION");

  /** Name representing the message channel type. */
  final String name;

  /**
   * Constructs an MessageChannelType with a given name.
   *
   * @param name The name associated with the message channel type.
   */
  MessageChannelType(String name) {
    this.name = name;
  }

  /**
   * Returns the channel's name
   *
   * @return the channel name
   */
  public String getName() {
    return name;
  }
}
