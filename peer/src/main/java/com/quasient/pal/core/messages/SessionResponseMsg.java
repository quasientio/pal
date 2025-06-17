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

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.BaseMsg;
import com.quasient.pal.messages.types.SessionStatusType;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.zeromq.ZMQ;

/**
 * Represents a session response message used within PAL to communicate session-related status
 * updates. In other words, response to a {@link SessionCommandMsg}.
 *
 * <p>This message encapsulates a session status value and, optionally, a set of object references.
 * The message is transmitted using a two-frame protocol:
 *
 * <pre>
 * FRAMES:
 * -------
 * 1. status     : byte - SessionStatusType
 * 2. objectRefs : comma-separated list of objectRefs (used in response to DELETE_SESSION)
 * </pre>
 */
public class SessionResponseMsg extends BaseMsg {
  /** The session status indicating the outcome of a session operation. */
  private final SessionStatusType statusType;

  /**
   * Optional set of object references returned with the response, for example, in operations
   * involving session deletion. May be null if not applicable.
   */
  @Nullable private Set<ObjectRef> objectRefs;

  /**
   * Private constructor used internally to create a complete session response message, including
   * the message size computed during reception.
   *
   * @param statusType the session status type; must not be null
   * @param objectRefs optional set of object references, may be null if not provided
   * @param size the total size in bytes of the received message frames
   */
  private SessionResponseMsg(
      @Nonnull SessionStatusType statusType, @Nullable Set<ObjectRef> objectRefs, int size) {
    this(statusType, objectRefs);
    this.size = size;
  }

  /**
   * Constructs a session response message with the specified session status.
   *
   * <p>This constructor initializes the message without any associated object references.
   *
   * @param statusType the session status type; must not be null
   */
  public SessionResponseMsg(@Nonnull SessionStatusType statusType) {
    Objects.requireNonNull(statusType);
    this.statusType = statusType;
  }

  /**
   * Constructs a session response message with the specified session status and associated object
   * references.
   *
   * @param statusType the session status type; must not be null
   * @param objectRefs optional set of object references, may be null if not applicable
   */
  public SessionResponseMsg(
      @Nonnull SessionStatusType statusType, @Nullable Set<ObjectRef> objectRefs) {
    this(statusType);
    this.objectRefs = objectRefs;
  }

  /**
   * Sends this session response message over the provided ZeroMQ socket.
   *
   * <p>The method sends the message in one or two frames. The first frame always contains the
   * session status as a byte. If object references are provided, they are serialized to a
   * comma-separated string and sent as a second frame.
   *
   * @param socket the ZeroMQ socket over which to send the message; must not be null
   * @return true if all message frames were sent successfully; false otherwise
   * @throws IllegalArgumentException if the socket is null
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;

    // status
    byte[] buff = new byte[] {statusType.toByte()};
    size += buff.length;
    final int sendMoreFlag = objectRefs != null && !objectRefs.isEmpty() ? ZMQ.SNDMORE : 0;
    if (!socket.send(buff, sendMoreFlag)) {
      return false;
    }

    // [objectRefs]
    String objectRefsAsString;
    if (objectRefs != null && !objectRefs.isEmpty()) {
      objectRefsAsString =
          objectRefs.stream().map(ObjectRef::asString).collect(Collectors.joining(","));
      buff = objectRefsAsString.getBytes(ZMQ.CHARSET);
      size += buff.length;
      return socket.send(buff, 0);
    }

    return true;
  }

  /**
   * Receives a session response message from the provided ZeroMQ socket.
   *
   * <p>This method reads the first frame for the session status and, if a subsequent frame is
   * present, it interprets it as a comma-separated list of object references. Depending on the
   * {@code blocking} flag, the method either waits for a message or returns {@code null} if none is
   * available.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @param blocking if {@code true} a blocking read is performed; if {@code false}, the method
   *     returns {@code null} when no message is available
   * @return a SessionResponseMsg instance representing the received message, or {@code null} if
   *     non-blocking and no message is available
   * @throws IllegalArgumentException if the socket is null
   */
  public static SessionResponseMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // status
    int msgSize = buff.length;
    SessionStatusType status = SessionStatusType.fromByte(buff[0]);

    // [objectRefs]
    Set<ObjectRef> objectRefs = null;
    if (socket.hasReceiveMore()) {
      buff = socket.recv();
      msgSize += buff.length;
      objectRefs =
          Arrays.stream(new String(buff, ZMQ.CHARSET).split(","))
              .map(ObjectRef::from)
              .collect(Collectors.toSet());
    }

    return new SessionResponseMsg(status, objectRefs, msgSize);
  }

  /**
   * Receives a session response message from the provided ZeroMQ socket in non-blocking mode.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @return a SessionResponseMsg instance representing the received message, or {@code null} if no
   *     message is available
   * @throws IllegalArgumentException if the socket is null
   * @see #receive(ZMQ.Socket, boolean)
   */
  public static SessionResponseMsg receive(ZMQ.Socket socket) {
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
    SessionResponseMsg that = (SessionResponseMsg) o;
    return statusType == that.statusType && Objects.equals(objectRefs, that.objectRefs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusType, objectRefs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SessionResponseMsg{status=").append(statusType.name());
    if (objectRefs != null) {
      String objectRefsAsString =
          objectRefs.stream().map(ObjectRef::asString).collect(Collectors.joining(","));
      sb.append(", objectRefs=").append(objectRefsAsString);
    }
    sb.append(", size=").append(getSize() == -1 ? "<unknown>" : getSize()).append('}');
    return sb.toString();
  }

  /**
   * Returns the session status type associated with this message.
   *
   * @return the SessionStatusType representing the message's status
   */
  public SessionStatusType getStatus() {
    return statusType;
  }

  /**
   * Returns the set of object references associated with this message.
   *
   * @return a Set of ObjectRef instances, or {@code null} if no object references are present
   */
  @Nullable
  public Set<ObjectRef> getObjectRefs() {
    return objectRefs;
  }
}
