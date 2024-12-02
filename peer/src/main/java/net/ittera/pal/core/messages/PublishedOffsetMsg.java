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

package net.ittera.pal.core.messages;

import com.google.common.primitives.Longs;
import java.util.Objects;
import java.util.stream.Stream;
import net.ittera.pal.messages.BaseMsg;
import org.zeromq.ZMQ;

public class PublishedOffsetMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. offset             : long
   * 2. message id         : byte[]
   * </pre>
   */

  // fields
  private final long offset;

  private final String messageId;

  public PublishedOffsetMsg(long offset, String messageId) {
    Stream.of(offset, messageId).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.messageId = messageId;
  }

  private PublishedOffsetMsg(long offset, String messageId, int size) {
    this(offset, messageId);
    this.size = size;
  }

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
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return PublishedOffsetMsg instance, or null if non-blocking and no message available
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

  // default is non-blocking
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

  public long getOffset() {
    return offset;
  }

  public String getMessageId() {
    return messageId;
  }
}
