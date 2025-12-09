/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

import static com.quasient.pal.serdes.colfer.ColferUtils.toBytes;

import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.types.MessageType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * Represents an outbound message in the Pal's runtime.
 *
 * <p>This class is responsible for constructing and sending messages through ZeroMQ sockets. It
 * encapsulates the message type, execution phase, headers, identifiers, and body.
 *
 * <pre>
 * ZMQ FRAMES:
 * ----------
 * 1. type of message    : int (MessageType)
 * 2. [execution phase]  : int (ExecPhase: Undefined if MessageType != ExecMessage)
 * 3. headers to follow  : int
 * 4. [headers]          : byte[]* (InternalHeader)
 * 5. message id         : byte[]
 * 6. responseToId       : byte[]
 * 7. message body       : byte[]
 * </pre>
 *
 * <pre>
 * Chronicle Queue binary format:
 * -----------------------------
 *  0. type          : byte
 *  1. bodyLen       : int
 *  2. body          : byte[]
 * </pre>
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Message wrapper - direct array access for performance in serialization")
public class OutboundMsg extends BaseMsg {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(OutboundMsg.class);

  /** The type of the message. */
  private final MessageType messageType;

  /** The execution phase associated with the message. */
  @Nullable private final ExecPhase execPhase;

  /** The list of headers associated with the message. */
  @Nullable private final List<InternalHeader> headers;

  /** The unique identifier for this message. */
  @Nullable private final String messageId;

  /** The identifier of the message to which this message is a response. */
  @Nullable private final String responseToId;

  /** The body of the message as a byte array. */
  private final byte[] body;

  /**
   * Constructs an OutboundMsg instance with the specified parameters. This constructor is primarily
   * used for unit testing.
   *
   * @param messageType the type of the message
   * @param execPhase the execution phase of the message
   * @param headers the headers associated with the message, may be {@code null}
   * @param messageId the unique identifier for the message
   * @param responseToId the identifier of the message to which this message is a response, may be
   *     {@code null}
   * @param body the body of the message as a byte array
   * @throws NullPointerException if any of {@code messageType}, {@code execPhase}, {@code
   *     messageId}, or {@code body} is {@code null}
   */
  OutboundMsg(
      MessageType messageType,
      @Nullable ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      @Nullable String messageId,
      @Nullable String responseToId,
      byte[] body) {

    Objects.requireNonNull(messageType);
    Objects.requireNonNull(body);
    this.messageType = messageType;
    this.execPhase = execPhase;
    this.headers = headers;
    this.messageId = messageId;
    this.responseToId = responseToId;
    this.body = body;
  }

  /**
   * Constructs an OutboundMsg instance with the specified parameters and marshalls the provided
   * object.
   *
   * @param messageType the type of the message
   * @param execPhase the execution phase of the message
   * @param headers the headers associated with the message, may be {@code null}
   * @param messageId the unique identifier for the message
   * @param responseToId the identifier of the message to which this message is a response, may be
   *     {@code null}
   * @param marshallable the object to be marshalled into the message body, must not be {@code null}
   * @throws NullPointerException if any of {@code messageType}, {@code execPhase}, {@code
   *     messageId}, or {@code marshallable} is {@code null}
   */
  public OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      String messageId,
      @Nullable String responseToId,
      Marshallable marshallable) {

    Stream.of(messageType, execPhase, messageId, marshallable).forEach(Objects::requireNonNull);
    this.messageType = messageType;
    this.execPhase = execPhase;
    this.headers = headers;
    this.messageId = messageId;
    this.responseToId = responseToId;
    this.body = toBytes(marshallable);
  }

  /**
   * Constructs an OutboundMsg instance with the specified parameters and body size.
   *
   * @param messageType the type of the message
   * @param execPhase the execution phase of the message
   * @param headers the headers associated with the message, may be {@code null}
   * @param messageId the unique identifier for the message
   * @param responseToId the identifier of the message to which this message is a response, may be
   *     {@code null}
   * @param body the body of the message as a byte array
   * @param size the size of the message in bytes
   */
  private OutboundMsg(
      MessageType messageType,
      ExecPhase execPhase,
      @Nullable List<InternalHeader> headers,
      String messageId,
      @Nullable String responseToId,
      byte[] body,
      int size) {
    this(messageType, execPhase, headers, messageId, responseToId, body);
    this.size = size;
  }

  /**
   * Constructs an OutboundMsg instance with the specified type and body. This constructor is used
   * for re-creating messages read from Chronicle queue.
   *
   * @param messageType the type of the message
   * @param body the body of the message as a byte array
   */
  OutboundMsg(MessageType messageType, byte[] body) {
    this(messageType, null, null, null, null, body, body.length + 1);
  }

  /**
   * Sends the outbound message through the specified ZeroMQ socket.
   *
   * @param socket the ZeroMQ socket to send the message through, must not be {@code null}
   * @return {@code true} if the message was sent successfully; {@code false} otherwise
   * @throws IllegalArgumentException if the provided socket is {@code null}
   */
  @Override
  public boolean send(ZMQ.Socket socket) {
    if (socket == null) {
      throw new IllegalArgumentException("Socket is null");
    }

    // assert nullable fields be present for zmq send
    Objects.requireNonNull(execPhase);
    Objects.requireNonNull(messageId);

    // type of message
    byte[] buff = new byte[] {messageType.getId()};
    size = 1;
    try {
      if (!socket.send(buff, ZMQ.SNDMORE)) {
        return false;
      }
    } catch (ZMQException e) {
      logger.error("Error sending message", e);
      return false;
    }

    // execution phase
    buff = new byte[] {execPhase.toByte()};
    size++;
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
      for (InternalHeader header : headers) {
        buff = toBytes(header);
        size += buff.length;
        if (!socket.send(buff, ZMQ.SNDMORE)) {
          return false;
        }
      }
    }

    // message id
    buff = messageId.getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // responseToId
    buff = responseToId == null ? "0".getBytes(ZMQ.CHARSET) : responseToId.getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, ZMQ.SNDMORE)) {
      return false;
    }

    // message body
    size += body.length;
    return socket.send(body, 0);
  }

  /**
   * Sends the outbound message through the specified ZeroMQ socket. Non-blocking version: send
   * every frame with (flags | ZMQ.SNDMORE) except the last one, which uses only the supplied flags.
   *
   * @param socket the ZeroMQ socket to send the message through, must not be {@code null}
   * @return {@code true} if the message was sent successfully; {@code false} otherwise
   * @throws IllegalArgumentException if the provided socket is {@code null}
   * @throws ZMQException on zmq socket error
   */
  public boolean send(ZMQ.Socket socket, int flags) throws IllegalArgumentException, ZMQException {
    if (socket == null) throw new IllegalArgumentException("Socket is null");

    // assert nullable fields be present for zmq send
    Objects.requireNonNull(execPhase);
    Objects.requireNonNull(messageId);

    int more = flags | ZMQ.SNDMORE; // convenience

    // 1) message type
    byte[] buff = {messageType.getId()};
    size = 1;
    if (!socket.send(buff, more)) return false;

    // 2) exec phase
    buff = new byte[] {execPhase.toByte()};
    size++;
    if (!socket.send(buff, more)) return false;

    // 3) #headers
    final int hdrCnt = headers != null ? headers.size() : 0;
    buff = Integer.toString(hdrCnt).getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, more)) return false;

    // 4) headers themselves
    if (hdrCnt > 0) {
      for (InternalHeader h : headers) {
        buff = toBytes(h);
        size += buff.length;
        if (!socket.send(buff, more)) return false;
      }
    }

    // 5) message-id
    buff = messageId.getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, more)) return false;

    // 6) response-to-id
    buff = responseToId == null ? "0".getBytes(ZMQ.CHARSET) : responseToId.getBytes(ZMQ.CHARSET);
    size += buff.length;
    if (!socket.send(buff, more)) return false;

    // 7) body  (last frame – **no** SNDMORE)
    size += body.length;
    return socket.send(body, flags);
  }

  /**
   * Receives an outbound message from the specified ZeroMQ socket.
   *
   * @param socket the ZeroMQ socket to receive the message from, must not be {@code null}
   * @param blocking {@code true} to block until a message is received; {@code false} to return
   *     immediately if no message is available
   * @return an {@code OutboundMsg} instance representing the received message, or {@code null} if
   *     no message was received and {@code blocking} is {@code false}
   * @throws IllegalArgumentException if the provided socket is {@code null}
   */
  public static OutboundMsg receive(ZMQ.Socket socket, boolean blocking) {
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
    final MessageType messageType = MessageType.fromId(buff[0]);
    // execution phase
    final ExecPhase execPhase;
    buff = socket.recv();
    msgSize += buff.length;
    execPhase = ExecPhase.fromByte(buff[0]);

    // # of headers to follow
    buff = socket.recv();
    msgSize += buff.length;
    final int headerCount = Integer.parseInt(new String(buff, ZMQ.CHARSET));
    // headers
    final List<InternalHeader> headers;
    if (headerCount > 0) {
      headers = new ArrayList<>();
      for (int i = 0; i < headerCount; i++) {
        buff = socket.recv();
        msgSize += buff.length;
        InternalHeader internalHeader = new InternalHeader();
        internalHeader.unmarshal(buff, 0);
        headers.add(internalHeader);
      }
    } else {
      headers = null;
    }

    // message id
    buff = socket.recv();
    msgSize += buff.length;
    final String messageId = new String(buff, ZMQ.CHARSET);

    // responseToId
    buff = socket.recv();
    msgSize += buff.length;
    final String responseToId;
    if (!"0".equals(new String(buff, ZMQ.CHARSET))) {
      responseToId = new String(buff, ZMQ.CHARSET);
    } else {
      responseToId = null;
    }

    // message body
    final byte[] body = socket.recv();
    msgSize += body.length;

    return new OutboundMsg(messageType, execPhase, headers, messageId, responseToId, body, msgSize);
  }

  /**
   * Write this message directly into Chronicle using a single document.
   *
   * @param appender the excerpt appender
   * @return the index of the newly written message
   * @throws IllegalStateException if the DocumentContext wire is null
   */
  public long appendTo(ExcerptAppender appender) {
    final DocumentContext dc =
        appender.writingDocument(); // don't use try-with-resources: we may need rollback
    try {
      final var wire = dc.wire();
      if (wire == null) {
        throw new IllegalStateException("DocumentContext wire is null");
      }
      final Bytes<?> out = wire.bytes(); // direct view on the mapped region

      // [0] type          : byte
      out.writeByte(messageType.getId());

      // [..] bodyLen : int, then body : bytes
      out.writeInt(body.length);
      out.write(body);

      // commit by closing
    } catch (Throwable t) {
      dc.rollbackOnClose(); // ensure partial writes are discarded
      throw t;
    } finally {
      dc.close();
    }

    // safe after commit
    return appender.lastIndexAppended();
  }

  /**
   * Low-level: write just the payload to an existing Bytes (useful for tests).
   *
   * @param out the Bytes sinc
   */
  public void writeTo(Bytes<?> out) {
    out.writeByte(messageType.getId());
    out.writeInt(body.length);
    out.write(body);
  }

  /**
   * Read the next Chronicle document and build an OutboundMsg (returns null if none present).
   *
   * @param tailer the queue tailer/reader
   * @return a new {@link OutboundMsg} or null if none available
   * @throws IllegalStateException if the DocumentContext wire is null
   * @throws IORuntimeException if body length is negative or data is truncated
   */
  @Nullable
  public static OutboundMsg readNext(ExcerptTailer tailer) {
    try (DocumentContext dc = tailer.readingDocument()) {
      if (!dc.isPresent()) return null;
      final var wire = dc.wire();
      if (wire == null) {
        throw new IllegalStateException("DocumentContext wire is null");
      }
      final Bytes<?> in = wire.bytes();

      // [0] type
      final byte typeId = in.readByte();
      final MessageType msgType = MessageType.fromId(typeId);

      // [..] bodyLen
      final int bodyLen = in.readInt();
      if (bodyLen < 0) {
        throw new IORuntimeException("Negative body length: " + bodyLen);
      }

      // Ensure we actually have that many bytes in this doc
      final long remaining = in.readRemaining();
      if (remaining < bodyLen) {
        throw new IORuntimeException(
            "Truncated body: expected " + bodyLen + " bytes, remaining " + remaining);
      }

      // [..] body
      final byte[] body = new byte[bodyLen];
      in.read(body, 0, bodyLen);

      return new OutboundMsg(msgType, body);
    }
  }

  /**
   * Receives an outbound message from the specified ZeroMQ socket in a non-blocking manner.
   *
   * @param socket the ZeroMQ socket to receive the message from, must not be {@code null}
   * @return an {@code OutboundMsg} instance representing the received message, or {@code null} if
   *     no message was received
   * @throws IllegalArgumentException if the provided socket is {@code null}
   */
  public static OutboundMsg receive(ZMQ.Socket socket) {
    return receive(socket, false);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OutboundMsg that = (OutboundMsg) o;
    return messageType == that.messageType
        && Objects.equals(execPhase, that.execPhase)
        && Objects.equals(headers, that.headers)
        && Objects.equals(messageId, that.messageId)
        && Objects.equals(responseToId, that.responseToId)
        && Arrays.equals(body, that.body);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = Objects.hash(messageType, execPhase, headers, messageId, responseToId);
    result = 31 * result + Arrays.hashCode(body);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "OutboundMsg{"
        + "messageType="
        + messageType
        + ", execPhase="
        + execPhase
        + ", headers="
        + headers
        + ", messageId="
        + messageId
        + ", responseToId="
        + responseToId
        + ", body="
        + Arrays.toString(body)
        + ", size="
        + (getSize() == -1 ? "<unknown>" : getSize())
        + '}';
  }

  /**
   * Returns the type of the message.
   *
   * @return the message type
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Returns the execution phase of the message.
   *
   * @return the execution phase, or {@code null} if not applicable
   */
  @Nullable
  public ExecPhase getExecPhase() {
    return execPhase;
  }

  /**
   * Returns the headers associated with the message.
   *
   * @return the list of internal headers, or {@code null} if no headers are present
   */
  @Nullable
  public List<InternalHeader> getHeaders() {
    return headers;
  }

  /**
   * Returns the unique identifier of the message.
   *
   * @return the message identifier
   */
  @Nullable
  public String getMessageId() {
    return messageId;
  }

  /**
   * Returns the identifier of the message to which this message is a response.
   *
   * @return the response-to message identifier, or {@code null} if not applicable
   */
  @Nullable
  public String getResponseToId() {
    return responseToId;
  }

  /**
   * Returns the body of the message.
   *
   * @return the message body as a byte array
   */
  public byte[] getBody() {
    return body;
  }
}
