package com.ittera.cometa.core.messages;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.common.util.UUIDUtils;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.zeromq.ZMQ;

public class OutboundMsg extends BaseMsg {

  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. type of message    : int (MessageType)
   * 2. headers to follow  : int
   * 3. [headers]          : byte[]* (InternalHeader)
   * 4. message uuid       : byte[]
   * 5. followingUuid      : byte[]
   * 6. message body       : byte[]
   * </pre>
   */

  // fields
  private final MessageType messageType;

  @Nullable private final List<Wrappers.InternalHeader> headers;
  private final UUID messageUuid;
  @Nullable private final UUID followingUuid;
  private final byte[] body;

  public OutboundMsg(
      MessageType messageType,
      @Nullable List<Wrappers.InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID followingUuid,
      byte[] body) {
    Stream.of(messageType, messageUuid, body).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.headers = headers;
    this.messageUuid = messageUuid;
    this.followingUuid = followingUuid;
    this.body = body;
  }

  private OutboundMsg(
      MessageType messageType,
      @Nullable List<Wrappers.InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID followingUuid,
      byte[] body,
      int size) {
    this(messageType, headers, messageUuid, followingUuid, body);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }

    size = 0;
    byte[] buff;
    // 1. type of message
    buff = Ints.toByteArray(messageType.ordinal());
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // 2. # of headers to follow
    final int headersCnt = headers != null ? headers.size() : 0;
    buff = Ints.toByteArray(headersCnt);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // 3. headers
    if (headers != null && !headers.isEmpty()) {
      for (Wrappers.InternalHeader header : headers) {
        buff = header.toByteArray();
        size += buff.length;
        if (!socket.send(buff, ZMQ.SNDMORE)) {
          return false;
        }
      }
    }

    // 4. message uuid
    buff = UUIDUtils.toBytes(messageUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // 5. followingUuid
    buff = followingUuid == null ? Ints.toByteArray(0) : UUIDUtils.toBytes(followingUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // 6. message body
    size += body.length;
    if (!socket.send(body, 0)) {
      return false;
    }

    return true;
  }

  // blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
  // ready, then all are)
  public static OutboundMsg recvMsg(ZMQ.Socket socket, boolean blocking)
      throws InvalidProtocolBufferException {
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
    // 2. # of headers to follow
    buff = socket.recv();
    msgSize += buff.length;
    final int headerCount = Ints.fromByteArray(buff);
    // 3. headers
    final List<Wrappers.InternalHeader> headers;
    if (headerCount > 0) {
      headers = new ArrayList<>();
      for (int i = 0; i < headerCount; i++) {
        buff = socket.recv();
        msgSize += buff.length;
        headers.add(Wrappers.InternalHeader.parseFrom(buff));
      }
    } else {
      headers = null;
    }

    // 4. message uuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID messageUuid = UUIDUtils.fromBytes(buff);

    // 5. followingUuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID followingUuid;
    if (Ints.fromByteArray(buff) != 0) {
      followingUuid = UUIDUtils.fromBytes(buff);
    } else {
      followingUuid = null;
    }

    // 6. message body
    final byte[] body = socket.recv();
    msgSize += body.length;

    return new OutboundMsg(messageType, headers, messageUuid, followingUuid, body, msgSize);
  }

  // default is non-blocking
  public static OutboundMsg recvMsg(ZMQ.Socket socket) throws InvalidProtocolBufferException {
    return recvMsg(socket, false);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OutboundMsg that = (OutboundMsg) o;
    return messageType == that.messageType
        && Objects.equals(headers, that.headers)
        && messageUuid.equals(that.messageUuid)
        && Objects.equals(followingUuid, that.followingUuid)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(messageType, headers, messageUuid, followingUuid);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "OutboundMsg{"
        + "messageType="
        + messageType
        + ", headers="
        + headers
        + ", messageUuid="
        + messageUuid
        + ", followingUuid="
        + followingUuid
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public List<Wrappers.InternalHeader> getHeaders() {
    return headers;
  }

  public UUID getMessageUuid() {
    return messageUuid;
  }

  @Nullable
  public UUID getFollowingUuid() {
    return followingUuid;
  }

  public byte[] getBody() {
    return body;
  }
}
