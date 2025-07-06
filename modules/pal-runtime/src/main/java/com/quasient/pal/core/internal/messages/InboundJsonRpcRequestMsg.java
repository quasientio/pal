/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.internal.messages;

import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.messages.BaseMsg;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.zeromq.ZMQ;

/**
 * This message is sent directly by DEALER, so it needs to emulate a REQ envelope (empty initial
 * frame) when serializing, and NOT expect it when deserializing.
 *
 * <p>The envelope is optional because the message is also sent and received through a PUSH/PULL
 * socket pair internally by JSONRPCRequestDispatcher, which does not require an envelope.
 *
 * <p>Instances of this class encapsulate an inbound JSON-RPC request, including the sender's peer
 * identifier and the JSON message payload.
 *
 * <pre>
 * FRAMES:
 * -------
 * 0 [empty REQ envelope]: ""
 * 1. peerId             : byte[] (UUID of WebSocket peer)
 * 2. message            : byte[] (JSON-RPC request)
 * </pre>
 */
public class InboundJsonRpcRequestMsg extends BaseMsg {

  /** The unique identifier of the peer (e.g. WebSocket client) that sent this JSON-RPC request. */
  private final UUID peerId;

  /** The JSON-RPC request represented as a JSON-formatted string. */
  private final String jsonMessage;

  /**
   * Constructs a new InboundJsonRpcRequestMsg with the specified peer identifier and JSON message.
   *
   * @param peerId the unique identifier of the sending peer; must not be null.
   * @param message the JSON-RPC request as a string; must not be null.
   * @throws NullPointerException if either peerId or message is null.
   */
  public InboundJsonRpcRequestMsg(UUID peerId, String message) {
    Stream.of(peerId, message).forEach(Objects::requireNonNull);
    this.peerId = peerId;
    this.jsonMessage = message;
  }

  /**
   * Internal constructor used during message reception to create an instance with a predefined
   * size.
   *
   * @param peerId the unique identifier of the sending peer; must not be null.
   * @param message the JSON-RPC request as a string; must not be null.
   * @param size the total size in bytes of the received message frames.
   */
  private InboundJsonRpcRequestMsg(UUID peerId, String message, int size) {
    this(peerId, message);
    this.size = size;
  }

  /**
   * Sends this message using the provided ZeroMQ socket with the default envelope inclusion.
   *
   * @param socket the ZeroMQ socket used for message transmission; must not be null.
   * @return true if all message frames were successfully sent, false otherwise.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    return send(socket, true);
  }

  /**
   * Sends this inbound JSON-RPC request message using the specified ZeroMQ socket. It optionally
   * prefixes the message with an empty frame to emulate a REQ envelope.
   *
   * @param socket the ZeroMQ socket used to send the message; must not be null.
   * @param withEnvelope if true, an empty frame is sent first to mimic a REQ envelope.
   * @return true if the message was sent completely; false otherwise.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  public boolean send(ZMQ.Socket socket, boolean withEnvelope) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    // emulate empty REQ envelope since this message is sent directly by a DEALER
    if (withEnvelope && !socket.send("", ZMQ.SNDMORE)) {
      return false;
    }
    // peerId
    byte[] buff = UuidUtils.toBytes(peerId);
    size = buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }
    // message
    buff = jsonMessage.getBytes(ZMQ.CHARSET);
    size += buff.length;
    return socket.send(buff, 0);
  }

  /**
   * Receives an inbound JSON-RPC request message from the provided ZeroMQ socket. The blocking flag
   * applies only to the first frame read; if available, the remaining frames are read atomically.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null.
   * @param blocking if true, the call blocks until at least the first frame is available;
   *     otherwise, returns null when no message is available.
   * @return an InboundJsonRpcRequestMsg instance containing the received data, or null if no
   *     message is available in non-blocking mode.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  public static InboundJsonRpcRequestMsg receive(ZMQ.Socket socket, boolean blocking) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }
    int flag = blocking ? 0 : ZMQ.DONTWAIT;
    byte[] buff = socket.recv(flag);
    if (!blocking && buff == null) {
      return null;
    }
    int msgSize = buff.length;

    // peerId
    final UUID peerId = UuidUtils.fromBytes(buff);

    // message body
    buff = socket.recv();
    msgSize += buff.length;
    final String message = new String(buff, ZMQ.CHARSET);

    return new InboundJsonRpcRequestMsg(peerId, message, msgSize);
  }

  /**
   * Receives an inbound JSON-RPC request message using a non-blocking call.
   *
   * @param socket the ZeroMQ socket from which to receive the message; must not be null.
   * @return an InboundJsonRpcRequestMsg instance with the received data, or null if no message is
   *     available.
   * @throws IllegalArgumentException if the provided socket is null.
   */
  public static InboundJsonRpcRequestMsg receive(ZMQ.Socket socket) {
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
    InboundJsonRpcRequestMsg that = (InboundJsonRpcRequestMsg) o;
    return Objects.equals(peerId, that.peerId) && Objects.equals(jsonMessage, that.jsonMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId, jsonMessage);
  }

  @Override
  public String toString() {
    return "InboundJsonRpcRequestMsg{peerId=" + peerId + ", message=" + jsonMessage + '}';
  }

  /**
   * Returns the unique identifier of the peer that sent the JSON-RPC request.
   *
   * @return the UUID of the sending peer.
   */
  public UUID getPeerId() {
    return peerId;
  }

  /**
   * Returns the JSON text of the JSON-RPC request.
   *
   * @return the JSON-RPC request message as a string.
   */
  public String getJsonMessage() {
    return jsonMessage;
  }
}
