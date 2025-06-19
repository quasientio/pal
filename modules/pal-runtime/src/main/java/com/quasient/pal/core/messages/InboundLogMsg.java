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

package com.quasient.pal.core.messages;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.quasient.pal.messages.BaseMsg;
import com.quasient.pal.messages.types.MessageFormatType;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.zeromq.ZMQ;

/**
 * Represents an inbound log message carrying a log record transmitted via a DEALER socket. Due to
 * the direct sending by a DEALER, the message emulates a REQ envelope during serialization. The
 * message is structured into several frames as follows:
 *
 * <pre>
 *   0: Empty REQ envelope (an empty string)
 *   1: Offset (8-byte long)
 *   2: Message format (1-byte indicator)
 *   3: Headers - first an integer count followed by successive key and value frames
 *   4: Message body (byte array)
 * </pre>
 *
 * Instances can be created either directly via the constructor or through message reception.
 */
public class InboundLogMsg extends BaseMsg {
  /** The log offset that identifies the position of this message within the log stream. */
  private final long offset;

  /** The format type of the message, indicating how to process the message body. */
  private final MessageFormatType messageFormat;

  /** The headers containing key-value metadata associated with the message. */
  private final Headers headers;

  /** The payload of the message as a byte array. */
  private final byte[] body;

  /**
   * Constructs an InboundLogMsg with the specified log offset, message format, headers, and body.
   * All parameters (except the primitive offset) must be non-null.
   *
   * @param offset the log offset identifying the message position
   * @param messageFormat the message format indicator
   * @param headers the metadata headers associated with the message
   * @param body the message payload as a byte array
   * @throws NullPointerException if any of the reference parameters are null
   */
  public InboundLogMsg(long offset, MessageFormatType messageFormat, Headers headers, byte[] body) {
    Stream.of(offset, headers, body).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.messageFormat = messageFormat;
    this.headers = headers;
    this.body = body;
  }

  /**
   * Constructs an InboundLogMsg with the specified log offset, message format, headers, body, and
   * the total serialized size. This constructor is intended for internal use when reading message
   * data including total size computation.
   *
   * @param offset the log offset identifying the message position
   * @param messageFormat the message format indicator
   * @param headers the metadata headers associated with the message
   * @param body the message payload as a byte array
   * @param size the total serialized size in bytes of the message
   * @throws NullPointerException if any of the reference parameters are null
   */
  private InboundLogMsg(
      long offset, MessageFormatType messageFormat, Headers headers, byte[] body, int size) {
    this(offset, messageFormat, headers, body);
    this.size = size;
  }

  /**
   * Sends this inbound log message over the specified ZeroMQ socket.
   *
   * <p>The method serializes the message into a series of frames: an empty frame (REQ envelope),
   * the log offset, the message format, header count with key-value pairs, and finally the message
   * body.
   *
   * @param socket the ZeroMQ socket used to send the message; must not be null
   * @return true if all message frames are successfully sent; false otherwise
   * @throws IllegalArgumentException if the provided socket is null
   */
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
   * Receives an inbound log message from the specified ZeroMQ socket.
   *
   * <p>The read operation applies a blocking flag only to the first frame. Subsequent frames are
   * read atomically if the first frame is available.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @param blocking if true, waits for the first frame; if false, returns null when no message is
   *     available
   * @return an InboundLogMsg instance if a complete message is received, or null if non-blocking
   *     mode is active and no message is available
   * @throws IllegalArgumentException if the provided socket is null
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

  /**
   * Receives an inbound log message from the specified ZeroMQ socket using non-blocking mode.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @return an InboundLogMsg instance if a complete message is received, or null if no message is
   *     available
   * @throws IllegalArgumentException if the provided socket is null
   * @see #receive(ZMQ.Socket, boolean)
   */
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

  /**
   * Returns the log offset associated with this message.
   *
   * @return the offset as a long value
   */
  public long getOffset() {
    return offset;
  }

  /**
   * Returns the headers metadata of this message.
   *
   * @return the Headers containing key-value pairs for the message
   */
  public Headers getHeaders() {
    return headers;
  }

  /**
   * Returns the payload of the message.
   *
   * @return a byte array representing the message body
   */
  public byte[] getBody() {
    return body;
  }

  /**
   * Returns the message format indicator.
   *
   * @return the MessageFormatType defining the format of the message body
   */
  public MessageFormatType getMessageFormat() {
    return messageFormat;
  }
}
