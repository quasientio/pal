package com.ittera.cometa.core.messages;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.common.util.UUIDUtils;
import com.ittera.cometa.core.exec.ExecPhase;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.Headers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
   * 2. [execution phase]  : int (ExecPhase: Undefined if MessageType != ExecMessage)
   * 3. headers to follow  : int
   * 4. [headers]          : byte[]* (InternalHeader)
   * 5. message uuid       : byte[]
   * 6. followingUuid      : byte[]
   * 7. message body       : byte[]
   * </pre>
   */

  // fields
  private final MessageType messageType;

  private final ExecPhase execPhase;
  @Nullable private final List<Headers.InternalHeader> headers;
  private final UUID messageUuid;
  @Nullable private final UUID followingUuid;
  private final byte[] body;

  public OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<Headers.InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID followingUuid,
      byte[] body) {
    Stream.of(messageType, execPhase, messageUuid, body).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.execPhase = execPhase;
    this.headers = headers;
    this.messageUuid = messageUuid;
    this.followingUuid = followingUuid;
    this.body = body;
  }

  private OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<Headers.InternalHeader> headers,
      UUID messageUuid,
      @Nullable UUID followingUuid,
      byte[] body,
      int size) {
    this(messageType, execPhase, headers, messageUuid, followingUuid, body);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }

    size = 0;
    byte[] buff;
    // type of message
    buff = String.valueOf(messageType.ordinal()).getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // execution phase
    buff = String.valueOf(execPhase.ordinal()).getBytes(ZMQ.CHARSET);
    size += buff.length;
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
      for (Headers.InternalHeader header : headers) {
        buff = header.toByteArray();
        size += buff.length;
        if (!socket.send(buff, ZMQ.SNDMORE)) {
          return false;
        }
      }
    }

    // message uuid
    buff = UUIDUtils.toBytes(messageUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // followingUuid
    buff =
        followingUuid == null
            ? String.valueOf(0).getBytes(ZMQ.CHARSET)
            : UUIDUtils.toBytes(followingUuid);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // message body
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

    // type of message
    int msgSize = buff.length;
    final MessageType messageType =
        MessageType.values[Integer.parseInt(new String(buff, ZMQ.CHARSET))];
    // execution phase
    final ExecPhase execPhase;
    buff = socket.recv();
    msgSize += buff.length;
    execPhase = ExecPhase.values[Integer.parseInt(new String(buff, ZMQ.CHARSET))];

    // # of headers to follow
    buff = socket.recv();
    msgSize += buff.length;
    final int headerCount = Integer.parseInt(new String(buff, ZMQ.CHARSET));
    // headers
    final List<Headers.InternalHeader> headers;
    if (headerCount > 0) {
      headers = new ArrayList<>();
      for (int i = 0; i < headerCount; i++) {
        buff = socket.recv();
        msgSize += buff.length;
        headers.add(Headers.InternalHeader.parseFrom(buff));
      }
    } else {
      headers = null;
    }

    // message uuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID messageUuid = UUIDUtils.fromBytes(buff);

    // followingUuid
    buff = socket.recv();
    msgSize += buff.length;
    final UUID followingUuid;
    if (!"0".equals(new String(buff, ZMQ.CHARSET))) {
      followingUuid = UUIDUtils.fromBytes(buff);
    } else {
      followingUuid = null;
    }

    // message body
    final byte[] body = socket.recv();
    msgSize += body.length;

    return new OutboundMsg(
        messageType, execPhase, headers, messageUuid, followingUuid, body, msgSize);
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
        && execPhase.equals(that.execPhase)
        && Objects.equals(headers, that.headers)
        && messageUuid.equals(that.messageUuid)
        && Objects.equals(followingUuid, that.followingUuid)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(messageType, execPhase, headers, messageUuid, followingUuid);
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

  @Nullable
  public ExecPhase getExecPhase() {
    return execPhase;
  }

  public List<Headers.InternalHeader> getHeaders() {
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
