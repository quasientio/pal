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
import net.ittera.pal.common.util.UUIDUtils;
import net.ittera.pal.messages.BaseMsg;
import org.zeromq.ZMQ;

public class InboundJsonRpcRequestMsg extends BaseMsg {
  /**
   * This message is sent directly by DEALER, so it needs to emulate a REQ envelope (empty initial
   * frame) when serializing, and NOT expect it when deserializing.
   *
   * <p>The envelope is optional because the message is also sent and received through a PUSH/PULL
   * socket pair internally by JSONRPCRequestDispatcher, which does not require an envelope.
   *
   * <pre>
   * FRAMES:
   * -------
   * 0 [empty REQ envelope]: ""
   * 1. clientId           : byte[] (UUID of WebSocket client)
   * 2. message            : byte[] (JSON-RPC request)
   * </pre>
   */

  // fields
  private final UUID clientId;

  private final String jsonMessage;

  public InboundJsonRpcRequestMsg(UUID clientId, String message) {
    Stream.of(clientId, message).forEach(Objects::requireNonNull);
    this.clientId = clientId;
    this.jsonMessage = message;
  }

  private InboundJsonRpcRequestMsg(UUID clientId, String message, int size) {
    this(clientId, message);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    return send(socket, true);
  }

  public boolean send(ZMQ.Socket socket, boolean withEnvelope) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    // emulate empty REQ envelope since this message is sent directly by a DEALER
    if (withEnvelope && !socket.send("", ZMQ.SNDMORE)) {
      return false;
    }
    // clientId
    byte[] buff = UUIDUtils.toBytes(clientId);
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
   * blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are)
   */
  public static InboundJsonRpcRequestMsg recvMsg(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    int msgSize = buff.length;

    // clientId
    final UUID clientId = UUIDUtils.fromBytes(buff);

    // message body
    buff = socket.recv();
    msgSize += buff.length;
    final String message = new String(buff, ZMQ.CHARSET);

    return new InboundJsonRpcRequestMsg(clientId, message, msgSize);
  }

  // default is non-blocking
  public static InboundJsonRpcRequestMsg recvMsg(ZMQ.Socket socket) {
    return recvMsg(socket, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InboundJsonRpcRequestMsg that = (InboundJsonRpcRequestMsg) o;
    return Objects.equals(clientId, that.clientId) && Objects.equals(jsonMessage, that.jsonMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, jsonMessage);
  }

  @Override
  public String toString() {
    return "InboundJsonRpcRequestMsg{clientId=" + clientId + ", message=" + jsonMessage + '}';
  }

  public UUID getClientId() {
    return clientId;
  }

  public String getJsonMessage() {
    return jsonMessage;
  }
}
