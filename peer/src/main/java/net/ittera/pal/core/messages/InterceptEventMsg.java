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

import static net.ittera.pal.serdes.colfer.ColferUtils.toBytes;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import net.ittera.pal.messages.BaseMsg;
import net.ittera.pal.messages.Marshallable;
import org.zeromq.ZMQ;

/**
 * Represents an intercept event message used to register or unregister an intercept. For a REGISTER
 * event, the message carries a payload (either as a byte array or generated from a Marshallable
 * object); for an UNREGISTER event, an intercept message identifier is sent. The message is
 * transmitted as a multi-part ZeroMQ message where the first frame identifies the event type, and
 * the second frame contains the payload or identifier.
 */
public class InterceptEventMsg extends BaseMsg {

  /**
   * Enumerates the types of intercept events. A REGISTER event is used to register a new intercept
   * message (using a payload), whereas an UNREGISTER event is used to cancel a previously
   * registered intercept (using a message identifier).
   *
   * <pre>
   * FRAMES:
   * -------
   * 1. type       : byte (event type: REGISTER/UNREGISTER)
   * 2. body/msgId : byte[] (payload for REGISTER, or message identifier for UNREGISTER)
   * </pre>
   *
   * The methods {@link #toByte()} and {@link #fromByte(byte)} handle the conversion between the
   * enumeration and its byte representation.
   */
  public enum Type {
    /**
     * Indicates a REGISTER intercept event. In this mode, a non-null payload (body) is expected to
     * be provided.
     */
    REGISTER((byte) 1),
    /**
     * Indicates an UNREGISTER intercept event. In this mode, the interceptMessageId must be
     * provided.
     */
    UNREGISTER((byte) 2);

    private final byte idx;

    /**
     * Constructs a new intercept event type with the specified byte representation.
     *
     * @param idx the byte value representing the type.
     */
    Type(byte idx) {
      this.idx = idx;
    }

    /**
     * Converts a byte value to its corresponding intercept event type.
     *
     * @param typeAsByte the byte representation of the intercept event type.
     * @return the {@code Type} corresponding to the given byte.
     * @throws IllegalArgumentException if the byte does not match any valid type.
     */
    public static Type fromByte(byte typeAsByte) {
      return switch (typeAsByte) {
        case 1 -> REGISTER;
        case 2 -> UNREGISTER;
        default -> throw new IllegalArgumentException("Unknown type: " + typeAsByte);
      };
    }

    /**
     * Returns the byte representation of this intercept event type.
     *
     * @return the byte value associated with this type.
     */
    public byte toByte() {
      return idx;
    }
  }

  /** The type of intercept event representing a registration or unregistration. */
  private final Type type;

  /**
   * The identifier for the intercept message, used when the event is an unregistration. This field
   * is null for registration events.
   */
  @Nullable private final String interceptMessageId;

  /**
   * The payload of the intercept event, used when registering an intercept message. This field is
   * null for unregistration events.
   */
  @Nullable private final byte[] body;

  /**
   * Creates an intercept event message for registering an intercept using the provided payload. The
   * message type is implicitly set to REGISTER.
   *
   * @param body the byte array representing the intercept message payload; must not be null.
   * @throws NullPointerException if the provided payload is null.
   */
  public InterceptEventMsg(byte[] body) {
    this(Type.REGISTER, body, null, null);
  }

  /**
   * Constructs an intercept event message for registering an intercept using a Marshallable object.
   * The object is converted to its byte representation for transmission. The message type is
   * implicitly set to REGISTER.
   *
   * @param message the Marshallable object containing intercept information; must not be null.
   * @throws NullPointerException if the resulting payload is null.
   */
  public InterceptEventMsg(Marshallable message) {
    this(Type.REGISTER, null, message, null);
  }

  /**
   * Creates an intercept event message for unregistering an intercept using the provided message
   * identifier. The message type is implicitly set to UNREGISTER.
   *
   * @param interceptMessageId the identifier of the intercept message to unregister; must not be
   *     null.
   * @throws NullPointerException if interceptMessageId is null.
   */
  public InterceptEventMsg(String interceptMessageId) {
    this(Type.UNREGISTER, null, null, interceptMessageId);
  }

  /**
   * Internal constructor to create an intercept event message with the specified parameters. For a
   * REGISTER event, either a byte array payload or a Marshallable object must be provided. For an
   * UNREGISTER event, the interceptMessageId must be non-null. If a Marshallable is provided, it is
   * converted into a byte array.
   *
   * @param type the type of intercept event (REGISTER or UNREGISTER).
   * @param body the byte array payload for registration; may be null if a Marshallable is provided.
   * @param marshallable a Marshallable object representing the intercept payload, alternative to
   *     {@code body}.
   * @param interceptMessageId the identifier for unregistration; required when {@code type} is
   *     UNREGISTER.
   * @throws NullPointerException if required fields are null based on the event type.
   */
  private InterceptEventMsg(
      Type type,
      @Nullable byte[] body,
      @Nullable Marshallable marshallable,
      @Nullable String interceptMessageId) {
    if (type.equals(Type.REGISTER) && (body == null && marshallable == null)) {
      throw new NullPointerException("Both body and marshallable are null.");
    }

    if (type.equals(Type.UNREGISTER)) {
      Objects.requireNonNull(interceptMessageId);
    }

    this.type = type;
    this.body = marshallable != null ? toBytes(marshallable) : body;
    this.interceptMessageId = interceptMessageId;
  }

  /**
   * Internal constructor that initializes an intercept event message and assigns its total size.
   *
   * @param type the type of intercept event.
   * @param body the payload for a REGISTER event.
   * @param interceptMessageId the identifier for an UNREGISTER event.
   * @param size the total size of the message in bytes.
   */
  private InterceptEventMsg(Type type, byte[] body, @Nullable String interceptMessageId, int size) {
    this(type, body, null, interceptMessageId);
    this.size = size;
  }

  /**
   * Sends the intercept event message over the specified ZeroMQ socket. The message is transmitted
   * as a multi-part message; the first frame contains the event type, and the second frame contains
   * either the payload (for REGISTER) or the message identifier (for UNREGISTER).
   *
   * @param socket the ZeroMQ socket to which the message is sent; must not be null.
   * @return {@code true} if the message was sent successfully, {@code false} otherwise.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    byte[] buff = new byte[] {type.toByte()};
    size = 1;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    if (type.equals(Type.REGISTER)) {
      buff = body;
    } else { // (type.equals(Type.UNREGISTER))
      buff = interceptMessageId.getBytes(ZMQ.CHARSET);
    }
    assert buff != null;
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Receives an intercept event message from the specified ZeroMQ socket. The method reads two
   * frames: the first determines the type of event, and the second carries either the payload for
   * registration or the message identifier for unregistration. In non-blocking mode, if no message
   * is available, {@code null} is returned.
   *
   * @param socket the ZeroMQ socket from which the message is received; must not be null.
   * @param blocking if {@code true}, waits for a message; if {@code false}, operates in
   *     non-blocking mode.
   * @return the received {@code InterceptEventMsg} instance, or {@code null} if in non-blocking
   *     mode and no message is available.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  public static InterceptEventMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }

    // type
    int msgSize = buff.length;
    Type type = Type.fromByte(buff[0]);

    // body | msgId
    buff = socket.recv();
    msgSize += buff.length;
    byte[] body = null;
    String interceptMsgId = null;
    if (type.equals(Type.REGISTER)) {
      body = buff;
    } else { // UNREGISTER
      interceptMsgId = new String(buff, ZMQ.CHARSET);
    }
    return new InterceptEventMsg(type, body, interceptMsgId, msgSize);
  }

  /**
   * Receives an intercept event message from the specified ZeroMQ socket in non-blocking mode.
   *
   * @param socket the ZeroMQ socket from which the message is received; must not be null.
   * @return the received {@code InterceptEventMsg} instance, or {@code null} if no message is
   *     available.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  public static InterceptEventMsg receive(ZMQ.Socket socket) {
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
    InterceptEventMsg that = (InterceptEventMsg) o;
    return type == that.type
        && Objects.equals(interceptMessageId, that.interceptMessageId)
        && Arrays.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(type, interceptMessageId);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  @Override
  public String toString() {
    return "InterceptEventMsg{"
        + "type="
        + type
        + ", interceptMsgId="
        + interceptMessageId
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  /**
   * Retrieves the type of this intercept event message.
   *
   * @return the event type, either REGISTER or UNREGISTER.
   */
  public Type getType() {
    return type;
  }

  /**
   * Retrieves the intercept message identifier for an UNREGISTER event.
   *
   * @return the intercept message identifier, or {@code null} if this is a REGISTER event.
   */
  @Nullable
  public String getInterceptMessageId() {
    return interceptMessageId;
  }

  /**
   * Retrieves the payload of the intercept event message for a REGISTER event.
   *
   * @return a byte array containing the payload, or {@code null} if this is an UNREGISTER event.
   */
  @Nullable
  public byte[] getBody() {
    return body;
  }
}
