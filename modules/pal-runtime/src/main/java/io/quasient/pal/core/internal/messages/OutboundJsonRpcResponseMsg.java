/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.internal.messages;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.BaseMsg;
import io.quasient.pal.messages.types.MessageType;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.zeromq.ZMQ;

/**
 * Represents an outbound JSON-RPC response message formatted for ZeroMQ transmission.
 *
 * <p>The message is composed of three frames:
 *
 * <pre>
 * 1. peerId       : byte[] (UUID of the WebSocket peer)
 * 2. message type : byte[] (MessageType as a single byte)
 * 3. message      : byte[] (JSON-RPC response content as String)
 * </pre>
 *
 * <p>This class encapsulates the details of a JSON-RPC response, including the peer identifier, the
 * message content, and its type, and provides methods to send and receive the message via ZMQ.
 */
public class OutboundJsonRpcResponseMsg extends BaseMsg {

  // fields

  /** Unique identifier of the peer (WebSocket client) associated with this message. */
  private final UUID peerId;

  /** JSON-RPC response content represented as a String. */
  private final String jsonMessage;

  /** Message type indicating the nature or handling required for the JSON-RPC response. */
  private final MessageType messageType;

  /**
   * Constructs an {@code OutboundJsonRpcResponseMsg} with the specified peer identifier, JSON
   * message, and message type.
   *
   * @param peerId a non-null UUID identifying the WebSocket peer; must not be null
   * @param message a non-null JSON-RPC response string; must not be null
   * @param messageType the type of the message indicating how the response should be interpreted
   */
  public OutboundJsonRpcResponseMsg(UUID peerId, String message, MessageType messageType) {
    Stream.of(peerId, message).forEach(Objects::requireNonNull);
    this.peerId = peerId;
    this.jsonMessage = message;
    this.messageType = messageType;
  }

  /**
   * Internal constructor that creates an {@code OutboundJsonRpcResponseMsg} instance with a
   * precomputed size.
   *
   * <p>This constructor is used during message reception to associate the calculated message size
   * with the instance.
   *
   * @param peerId a non-null UUID identifying the WebSocket peer
   * @param message a non-null JSON-RPC response string
   * @param messageType the type of the message indicating its handling
   * @param size the total size in bytes of the serialized message frames as calculated during
   *     reception
   */
  private OutboundJsonRpcResponseMsg(
      UUID peerId, String message, MessageType messageType, int size) {
    this(peerId, message, messageType);
    this.size = size;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sends the JSON-RPC response message over the specified ZeroMQ socket. The message is sent in
   * three sequential frames:
   *
   * <ol>
   *   <li>The peer identifier converted to a byte array.
   *   <li>The message type represented as a single-byte array.
   *   <li>The JSON-RPC response message content as a byte array.
   * </ol>
   *
   * @param socket the ZeroMQ socket through which the message is to be sent; must not be null
   * @return {@code true} if all message frames are successfully sent; {@code false} otherwise
   * @throws IllegalArgumentException if the provided socket is null
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    // peerId
    byte[] buff = UuidUtils.toBytes(peerId);
    size = buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // message type
    buff = new byte[] {messageType.getId()};
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    size += buff.length;

    // message
    buff = jsonMessage.getBytes(ZMQ.CHARSET);
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Receives an outbound JSON-RPC response message from the provided ZeroMQ socket.
   *
   * <p>This method performs message reception by reading consecutive frames:
   *
   * <ul>
   *   <li>An initial empty envelope (identity) frame which is discarded.
   *   <li>A frame containing the peer identifier, which is converted from bytes to a UUID.
   *   <li>A frame containing the message type, where the first byte indicates the type.
   *   <li>A frame containing the JSON-RPC response content as a byte array, converted to a String.
   * </ul>
   *
   * The {@code blocking} flag only applies to the first read; if the first frame is available, the
   * remaining frames are assumed to be ready.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @param blocking {@code true} for a blocking read, {@code false} for a non-blocking read that
   *     returns {@code null} if no message is available
   * @return an instance of {@code OutboundJsonRpcResponseMsg} if a message is received, or {@code
   *     null} when in non-blocking mode and no message is available
   * @throws IllegalArgumentException if the provided socket is null
   */
  public static OutboundJsonRpcResponseMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;

    // read and discard empty envelope (identity frame)
    socket.recv(flag);

    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    int msgSize = buff.length;

    // peerId
    final UUID peerId = UuidUtils.fromBytes(buff);

    // message type
    buff = socket.recv();
    MessageType messageType = MessageType.fromId(buff[0]);
    msgSize += buff.length;

    // message body
    buff = socket.recv();
    msgSize += buff.length;
    final String message = new String(buff, ZMQ.CHARSET);

    return new OutboundJsonRpcResponseMsg(peerId, message, messageType, msgSize);
  }

  /**
   * Receives an outbound JSON-RPC response message from the provided ZeroMQ socket using
   * non-blocking semantics.
   *
   * <p>This convenience method internally invokes {@link #receive(ZMQ.Socket, boolean)} with the
   * blocking parameter set to {@code false}.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null
   * @return an instance of {@code OutboundJsonRpcResponseMsg} if a message is available; or {@code
   *     null} if no message is received under non-blocking conditions
   * @throws IllegalArgumentException if the provided socket is null
   */
  public static OutboundJsonRpcResponseMsg receive(ZMQ.Socket socket) {
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
    OutboundJsonRpcResponseMsg that = (OutboundJsonRpcResponseMsg) o;
    return Objects.equals(peerId, that.peerId) && Objects.equals(jsonMessage, that.jsonMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId, jsonMessage);
  }

  @Override
  public String toString() {
    return "OutboundJsonRpcResponseMsg{peerId=" + peerId + ", message=" + jsonMessage + '}';
  }

  /**
   * Retrieves the unique identifier of the peer associated with this message.
   *
   * @return the UUID representing the WebSocket peer
   */
  public UUID getPeerId() {
    return peerId;
  }

  /**
   * Retrieves the JSON-RPC response message.
   *
   * @return the String containing the JSON message
   */
  public String getJsonMessage() {
    return jsonMessage;
  }

  /**
   * Retrieves the type of the message.
   *
   * @return the {@link MessageType} indicating the message's intended handling
   */
  public MessageType getMessageType() {
    return messageType;
  }
}
