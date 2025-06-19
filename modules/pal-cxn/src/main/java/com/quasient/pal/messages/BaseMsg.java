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

package com.quasient.pal.messages;

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
