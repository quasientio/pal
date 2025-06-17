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
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.messages.BaseMsg;
import com.quasient.pal.messages.types.SessionCommandType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.zeromq.ZMQ;

/**
 * Represents a session command message used for session control within the PAL runtime.
 *
 * <p>The message frames are arranged as follows:
 *
 * <pre>
 * FRAMES:
 * -------
 * 1. cmd                : byte - SessionCommandType
 * [2. sessionId]        : UUID - peerUuid used as sessionId. Mandatory except for CLEAR_SESSIONS
 * [3. objectRef]        : String - mandatory for STORE_OBJECT and DELETE_OBJECT commands
 * </pre>
 *
 * This structure enables precise communication of session commands between components.
 */
public class SessionCommandMsg extends BaseMsg {
  /**
   * The type of session command represented by this message. This value determines which additional
   * fields are required.
   */
  private final SessionCommandType commandType;

  /**
   * The unique identifier of the session. This must be provided for all command types except {@code
   * CLEAR_SESSIONS}.
   */
  @Nullable private final UUID sessionId;

  /**
   * The object reference associated with the command. This is required for commands {@code
   * STORE_OBJECT} and {@code DELETE_OBJECT}.
   */
  @Nullable private final ObjectRef objectRef;

  /**
   * Constructs a session command message with the specified command type, session identifier, and
   * object reference.
   *
   * <p>Preconditions:
   *
   * <ul>
   *   <li>{@code commandType} must not be null.
   *   <li>For command types other than {@code CLEAR_SESSIONS}, {@code sessionId} must not be null.
   *   <li>For {@code STORE_OBJECT} and {@code DELETE_OBJECT} commands, {@code objectRef} must not
   *       be null.
   * </ul>
   *
   * @param commandType the session command type
   * @param sessionId the session identifier; nullable if the command is {@code CLEAR_SESSIONS}
   * @param objectRef the associated object reference; required for {@code STORE_OBJECT} and {@code
   *     DELETE_OBJECT}
   */
  public SessionCommandMsg(
      @Nonnull SessionCommandType commandType,
      @Nullable UUID sessionId,
      @Nullable ObjectRef objectRef) {
    Objects.requireNonNull(commandType);
    if (!commandType.equals(SessionCommandType.CLEAR_SESSIONS)) {
      Objects.requireNonNull(sessionId);
    }
    if (commandType.equals(SessionCommandType.STORE_OBJECT)
        || commandType.equals(SessionCommandType.DELETE_OBJECT)) {
      Objects.requireNonNull(objectRef);
    }
    this.commandType = commandType;
    this.sessionId = sessionId;
    this.objectRef = objectRef;
  }

  /**
   * Constructs a session command message with the specified command type.
   *
   * <p>This constructor is used when no session identifier or object reference is needed.
   *
   * @param commandType the session command type
   */
  public SessionCommandMsg(@Nonnull SessionCommandType commandType) {
    this(commandType, null, null);
  }

  /**
   * Constructs a session command message with the specified command type and session identifier.
   *
   * <p>This constructor is typically used when an object reference is not required.
   *
   * <p>Preconditions:
   *
   * <ul>
   *   <li>{@code commandType} must not be null.
   *   <li>For command types other than {@code CLEAR_SESSIONS}, {@code sessionId} must not be null.
   * </ul>
   *
   * @param commandType the session command type
   * @param sessionId the session identifier; must be provided if required by the command type
   */
  public SessionCommandMsg(@Nonnull SessionCommandType commandType, UUID sessionId) {
    this(commandType, sessionId, null);
  }

  /**
   * Constructs a session command message with all details including the computed message size.
   *
   * <p>This constructor is primarily used internally when receiving a message from a socket.
   *
   * <p>Preconditions:
   *
   * <ul>
   *   <li>{@code commandType} must not be null.
   *   <li>For command types other than {@code CLEAR_SESSIONS}, {@code sessionId} must not be null.
   *   <li>For {@code STORE_OBJECT} and {@code DELETE_OBJECT} commands, {@code objectRef} must not
   *       be null.
   * </ul>
   *
   * @param commandType the session command type
   * @param sessionId the session identifier; required based on command type
   * @param objectRef the object reference associated with the command
   * @param size the total size of the message as calculated during transmission
   */
  private SessionCommandMsg(
      @Nonnull SessionCommandType commandType, UUID sessionId, ObjectRef objectRef, int size) {
    this(commandType, sessionId, objectRef);
    this.size = size;
  }

  /**
   * Constructs a session command message for commands that do not require a session identifier or
   * object reference, with the specified message size.
   *
   * <p>This constructor is used internally to create a message instance, particularly for the
   * {@code CLEAR_SESSIONS} command.
   *
   * @param commandType the session command type
   * @param size the total size of the message as calculated during transmission
   */
  private SessionCommandMsg(@Nonnull SessionCommandType commandType, int size) {
    this(commandType);
    this.size = size;
  }

  /**
   * Sends this session command message via the provided ZeroMQ socket.
   *
   * <p>The method assembles the message frames based on the command type and available data,
   * ensuring that mandatory frames such as the session identifier and object reference are sent as
   * required.
   *
   * <p>Preconditions:
   *
   * <ul>
   *   <li>{@code socket} must not be null.
   * </ul>
   *
   * @param socket the ZeroMQ socket used for sending the message
   * @return {@code true} if the message is successfully sent on all frames; {@code false} otherwise
   * @throws IllegalArgumentException if the socket is null
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    size = 0;

    // command
    byte[] buff = new byte[] {commandType.toByte()};
    size += buff.length;
    final boolean hasSessionId =
        !commandType.equals(SessionCommandType.CLEAR_SESSIONS) && (sessionId != null);
    if (!socket.send(buff, hasSessionId ? ZMQ.SNDMORE : 0)) {
      return false;
    }

    final boolean hasObjectRef =
        (commandType.equals(SessionCommandType.STORE_OBJECT)
                || commandType.equals(SessionCommandType.DELETE_OBJECT))
            && (objectRef != null);

    // sessionId
    if (hasSessionId) {
      buff = UuidUtils.toBytes(sessionId);
      size += buff.length;
      if (!socket.send(buff, hasObjectRef ? ZMQ.SNDMORE : 0)) {
        return false;
      }
    } else {
      return true;
    }

    // [objectRef]
    if (hasObjectRef) {
      buff = objectRef.asString().getBytes(ZMQ.CHARSET);
      size += buff.length;
      return socket.send(buff, 0);
    }

    return true;
  }

  /**
   * Receives a session command message from the provided ZeroMQ socket.
   *
   * <p>The method reads the message frames sequentially based on the expected structure and
   * constructs an instance of {@code SessionCommandMsg}. In non-blocking mode, if no message is
   * available, the method returns {@code null}.
   *
   * <p>Note: When in non-blocking mode, the blocking flag only applies to the first read. If the
   * first frame is ready, all subsequent frames are assumed to be available.
   *
   * @param socket the ZeroMQ socket from which to receive the message
   * @param blocking if {@code true} the method blocks until a message is available; if {@code
   *     false}, returns {@code null} when no message is available
   * @return a {@code SessionCommandMsg} instance representing the received message, or {@code null}
   *     if in non-blocking mode and no message is available
   * @throws IllegalArgumentException if the socket is null
   */
  public static SessionCommandMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // command
    int msgSize = buff.length;
    SessionCommandType commandType = SessionCommandType.fromByte(buff[0]);

    if (commandType.equals(SessionCommandType.CLEAR_SESSIONS)) {
      return new SessionCommandMsg(commandType, msgSize);
    }

    // sessionId
    buff = socket.recv();
    msgSize += buff.length;
    UUID sessionUuid = UuidUtils.fromBytes(buff);

    // [objectRef]
    ObjectRef objRef = null;
    if (commandType.equals(SessionCommandType.STORE_OBJECT)
        || commandType.equals(SessionCommandType.DELETE_OBJECT)) {
      buff = socket.recv();
      msgSize += buff.length;
      objRef = ObjectRef.from(new String(buff, ZMQ.CHARSET));
    }
    return new SessionCommandMsg(commandType, sessionUuid, objRef, msgSize);
  }

  /**
   * Receives a session command message from the provided ZeroMQ socket in non-blocking mode.
   *
   * <p>This method delegates to {@link #receive(ZMQ.Socket, boolean)} with a {@code blocking} flag
   * of {@code false}.
   *
   * @param socket the ZeroMQ socket from which to receive the message
   * @return a {@code SessionCommandMsg} instance representing the received message, or {@code null}
   *     if no message is available
   * @throws IllegalArgumentException if the socket is null
   */
  public static SessionCommandMsg receive(ZMQ.Socket socket) {
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
    SessionCommandMsg that = (SessionCommandMsg) o;
    return commandType == that.commandType
        && Objects.equals(sessionId, that.sessionId)
        && Objects.equals(objectRef, that.objectRef);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commandType, sessionId, objectRef);
  }

  /**
   * Returns a string representation of the session command message.
   *
   * <p>The representation includes the command type, session identifier, object reference, and the
   * calculated size.
   *
   * @return a string representation of this message
   */
  @Override
  public String toString() {
    return "SessionCommandMsg{"
        + "type="
        + commandType.name()
        + ", sessionID="
        + sessionId
        + ", objectRef="
        + objectRef
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  /**
   * Retrieves the session command type.
   *
   * @return the session command type
   */
  public SessionCommandType getCommand() {
    return commandType;
  }

  /**
   * Retrieves the session identifier associated with this message.
   *
   * @return the session identifier, or {@code null} if not applicable
   */
  @Nullable
  public UUID getSessionId() {
    return sessionId;
  }

  /**
   * Retrieves the object reference associated with this message.
   *
   * @return the object reference, or {@code null} if not applicable
   */
  @Nullable
  public ObjectRef getObjectRef() {
    return objectRef;
  }
}
