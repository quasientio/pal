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

import static net.ittera.pal.serdes.colfer.ColferUtils.toBytes;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import net.ittera.pal.messages.BaseMsg;
import net.ittera.pal.messages.Marshallable;
import org.zeromq.ZMQ;

public class InterceptEventMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. type               : Type (register/unregister)
   * 2. body               : byte[] (body of intercept message to register)
   * 2. message id         : byte[] (ID of message to unregister)
   * </pre>
   */
  public enum Type {
    REGISTER((byte) 1),
    UNREGISTER((byte) 2);

    private final byte idx;

    Type(byte idx) {
      this.idx = idx;
    }

    public static Type fromByte(byte typeAsByte) {
      return switch (typeAsByte) {
        case 1 -> REGISTER;
        case 2 -> UNREGISTER;
        default -> throw new IllegalArgumentException("Unknown type: " + typeAsByte);
      };
    }

    public byte toByte() {
      return idx;
    }
  }

  // fields
  private final Type type;
  @Nullable private final String interceptMessageId;
  @Nullable private final byte[] body;

  public InterceptEventMsg(byte[] body) {
    this(Type.REGISTER, body, null, null);
  }

  public InterceptEventMsg(Marshallable message) {
    this(Type.REGISTER, null, message, null);
  }

  public InterceptEventMsg(String interceptMessageId) {
    this(Type.UNREGISTER, null, null, interceptMessageId);
  }

  private InterceptEventMsg(
      Type type,
      @Nullable byte[] body,
      @Nullable Marshallable marshallable,
      @Nullable String interceptMessageId) {
    if (type.equals(Type.REGISTER) && (body == null && marshallable == null)) {
      throw new NullPointerException("Both body and marshallable are null.");
    }

    if (type.equals(Type.UNREGISTER)) {
      Objects.requireNonNull(interceptMessageId);
    }

    this.type = type;
    this.body = marshallable != null ? toBytes(marshallable) : body;
    this.interceptMessageId = interceptMessageId;
  }

  private InterceptEventMsg(Type type, byte[] body, @Nullable String interceptMessageId, int size) {
    this(type, body, null, interceptMessageId);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    byte[] buff = new byte[] {type.toByte()};
    size = 1;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    if (type.equals(Type.REGISTER)) {
      buff = body;
    } else { // (type.equals(Type.UNREGISTER))
      buff = interceptMessageId.getBytes(ZMQ.CHARSET);
    }
    assert buff != null;
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return InterceptEventMsg instance, or null if non-blocking and no message available
   */
  public static InterceptEventMsg receive(ZMQ.Socket socket, boolean blocking) {
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
    Type type = Type.fromByte(buff[0]);

    // body | msgId
    buff = socket.recv();
    msgSize += buff.length;
    byte[] body = null;
    String interceptMsgId = null;
    if (type.equals(Type.REGISTER)) {
      body = buff;
    } else { // UNREGISTER
      interceptMsgId = new String(buff, ZMQ.CHARSET);
    }
    return new InterceptEventMsg(type, body, interceptMsgId, msgSize);
  }

  // default is non-blocking
  public static InterceptEventMsg receive(ZMQ.Socket socket) {
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
    InterceptEventMsg that = (InterceptEventMsg) o;
    return type == that.type
        && Objects.equals(interceptMessageId, that.interceptMessageId)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type, interceptMessageId);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InterceptEventMsg{"
        + "type="
        + type
        + ", interceptMsgId="
        + interceptMessageId
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
  public String getInterceptMessageId() {
    return interceptMessageId;
  }

  @Nullable
  public byte[] getBody() {
    return body;
  }
}
