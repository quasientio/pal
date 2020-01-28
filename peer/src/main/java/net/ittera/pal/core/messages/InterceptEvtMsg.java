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
import java.util.UUID;
import javax.annotation.Nullable;
import net.ittera.pal.common.util.UUIDUtils;
import net.ittera.pal.messages.BaseMsg;
import org.zeromq.ZMQ;

public class InterceptEvtMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. type               : Type (register/unregister)
   * 2. body               : byte[] (body of intercept message to register)
   * 2. message uuid       : byte[] (UUID of message to unregister)
   * </pre>
   */
  public enum Type {
    REGISTER,
    UNREGISTER;
    static final Type[] values = values();
  }

  // fields
  private final Type type;
  @Nullable private final UUID interceptMsgUUID;
  @Nullable private final byte[] body;

  public InterceptEvtMsg(byte[] body) {
    this(Type.REGISTER, body, null);
  }

  public InterceptEvtMsg(UUID interceptMsgUUID) {
    this(Type.UNREGISTER, null, interceptMsgUUID);
  }

  private InterceptEvtMsg(Type type, @Nullable byte[] body, @Nullable UUID interceptMsgUUID) {
    if (type.equals(Type.REGISTER)) {
      Objects.requireNonNull(body);
    }

    if (type.equals(Type.UNREGISTER)) {
      Objects.requireNonNull(interceptMsgUUID);
    }

    this.type = type;
    this.body = body;
    this.interceptMsgUUID = interceptMsgUUID;
  }

  private InterceptEvtMsg(Type type, byte[] body, @Nullable UUID interceptMsgUUID, int size) {
    this(type, body, interceptMsgUUID);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;
    byte[] buff = String.valueOf(type.ordinal()).getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    if (type.equals(Type.REGISTER)) {
      buff = body;
      size += buff.length;
      if (!socket.send(buff, 0)) {
        return false;
      }
    } else { // (type.equals(Type.UNREGISTER))
      buff = UUIDUtils.toBytes(interceptMsgUUID);
      size += buff.length;
      if (!socket.send(buff, 0)) {
        return false;
      }
    }

    return true;
  }

  // blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
  // ready, then all are)
  public static InterceptEvtMsg recvMsg(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // type
    int msgSize = buff.length;
    Type type = Type.values[Integer.parseInt(new String(buff, ZMQ.CHARSET))];

    // body | msgUUID
    buff = socket.recv();
    msgSize += buff.length;
    byte[] body = null;
    UUID interceptMsgUuid = null;
    if (type.equals(Type.REGISTER)) {
      body = buff;
    } else { // UNREGISTER
      interceptMsgUuid = UUIDUtils.fromBytes(buff);
    }
    return new InterceptEvtMsg(type, body, interceptMsgUuid, msgSize);
  }

  // default is non-blocking
  public static InterceptEvtMsg recvMsg(ZMQ.Socket socket) {
    return recvMsg(socket, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptEvtMsg that = (InterceptEvtMsg) o;
    return type == that.type
        && Objects.equals(interceptMsgUUID, that.interceptMsgUUID)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type, interceptMsgUUID);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InterceptEvtMsg{"
        + "type="
        + type
        + ", interceptMsgUUID="
        + interceptMsgUUID
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public Type getType() {
    return type;
  }

  @Nullable
  public UUID getInterceptMsgUUID() {
    return interceptMsgUUID;
  }

  @Nullable
  public byte[] getBody() {
    return body;
  }
}
