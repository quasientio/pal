/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.WireType;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class OutboundMsgTest {
  private final MessageBuilder messageBuilder = new MessageBuilder();
  private static final Logger logger = LoggerFactory.getLogger("tests");

  protected ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  @Test
  public void sendWithNullables() {
    String execMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    ExecPhase execPhase = ExecPhase.BEFORE;

    // with null headers and responseToId
    OutboundMsg msgOut =
        new OutboundMsg(MessageType.EXEC_CONSTRUCTOR, execPhase, null, execMessageId, null, body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.EXEC_CONSTRUCTOR));
    assertThat(msgOut.getExecPhase(), is(execPhase));
    assertThat(msgOut.getHeaders(), is(nullValue()));
    assertThat(msgOut.getMessageId(), is(execMessageId));
    assertThat(msgOut.getResponseToId(), is(nullValue()));
    assertThat(msgOut.getBody(), is(body));

    // send
    String zmqEndpoint = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(zmqEndpoint);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(zmqEndpoint);
    msgOut.send(out);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void send() throws UnsupportedEncodingException {
    String interceptMessageId = UUID.randomUUID().toString();
    String followingMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    // with all values filled
    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.UNDEFINED,
            headers,
            interceptMessageId,
            followingMessageId,
            body);

    // verify getters
    assertThat(msgOut.getMessageType(), is(MessageType.INTERCEPT_MESSAGE));
    assertThat(msgOut.getExecPhase(), is(ExecPhase.UNDEFINED));
    assertThat(msgOut.getHeaders(), is(notNullValue()));
    assertThat(msgOut.getHeaders().size(), is(1));
    assertThat(msgOut.getHeaders().get(0), is(writeAhead));
    assertThat(msgOut.getMessageId(), is(interceptMessageId));
    assertThat(msgOut.getResponseToId(), is(followingMessageId));
    assertThat(msgOut.getBody(), is(body));

    // send
    String zmqEndpoint = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(zmqEndpoint);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(zmqEndpoint);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    OutboundMsg msgIn = OutboundMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testNullPointerException() {
    String messageId = UUID.randomUUID().toString();
    String responseToId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    List<InternalHeader> headers = Collections.emptyList();

    // null messageType
    try {
      new OutboundMsg(null, ExecPhase.UNDEFINED, headers, messageId, responseToId, body);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }

    // null body
    try {
      new OutboundMsg(
          MessageType.INTERCEPT_MESSAGE,
          ExecPhase.UNDEFINED,
          headers,
          messageId,
          responseToId,
          (byte[]) null);
      fail("Should have thrown NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    String messageId = UUID.randomUUID().toString();
    String responseToMessageId = UUID.randomUUID().toString();
    byte[] body = "whatever".getBytes(UTF_8);
    InternalHeader writeAhead = messageBuilder.buildWriteAheadHeader(UUID.randomUUID());
    List<InternalHeader> headers = Collections.singletonList(writeAhead);

    OutboundMsg msg1 =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body);

    // equal
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body),
        is(msg1));

    // different type
    assertThat(
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            body),
        is(not(msg1)));

    // different headers
    List<InternalHeader> otherHeaders =
        Arrays.asList(writeAhead, writeAhead); // just duplicate the header
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            otherHeaders,
            messageId,
            responseToMessageId,
            body),
        is(not(msg1)));

    // different message ID
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            UUID.randomUUID().toString(),
            responseToMessageId,
            body),
        is(not(msg1)));

    // different responseToId
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            UUID.randomUUID().toString(),
            body),
        is(not(msg1)));

    // different body
    assertThat(
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToMessageId,
            "whatevah".getBytes(UTF_8)),
        is(not(msg1)));
  }

  @Test
  public void chronicle_roundTrip_basic() throws Exception {
    Path dir = Files.createTempDirectory("cq-basic");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptAppender app = q.createAppender();
      ExcerptTailer tailer = q.createTailer();

      byte[] body = "hello".getBytes(UTF_8);
      OutboundMsg out = new OutboundMsg(MessageType.EXEC_CLASS_METHOD, body);

      long idx = out.appendTo(app);

      OutboundMsg in = OutboundMsg.readNext(tailer);
      assertThat(in, is(notNullValue()));
      assertThat(in.getMessageType(), is(out.getMessageType()));
      assertThat(in.getBody(), is(out.getBody()));

      // No extra docs
      assertThat(OutboundMsg.readNext(tailer), is(nullValue()));

      // Index should be >= 0 (sanity)
      assertThat(idx >= 0, is(true));
    }
  }

  @Test
  public void chronicle_roundTrip_zeroLengthBody() throws Exception {
    Path dir = Files.createTempDirectory("cq-zero");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptAppender app = q.createAppender();
      ExcerptTailer tailer = q.createTailer();

      byte[] body = new byte[0];
      OutboundMsg out = new OutboundMsg(MessageType.EXEC_CONSTRUCTOR, body);

      out.appendTo(app);

      OutboundMsg in = OutboundMsg.readNext(tailer);
      assertThat(in, is(notNullValue()));
      assertThat(in.getMessageType(), is(MessageType.EXEC_CONSTRUCTOR));
      assertThat(in.getBody(), is(body));
    }
  }

  @Test
  public void chronicle_roundTrip_multipleInOrder() throws Exception {
    Path dir = Files.createTempDirectory("cq-multi");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptAppender app = q.createAppender();
      ExcerptTailer tailer = q.createTailer();

      OutboundMsg m1 = new OutboundMsg(MessageType.EXEC_GET_FIELD, "A".getBytes(UTF_8));
      OutboundMsg m2 = new OutboundMsg(MessageType.EXEC_CONSTRUCTOR, "BB".getBytes(UTF_8));
      OutboundMsg m3 = new OutboundMsg(MessageType.EXEC_INSTANCE_METHOD, "CCC".getBytes(UTF_8));

      long i1 = m1.appendTo(app);
      long i2 = m2.appendTo(app);
      long i3 = m3.appendTo(app);

      OutboundMsg r1 = OutboundMsg.readNext(tailer);
      OutboundMsg r2 = OutboundMsg.readNext(tailer);
      OutboundMsg r3 = OutboundMsg.readNext(tailer);
      OutboundMsg r4 = OutboundMsg.readNext(tailer);

      assertThat(r1.getBody(), is("A".getBytes(UTF_8)));
      assertThat(r2.getBody(), is("BB".getBytes(UTF_8)));
      assertThat(r3.getBody(), is("CCC".getBytes(UTF_8)));
      assertThat(r4, is(nullValue()));

      // monotonic indices (from appendTo returns)
      assertThat(i1 < i2 && i2 < i3, is(true));
    }
  }

  @Test
  public void chronicle_readNext_returnsNullWhenNoDoc() throws Exception {
    Path dir = Files.createTempDirectory("cq-empty");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptTailer tailer = q.createTailer();
      assertThat(OutboundMsg.readNext(tailer), is(nullValue()));
    }
  }

  /**
   * Write a deliberately truncated document: length says N, but fewer than N bytes are present.
   * readNext should throw (Bytes underflow).
   */
  @Test
  public void chronicle_readNext_truncatedBody_throws() throws Exception {
    Path dir = Files.createTempDirectory("cq-trunc");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptAppender app = q.createAppender();

      // valid doc first
      new OutboundMsg(
              MessageType.EXEC_GET_STATIC,
              ExecPhase.UNDEFINED,
              null,
              "ok",
              null,
              "OK".getBytes(UTF_8))
          .appendTo(app);

      // craft a corrupted doc
      try (DocumentContext dc = app.writingDocument()) {
        Bytes<?> out = dc.wire().bytes();
        out.writeByte(MessageType.EXEC_PUT_FIELD.getId()); // type
        out.writeInt(4); // claims length=4
        out.write(new byte[] {1, 2}); // but only 2 bytes provided
      }

      ExcerptTailer tailer = q.createTailer();
      // consume the valid one
      OutboundMsg first = OutboundMsg.readNext(tailer);
      assertThat(first, is(notNullValue()));

      // now the broken one must throw
      try {
        OutboundMsg.readNext(tailer);
        fail("Expected IORuntimeException for truncated body");
      } catch (IORuntimeException expected) {
        // expected
      }
    }
  }

  @Test
  public void chronicle_readNext_negativeBodyLen_throws() throws Exception {
    Path dir = Files.createTempDirectory("cq-neg");
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(dir.toFile()).wireType(WireType.BINARY_LIGHT).build()) {
      ExcerptAppender app = q.createAppender();

      try (DocumentContext dc = app.writingDocument()) {
        Bytes<?> out = dc.wire().bytes();
        out.writeByte(MessageType.EXEC_CONSTRUCTOR.getId());
        out.writeInt(-1); // invalid
      }

      ExcerptTailer tailer = q.createTailer();

      try {
        OutboundMsg.readNext(tailer);
        fail("Expected IORuntimeException for negative body length");
      } catch (IORuntimeException expected) {
        // pass
      }
    }
  }

  // ============================================================================
  // Test specifications for OutboundMsg edge cases (Issue #427)
  // These tests are awaiting implementation in #428
  // ============================================================================

  /**
   * Verifies that send() throws IllegalArgumentException when called with a null socket.
   *
   * <p>Per Javadoc contract, the send method should validate its input and throw
   * IllegalArgumentException for null socket parameter.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void send_nullSocket_throwsIllegalArgumentException() {
    // Given: A valid OutboundMsg with all required fields
    // When: send(null) is called
    // Then: IllegalArgumentException is thrown (per Javadoc contract)

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that send() with DONTWAIT flag returns appropriate values.
   *
   * <p>When using the two-argument send(socket, flags) method with ZMQ.DONTWAIT flag, the method
   * should return false if the operation would block, and true if the message was sent
   * successfully.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void send_withDontwaitFlag_returnsAppropriately() {
    // Given: A valid OutboundMsg; ZMQ socket configured with DONTWAIT flag
    // When: send(socket, ZMQ.DONTWAIT) is called
    // Then: Returns false if would block; true if sent successfully

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that receive() handles null socket gracefully.
   *
   * <p>Per Javadoc contract, the receive method should throw IllegalArgumentException when called
   * with a null socket parameter.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void receive_nullSocket_handlesGracefully() {
    // Given: null socket
    // When: receive(null) is called
    // Then: IllegalArgumentException is thrown (per Javadoc contract)

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that writeTo() writes the correct binary format.
   *
   * <p>The writeTo method should write the message in Chronicle Queue binary format: type (1 byte)
   * + length (4 bytes) + body (variable).
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void writeTo_validBytes_writesCorrectFormat() {
    // Given: A valid OutboundMsg with known body content
    // When: writeTo(bytes) is called
    // Then: Writes type (1 byte) + length (4 bytes) + body in correct format

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that multiple headers are preserved during send/receive round-trip.
   *
   * <p>When an OutboundMsg has multiple InternalHeaders, all headers should be preserved when the
   * message is sent via ZMQ and then received.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void sendReceive_multipleHeaders_roundTripPreserved() {
    // Given: OutboundMsg with 3 InternalHeaders
    // When: Message is sent via ZMQ then received
    // Then: All headers are preserved in received message

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that toString() returns a readable string representation.
   *
   * <p>When an OutboundMsg has all fields populated, toString() should return an informative string
   * that includes the message type, execution phase, headers, message ID, response-to ID, body, and
   * size.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void toString_validMessage_returnsReadableString() {
    // Given: OutboundMsg with all fields populated
    // When: toString() is called
    // Then: Returns informative string representation containing all field values

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }

  /**
   * Verifies that equal OutboundMsg objects have the same hashCode.
   *
   * <p>Per the hashCode contract, two objects that are equal according to equals() must return the
   * same hashCode value.
   */
  @Test
  @Ignore("Awaiting implementation in #428")
  public void hashCode_equalObjects_sameHashCode() {
    // Given: Two equal OutboundMsg objects (same messageType, execPhase, headers,
    //        messageId, responseToId, and body)
    // When: hashCode() is called on both
    // Then: Same hash code value is returned

    // TODO(#428): Implement test
    fail("Not yet implemented");
  }
}
