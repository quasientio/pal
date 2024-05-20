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

package net.ittera.pal.messages;

import static net.ittera.pal.serdes.colfer.ColferUtils.toBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.types.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

public class OutboundMsg extends BaseMsg {

  private static final Logger logger = LoggerFactory.getLogger(OutboundMsg.class);

  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. type of message    : int (MessageType)
   * 2. [execution phase]  : int (ExecPhase: Undefined if MessageType != ExecMessage)
   * 3. headers to follow  : int
   * 4. [headers]          : byte[]* (InternalHeader)
   * 5. message uuid       : byte[]
   * 6. responseToUuid      : byte[]
   * 7. message body       : byte[]
   * </pre>
   */

  // fields
  private final MessageType messageType;

  private final ExecPhase execPhase;
  @Nullable private final List<InternalHeader> headers;
  private final UUID messageUuid;
  @Nullable private final UUID responseToUuid;
  private final byte[] body;

  // Only used by unit test
  OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID responseToUuid,
      byte[] body) {

    Stream.of(messageType, execPhase, messageUuid, body).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.execPhase = execPhase;
    this.headers = headers;
    this.messageUuid = messageUuid;
    this.responseToUuid = responseToUuid;
    this.body = body;
  }

  public OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID responseToUuid,
      @Nullable Marshallable marshallable) {

    Stream.of(messageType, execPhase, messageUuid, marshallable).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.execPhase = execPhase;
    this.headers = headers;
    this.messageUuid = messageUuid;
    this.responseToUuid = responseToUuid;
    this.body = toBytes(marshallable);
  }

  private OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID responseToUuid,
      byte[] body,
      int size) {
    this(messageType, execPhase, headers, messageUuid, responseToUuid, body);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }

    // type of message
    byte[] buff = new byte[] {messageType.toByte()};
    size = 1;
    try {
      if (!socket.send(buff, ZMQ.SNDMORE)) {
        return false;
      }
    } catch (ZMQException e) {
      logger.error("Error sending message", e);
      return false;
    }

    // execution phase
    buff = new byte[] {execPhase.toByte()};
    size++;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // # of headers to follow
    final int headersCnt = headers != null ? headers.size() : 0;
    buff = String.valueOf(headersCnt).getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // headers
    if (headers != null && !headers.isEmpty()) {
      for (InternalHeader header : headers) {
        buff = toBytes(header);
        size += buff.length;
        if (!socket.send(buff, ZMQ.SNDMORE)) {
          return false;
        }
      }
    }

    // message uuid
    buff = UuidUtils.toBytes(messageUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // responseToUuid
    buff =
        responseToUuid == null
            ? String.valueOf(0).getBytes(ZMQ.CHARSET)
            : UuidUtils.toBytes(responseToUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // message body
    size += body.length;
    return socket.send(body, 0);
  }

  // blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
  // ready, then all are)
  public static OutboundMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // type of message
    int msgSize = buff.length;
    final MessageType messageType = MessageType.fromByte(buff[0]);
    // execution phase
    final ExecPhase execPhase;
    buff = socket.recv();
    msgSize += buff.length;
    execPhase = ExecPhase.fromByte(buff[0]);

    // # of headers to follow
    buff = socket.recv();
    msgSize += buff.length;
    final int headerCount = Integer.parseInt(new String(buff, ZMQ.CHARSET));
    // headers
    final List<InternalHeader> headers;
    if (headerCount > 0) {
      headers = new ArrayList<>();
      for (int i = 0; i < headerCount; i++) {
        buff = socket.recv();
        msgSize += buff.length;
        InternalHeader internalHeader = new InternalHeader();
        internalHeader.unmarshal(buff, 0);
        headers.add(internalHeader);
      }
    } else {
      headers = null;
    }

    // message uuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID messageUuid = UuidUtils.fromBytes(buff);

    // responseToUuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID responseToUuid;
    if (!"0".equals(new String(buff, ZMQ.CHARSET))) {
      responseToUuid = UuidUtils.fromBytes(buff);
    } else {
      responseToUuid = null;
    }

    // message body
    final byte[] body = socket.recv();
    msgSize += body.length;

    return new OutboundMsg(
        messageType, execPhase, headers, messageUuid, responseToUuid, body, msgSize);
  }

  // default is non-blocking
  public static OutboundMsg receive(ZMQ.Socket socket) {
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
    OutboundMsg that = (OutboundMsg) o;
    return messageType == that.messageType
        && execPhase.equals(that.execPhase)
        && Objects.equals(headers, that.headers)
        && messageUuid.equals(that.messageUuid)
        && Objects.equals(responseToUuid, that.responseToUuid)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(messageType, execPhase, headers, messageUuid, responseToUuid);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "OutboundMsg{"
        + "messageType="
        + messageType
        + ", execPhase="
        + execPhase
        + ", headers="
        + headers
        + ", messageUuid="
        + messageUuid
        + ", responseToUuid="
        + responseToUuid
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public MessageType getMessageType() {
    return messageType;
  }

  @Nullable
  public ExecPhase getExecPhase() {
    return execPhase;
  }

  @Nullable
  public List<InternalHeader> getHeaders() {
    return headers;
  }

  public UUID getMessageUuid() {
    return messageUuid;
  }

  @Nullable
  public UUID getResponseToUuid() {
    return responseToUuid;
  }

  public byte[] getBody() {
    return body;
  }
}
