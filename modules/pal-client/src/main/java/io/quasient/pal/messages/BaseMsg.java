/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages;

import org.zeromq.ZMQ;

/**
 * Abstract base class for PAL's internal ZMQ protocol messages.
 *
 * <p>This abstract class provides common properties and methods for message handling, including
 * sending messages through a ZeroMQ socket and tracking message size.
 */
public abstract class BaseMsg {

  /**
   * The size of the message in bytes.
   *
   * <p>Initialized to -1 to indicate that the size has not yet been determined.
   */
  protected int size = -1;

  /**
   * Sends the message through the specified ZeroMQ socket in a non-blocking manner.
   *
   * @param socket the ZeroMQ socket to send the message through
   * @return {@code true} if the message was sent successfully, {@code false} otherwise
   */
  public abstract boolean send(ZMQ.Socket socket);

  /**
   * Retrieves the size of the message in bytes.
   *
   * @return the size of the message, or {@code -1} if the size is not set
   */
  public int getSize() {
    return size;
  }
}
