package com.ittera.cometa.core.messages;

import com.google.common.primitives.Longs;
import com.ittera.cometa.messages.BaseMsg;
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
   * 1. offset             : long
   * 2. message body       : byte[]
   * </pre>
   */

  // fields
  private final long offset;

  private final byte[] body;

  public InboundLogMsg(long offset, byte[] body) {
    Stream.of(offset, body).forEach(Objects::requireNonNull);
    this.offset = offset;
    this.body = body;
  }

  private InboundLogMsg(long offset, byte[] body, int size) {
    this(offset, body);
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
    // message body
    size += body.length;
    return socket.send(body, 0);
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
    int msgSize = buff.length;
    // message offset
    final long offset = Longs.fromByteArray(buff);
    // message body
    final byte[] body = socket.recv();
    msgSize += body.length;
    return new InboundLogMsg(offset, body, msgSize);
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
    return offset == that.offset && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(offset);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InboundLogMsg{"
        + ", offset="
        + offset
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public long getOffset() {
    return offset;
  }

  public byte[] getBody() {
    return body;
  }
}
