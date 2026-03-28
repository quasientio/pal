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
