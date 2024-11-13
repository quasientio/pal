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
import java.util.stream.Stream;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.BaseMsg;
import org.zeromq.ZMQ;

public class OutboundJsonRpcResponseMsg extends BaseMsg {
  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. peerId           : byte[] (UUID of WebSocket peer)
   * 2. message            : byte[] (JSON-RPC response)
   * </pre>
   */

  // fields
  private final UUID peerId;

  private final String jsonMessage;

  public OutboundJsonRpcResponseMsg(UUID peerId, String message) {
    Stream.of(peerId, message).forEach(Objects::requireNonNull);
    this.peerId = peerId;
    this.jsonMessage = message;
  }

  private OutboundJsonRpcResponseMsg(UUID peerId, String message, int size) {
    this(peerId, message);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    // peerId
    byte[] buff = UuidUtils.toBytes(peerId);
    size = buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    // message
    buff = jsonMessage.getBytes(ZMQ.CHARSET);
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return OutboundJsonRpcResponseMsg instance, or null if non-blocking and no message available
   */
  public static OutboundJsonRpcResponseMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;

    // read and discard empty envelope (identity frame)
    socket.recv(flag);

    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    int msgSize = buff.length;

    // peerId
    final UUID peerId = UuidUtils.fromBytes(buff);

    // message body
    buff = socket.recv();
    msgSize += buff.length;
    final String message = new String(buff, ZMQ.CHARSET);

    return new OutboundJsonRpcResponseMsg(peerId, message, msgSize);
  }

  // default is non-blocking
  public static OutboundJsonRpcResponseMsg receive(ZMQ.Socket socket) {
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
    OutboundJsonRpcResponseMsg that = (OutboundJsonRpcResponseMsg) o;
    return Objects.equals(peerId, that.peerId) && Objects.equals(jsonMessage, that.jsonMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId, jsonMessage);
  }

  @Override
  public String toString() {
    return "OutboundJsonRpcResponseMsg{peerId=" + peerId + ", message=" + jsonMessage + '}';
  }

  public UUID getPeerId() {
    return peerId;
  }

  public String getJsonMessage() {
    return jsonMessage;
  }
}
