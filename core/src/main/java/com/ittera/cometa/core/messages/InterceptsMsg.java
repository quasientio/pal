package com.ittera.cometa.core.messages;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import java.util.*;
import javax.annotation.Nullable;
import org.zeromq.ZMQ;

public class InterceptsMsg extends BaseMsg {

  /**
   *
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. intercept reqs contained    : int
   * 2. InterceptMessage*           : byte[]* (InterceptMessage)
   * </pre>
   */

  // fields
  @Nullable private final List<InterceptMessage> intercepts;

  public InterceptsMsg(@Nullable List<Intercepts.InterceptMessage> intercepts) {
    this.intercepts = intercepts;
  }

  private InterceptsMsg(@Nullable List<Intercepts.InterceptMessage> intercepts, int size) {
    this(intercepts);
    this.size = size;
  }

  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }

    size = 0;
    byte[] buff;
    // 1. number of contained intercepts
    final int interceptsCount = intercepts != null ? intercepts.size() : 0;
    //    buff = Ints.toByteArray(interceptsCount);
    buff = String.valueOf(interceptsCount).getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, interceptsCount > 0 ? ZMQ.SNDMORE : 0)) {
      return false;
    }

    // 2. intercepts
    if (interceptsCount > 0) {
      int interceptsSent = 0;
      for (InterceptMessage interceptMessage : intercepts) {
        buff = interceptMessage.toByteArray();
        size += buff.length;
        if (!socket.send(buff, ++interceptsSent < interceptsCount ? ZMQ.SNDMORE : 0)) {
          return false;
        }
      }
    }
    return true;
  }

  // blocking flag only applies to first read, by virtue of messages being atomic (if 1st frame is
  // ready, then all are)
  public static InterceptsMsg recvMsg(ZMQ.Socket socket, boolean blocking)
      throws InvalidProtocolBufferException {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    int msgSize = buff.length;
    // 1. number of intercepts
    final int interceptCount = Integer.parseInt(new String(buff, ZMQ.CHARSET));
    // 2. intercepts
    final List<Intercepts.InterceptMessage> interceptMessages;
    if (interceptCount > 0) {
      interceptMessages = new ArrayList<>();
      for (int i = 0; i < interceptCount; i++) {
        buff = socket.recv();
        msgSize += buff.length;
        interceptMessages.add(InterceptMessage.parseFrom(buff));
      }
    } else {
      interceptMessages = null;
    }

    return new InterceptsMsg(interceptMessages, msgSize);
  }

  // default is non-blocking
  public static InterceptsMsg recvMsg(ZMQ.Socket socket) throws InvalidProtocolBufferException {
    return recvMsg(socket, false);
  }

  @Nullable
  public List<Intercepts.InterceptMessage> getIntercepts() {
    return intercepts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InterceptsMsg that = (InterceptsMsg) o;
    return Objects.equals(intercepts, that.intercepts);
  }

  @Override
  public int hashCode() {
    return Objects.hash(intercepts);
  }

  @Override
  public String toString() {
    return "InterceptsMsg{"
        + "intercepts="
        + intercepts
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }
}
