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

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.BaseMsg;
import net.ittera.pal.messages.types.SessionCommandType;
import org.zeromq.ZMQ;

public class SessionCommandMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. cmd                : byte - SessionCommandType
   * [2. sessionId]        : UUID - peerUuid used as sessionId. Mandatory except for CLEAR_SESSIONS
   * [3. objectRef]        : String - mandatory argument for STORE_OBJECT and DELETE_OBJECT
   * </pre>
   */

  // fields
  private final SessionCommandType commandType;

  @Nullable private final UUID sessionId;
  @Nullable private final ObjectRef objectRef;

  public SessionCommandMsg(
      @Nonnull SessionCommandType commandType,
      @Nullable UUID sessionId,
      @Nullable ObjectRef objectRef) {
    Objects.requireNonNull(commandType);
    if (!commandType.equals(SessionCommandType.CLEAR_SESSIONS)) {
      Objects.requireNonNull(sessionId);
    }
    if (commandType.equals(SessionCommandType.STORE_OBJECT)
        || commandType.equals(SessionCommandType.DELETE_OBJECT)) {
      Objects.requireNonNull(objectRef);
    }
    this.commandType = commandType;
    this.sessionId = sessionId;
    this.objectRef = objectRef;
  }

  public SessionCommandMsg(@Nonnull SessionCommandType commandType) {
    this(commandType, null, null);
  }

  public SessionCommandMsg(@Nonnull SessionCommandType commandType, UUID sessionId) {
    this(commandType, sessionId, null);
  }

  private SessionCommandMsg(
      @Nonnull SessionCommandType commandType, UUID sessionId, ObjectRef objectRef, int size) {
    this(commandType, sessionId, objectRef);
    this.size = size;
  }

  private SessionCommandMsg(@Nonnull SessionCommandType commandType, int size) {
    this(commandType);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;

    // command
    byte[] buff = new byte[] {commandType.toByte()};
    size += buff.length;
    final boolean hasSessionId =
        !commandType.equals(SessionCommandType.CLEAR_SESSIONS) && (sessionId != null);
    if (!socket.send(buff, hasSessionId ? ZMQ.SNDMORE : 0)) {
      return false;
    }

    final boolean hasObjectRef =
        (commandType.equals(SessionCommandType.STORE_OBJECT)
                || commandType.equals(SessionCommandType.DELETE_OBJECT))
            && (objectRef != null);

    // sessionId
    if (hasSessionId) {
      buff = UuidUtils.toBytes(sessionId);
      size += buff.length;
      if (!socket.send(buff, hasObjectRef ? ZMQ.SNDMORE : 0)) {
        return false;
      }
    } else {
      return true;
    }

    // [objectRef]
    if (hasObjectRef) {
      buff = objectRef.asString().getBytes(ZMQ.CHARSET);
      size += buff.length;
      return socket.send(buff, 0);
    }

    return true;
  }

  /**
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return SessionCommandMsg instance, or null if non-blocking and no message available
   */
  public static SessionCommandMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // command
    int msgSize = buff.length;
    SessionCommandType commandType = SessionCommandType.fromByte(buff[0]);

    if (commandType.equals(SessionCommandType.CLEAR_SESSIONS)) {
      return new SessionCommandMsg(commandType, msgSize);
    }

    // sessionId
    buff = socket.recv();
    msgSize += buff.length;
    UUID sessionUuid = UuidUtils.fromBytes(buff);

    // [objectRef]
    ObjectRef objRef = null;
    if (commandType.equals(SessionCommandType.STORE_OBJECT)
        || commandType.equals(SessionCommandType.DELETE_OBJECT)) {
      buff = socket.recv();
      msgSize += buff.length;
      objRef = ObjectRef.from(new String(buff, ZMQ.CHARSET));
    }
    return new SessionCommandMsg(commandType, sessionUuid, objRef, msgSize);
  }

  // default is non-blocking
  public static SessionCommandMsg receive(ZMQ.Socket socket) {
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
    SessionCommandMsg that = (SessionCommandMsg) o;
    return commandType == that.commandType
        && Objects.equals(sessionId, that.sessionId)
        && Objects.equals(objectRef, that.objectRef);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commandType, sessionId, objectRef);
  }

  @Override
  public String toString() {
    return "SessionCommandMsg{"
        + "type="
        + commandType.name()
        + ", sessionID="
        + sessionId
        + ", objectRef="
        + objectRef
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public SessionCommandType getCommand() {
    return commandType;
  }

  @Nullable
  public UUID getSessionId() {
    return sessionId;
  }

  @Nullable
  public ObjectRef getObjectRef() {
    return objectRef;
  }
}
