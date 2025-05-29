/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

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
   * <p>This option configures the system to listen for and process remote procedure call requests.
   */
  WITH_RPC,

  /**
   * Enables handling of JSON-RPC requests.
   *
   * <p>Activating this option allows the system to receive and process JSON-formatted RPC requests
   * over Websocket.
   */
  WITH_JSONRPC,

  /**
   * Enables publishing messages via TCP.
   *
   * <p>When selected, this option configures the system to publish messages over a ZMQ PUB socket
   * (TCP)
   */
  WITH_TCP_PUB,

  /**
   * Allows interception of messages.
   *
   * <p>This option permits the system to inspect or modify messages during transmission, which is
   * useful for debugging, monitoring, or applying custom processing.
   */
  WITH_INTERCEPTS,

  /** Enables reading and dispatching messages from the Log (i.e. event-sourcing). */
  WITH_IN_LOG,

  /** Enables write-ahead publishing of messages to the Log. */
  WITH_OUT_LOG,
}
