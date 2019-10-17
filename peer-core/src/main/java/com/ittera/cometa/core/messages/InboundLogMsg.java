package com.ittera.cometa.core.messages;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.ittera.cometa.messages.MessageType;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
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
   * 1. type of message    : int (MessageType)
   * 2. offset             : long
   * 3. message body       : byte[]
   * </pre>
   */

  // fields
  private final MessageType messageType;

  private final long offset;
  private final byte[] body;

  public InboundLogMsg(MessageType messageType, long offset, byte[] body) {
    Stream.of(messageType, offset, body).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.offset = offset;
    this.body = body;
  }

  private InboundLogMsg(MessageType messageType, long offset, byte[] body, int size) {
    this(messageType, offset, body);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;
    // 0. emulate empty REQ envelope since this message is sent directly by a DEALER
    if (!socket.send("", ZMQ.SNDMORE)) {
      return false;
    }
    // 1. type of message
    byte[] buff = Ints.toByteArray(messageType.ordinal());
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    // 2. message offset
    buff = Longs.toByteArray(offset);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    // 3. message body
    size += body.length;
    if (!socket.send(body, 0)) {
      return false;
    }
    return true;
  }

  /**
   * blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
   * ready, then all are)
   */
  public static InboundLogMsg recvMsg(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    // 1. type of message
    int msgSize = buff.length;
    final MessageType messageType = MessageType.values[Ints.fromByteArray(buff)];
    // 2. message offset
    buff = socket.recv();
    msgSize += buff.length;
    final long offset = Longs.fromByteArray(buff);
    // 3. message body
    final byte[] body = socket.recv();
    msgSize += body.length;
    return new InboundLogMsg(messageType, offset, body, msgSize);
  }

  // default is non-blocking
  public static InboundLogMsg recvMsg(ZMQ.Socket socket) {
    return recvMsg(socket, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InboundLogMsg that = (InboundLogMsg) o;
    return offset == that.offset
        && messageType == that.messageType
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(messageType, offset);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InboundLogMsg{"
        + "messageType="
        + messageType
        + ", offset="
        + offset
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public long getOffset() {
    return offset;
  }

  public byte[] getBody() {
    return body;
  }
}
