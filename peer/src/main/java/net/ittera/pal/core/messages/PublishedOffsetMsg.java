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
import java.util.UUID;
import java.util.stream.Stream;
import net.ittera.pal.common.util.UUIDUtils;
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
   * 2. message uuid       : byte[]
   * </pre>
   */

  // fields
  private final long offset;

  private final UUID messageUuid;

  public PublishedOffsetMsg(long offset, UUID messageUuid) {
    Stream.of(offset, messageUuid).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.messageUuid = messageUuid;
  }

  private PublishedOffsetMsg(long offset, UUID messageUuid, int size) {
    this(offset, messageUuid);
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

    // 2. message uuid
    buff = UUIDUtils.toBytes(messageUuid);
    size += buff.length;
    return socket.send(buff, 0);
  }

  // blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
  // ready, then all are)
  public static PublishedOffsetMsg recvMsg(ZMQ.Socket socket, boolean blocking) {
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
    // 2. message uuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID messageUuid = UUIDUtils.fromBytes(buff);
    return new PublishedOffsetMsg(offset, messageUuid, msgSize);
  }

  // default is non-blocking
  public static PublishedOffsetMsg recvMsg(ZMQ.Socket socket) {
    return recvMsg(socket, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PublishedOffsetMsg that = (PublishedOffsetMsg) o;
    return offset == that.offset && messageUuid.equals(that.messageUuid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(offset, messageUuid);
  }

  @Override
  public String toString() {
    return "PublishedOffsetMsg{"
        + "offset="
        + offset
        + ", messageUuid="
        + messageUuid
        + " size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public long getOffset() {
    return offset;
  }

  public UUID getMessageUuid() {
    return messageUuid;
  }
}
