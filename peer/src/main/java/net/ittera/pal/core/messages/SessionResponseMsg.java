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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.BaseMsg;
import net.ittera.pal.messages.types.SessionStatusType;
import org.zeromq.ZMQ;

public class SessionResponseMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. status             : byte - SessionStatusType
   * 2. objectRefs         : comma-separated list of objectRefs - in response to DELETE_SESSION
   * </pre>
   */

  // fields
  private final SessionStatusType statusType;

  @Nullable private Set<ObjectRef> objectRefs;

  private SessionResponseMsg(
      @Nonnull SessionStatusType statusType, @Nullable Set<ObjectRef> objectRefs, int size) {
    this(statusType, objectRefs);
    this.size = size;
  }

  public SessionResponseMsg(@Nonnull SessionStatusType statusType) {
    Objects.requireNonNull(statusType);
    this.statusType = statusType;
  }

  public SessionResponseMsg(
      @Nonnull SessionStatusType statusType, @Nullable Set<ObjectRef> objectRefs) {
    this(statusType);
    this.objectRefs = objectRefs;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;

    // status
    byte[] buff = new byte[] {statusType.toByte()};
    size += buff.length;
    final int sendMoreFlag = objectRefs != null && !objectRefs.isEmpty() ? ZMQ.SNDMORE : 0;
    if (!socket.send(buff, sendMoreFlag)) {
      return false;
    }

    // [objectRefs]
    String objectRefsAsString;
    if (objectRefs != null && !objectRefs.isEmpty()) {
      objectRefsAsString =
          objectRefs.stream().map(ObjectRef::asString).collect(Collectors.joining(","));
      buff = objectRefsAsString.getBytes(ZMQ.CHARSET);
      size += buff.length;
      return socket.send(buff, 0);
    }

    return true;
  }

  /**
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * read, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return SessionResponseMsg instance, or null if non-blocking and no message available
   */
  public static SessionResponseMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // status
    int msgSize = buff.length;
    SessionStatusType status = SessionStatusType.fromByte(buff[0]);

    // [objectRefs]
    Set<ObjectRef> objectRefs = null;
    if (socket.hasReceiveMore()) {
      buff = socket.recv();
      msgSize += buff.length;
      objectRefs =
          Arrays.stream(new String(buff, ZMQ.CHARSET).split(","))
              .map(ObjectRef::from)
              .collect(Collectors.toSet());
    }

    return new SessionResponseMsg(status, objectRefs, msgSize);
  }

  // default is non-blocking
  public static SessionResponseMsg receive(ZMQ.Socket socket) {
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
    SessionResponseMsg that = (SessionResponseMsg) o;
    return statusType == that.statusType && Objects.equals(objectRefs, that.objectRefs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusType, objectRefs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SessionResponseMsg{status=").append(statusType.name());
    if (objectRefs != null) {
      String objectRefsAsString =
          objectRefs.stream().map(ObjectRef::asString).collect(Collectors.joining(","));
      sb.append(", objectRefs=").append(objectRefsAsString);
    }
    sb.append(", size=").append(getSize() == -1 ? "<unknown>" : getSize()).append('}');
    return sb.toString();
  }

  public SessionStatusType getStatus() {
    return statusType;
  }

  @Nullable
  public Set<ObjectRef> getObjectRefs() {
    return objectRefs;
  }
}
