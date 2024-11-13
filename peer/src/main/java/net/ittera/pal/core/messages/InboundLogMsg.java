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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import net.ittera.pal.messages.BaseMsg;
import net.ittera.pal.messages.types.MessageFormatType;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.zeromq.ZMQ;

public class InboundLogMsg extends BaseMsg {
  /**
   * This message is sent directly by DEALER, so it needs to emulate a REQ envelope (empty initial
   * frame) when serializing, and NOT expect it when deserializing.
   *
   * <pre>
   * FRAMES:
   * -------
   * 0 [empty REQ envelope]: ""
   * 1. offset             : long
   * 2. message format     : byte
   * 3. message body       : byte[]
   * </pre>
   */

  // fields
  private final long offset;

  private final MessageFormatType messageFormat;
  private final Headers headers;
  private final byte[] body;

  public InboundLogMsg(long offset, MessageFormatType messageFormat, Headers headers, byte[] body) {
    Stream.of(offset, headers, body).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.messageFormat = messageFormat;
    this.headers = headers;
    this.body = body;
  }

  private InboundLogMsg(
      long offset, MessageFormatType messageFormat, Headers headers, byte[] body, int size) {
    this(offset, messageFormat, headers, body);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    // emulate empty REQ envelope since this message is sent directly by a DEALER
    if (!socket.send("", ZMQ.SNDMORE)) {
      return false;
    }
    // message offset
    byte[] buff = Longs.toByteArray(offset);
    size = buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // message format
    buff = new byte[] {messageFormat.toByte()};
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // headers
    int headerCount = headers.toArray().length;
    buff = Ints.toByteArray(headerCount);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    for (Header header : headers) {
      byte[] keyBytes = header.key().getBytes(ZMQ.CHARSET);
      byte[] valueBytes = header.value();
      if (!socket.send(keyBytes, ZMQ.SNDMORE) || !socket.send(valueBytes, ZMQ.SNDMORE)) {
        return false;
      }
      size += keyBytes.length + valueBytes.length;
    }

    // message body
    size += body.length;
    return socket.send(body, 0);
  }

  /**
   * Blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are).
   *
   * @param socket ZMQ socket
   * @param blocking blocking read flag
   * @return InboundLogMsg instance, or null if non-blocking and no message available
   */
  public static InboundLogMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    int msgSize = buff.length;

    // message offset
    final long offset = Longs.fromByteArray(buff);

    // message format
    buff = socket.recv();
    msgSize += buff.length;
    final MessageFormatType messageFormat = MessageFormatType.fromByte(buff[0]);

    // headers
    buff = socket.recv();
    msgSize += buff.length;
    int headerCount = Ints.fromByteArray(buff);
    Headers headers = new RecordHeaders();
    for (int i = 0; i < headerCount; i++) {
      byte[] keyBytes = socket.recv();
      byte[] valueBytes = socket.recv();
      msgSize += keyBytes.length + valueBytes.length;
      headers.add(new RecordHeader(new String(keyBytes, ZMQ.CHARSET), valueBytes));
    }

    // message body
    final byte[] body = socket.recv();
    msgSize += body.length;

    return new InboundLogMsg(offset, messageFormat, headers, body, msgSize);
  }

  // default is non-blocking
  public static InboundLogMsg receive(ZMQ.Socket socket) {
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
    InboundLogMsg that = (InboundLogMsg) o;
    return offset == that.offset
        && messageFormat == that.messageFormat
        && headers.equals(that.headers)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(offset, messageFormat, headers);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InboundLogMsg{"
        + ", offset="
        + offset
        + ", messageFormat="
        + messageFormat
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public long getOffset() {
    return offset;
  }

  public Headers getHeaders() {
    return headers;
  }

  public byte[] getBody() {
    return body;
  }

  public MessageFormatType getMessageFormat() {
    return messageFormat;
  }
}
