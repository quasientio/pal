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

package com.quasient.pal.core.messages;

import com.google.common.primitives.Longs;
import com.quasient.pal.messages.BaseMsg;
import java.util.Objects;
import java.util.stream.Stream;
import org.zeromq.ZMQ;

/**
 * Represents a published message comprising a numeric offset and a unique message identifier.
 *
 * <p>When transmitted over a ZeroMQ socket, the message is split into two frames:
 *
 * <ol>
 *   <li>The first frame contains the binary representation of the offset (a 64-bit long).
 *   <li>The second frame contains the message identifier as a byte array encoded using ZeroMQ's
 *       charset.
 * </ol>
 *
 * The cumulative size of these frames is stored in the inherited {@code size} field from {@link
 * BaseMsg}. This class is utilized within the PAL runtime for publishing messages with ordering
 * provided by the offset.
 */
public class PublishedOffsetMsg extends BaseMsg {

  /** The numeric offset associated with the published message, used to maintain message order. */
  private final long offset;

  /** The unique identifier of the published message. */
  private final String messageId;

  /**
   * Constructs a new PublishedOffsetMsg with the specified offset and message identifier.
   *
   * @param offset the numeric offset for the message, representing its sequential position
   * @param messageId the unique identifier of the message; must not be null
   * @throws NullPointerException if {@code messageId} is null
   */
  public PublishedOffsetMsg(long offset, String messageId) {
    Stream.of(offset, messageId).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.messageId = messageId;
  }

  /**
   * Constructs a new PublishedOffsetMsg with a specified message size. This constructor is
   * primarily used during message reception to record the cumulative size of the received frames.
   *
   * @param offset the numeric offset for the message
   * @param messageId the unique identifier of the message
   * @param size the total size in bytes of the received message frames
   */
  private PublishedOffsetMsg(long offset, String messageId, int size) {
    this(offset, messageId);
    this.size = size;
  }

  /**
   * Sends the message frames over the provided ZeroMQ socket.
   *
   * <p>The method first sends the offset as a byte array followed by the message identifier
   * converted into a byte array based on ZeroMQ's charset. The cumulative size of these frames is
   * updated accordingly.
   *
   * @param socket the ZeroMQ socket over which the message is transmitted; must not be null
   * @return {@code true} if both message frames are sent successfully, {@code false} otherwise
   * @throws IllegalArgumentException if the provided {@code socket} is null
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;
    byte[] buff = Longs.toByteArray(offset);
    size += buff.length;
    // 1. message offset
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // 2. message id
    buff = messageId.getBytes(ZMQ.CHARSET);
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Receives a PublishedOffsetMsg from the provided ZeroMQ socket.
   *
   * <p>This method reads two frames from the socket: the first frame (which can be read in blocking
   * or non-blocking mode) contains the offset, and the second frame contains the message
   * identifier. In non-blocking mode, if the first frame is not available, the method returns
   * {@code null}.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @param blocking if {@code true}, performs a blocking read on the first frame; if {@code false},
   *     returns {@code null} immediately when no message is available
   * @return a new PublishedOffsetMsg instance if the message is successfully received, or {@code
   *     null} when in non-blocking mode with no available message
   * @throws IllegalArgumentException if the provided {@code socket} is null
   */
  public static PublishedOffsetMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    // 1. message offset
    int msgSize = buff.length;
    final long offset = Longs.fromByteArray(buff);
    // 2. message id
    buff = socket.recv();
    msgSize += buff.length;
    final String messageId = new String(buff, ZMQ.CHARSET);
    return new PublishedOffsetMsg(offset, messageId, msgSize);
  }

  /**
   * Receives a PublishedOffsetMsg from the provided ZeroMQ socket using a non-blocking read.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @return a new PublishedOffsetMsg instance if a message is available, or {@code null} if no
   *     message is present
   * @throws IllegalArgumentException if the provided {@code socket} is null
   */
  public static PublishedOffsetMsg receive(ZMQ.Socket socket) {
    return receive(socket, false);
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PublishedOffsetMsg that = (PublishedOffsetMsg) o;
    return offset == that.offset && messageId.equals(that.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(offset, messageId);
  }

  @Override
  public String toString() {
    return "PublishedOffsetMsg{"
        + "offset="
        + offset
        + ", messageId="
        + messageId
        + " size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  /**
   * Retrieves the offset value of the published message.
   *
   * @return the long offset associated with this message
   */
  public long getOffset() {
    return offset;
  }

  /**
   * Retrieves the unique message identifier.
   *
   * @return the message identifier string associated with this message
   */
  public String getMessageId() {
    return messageId;
  }
}
