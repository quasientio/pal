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
import java.util.Locale;
import java.util.UUID;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.io.IORuntimeException;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.WireType;
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
  // Edge case tests for OutboundMsg (Issue #428)
  // Implements test specifications from Issue #427
  // ============================================================================

  /**
   * Verifies that send() throws IllegalArgumentException when called with a null socket.
   *
   * <p>Per Javadoc contract, the send method should validate its input and throw
   * IllegalArgumentException for null socket parameter.
   */
  @Test
  public void send_nullSocket_throwsIllegalArgumentException() {
    // Given: A valid OutboundMsg with all required fields
    String messageId = UUID.randomUUID().toString();
    byte[] body = "test body".getBytes(UTF_8);
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, messageId, null, body);

    // When: send(null) is called
    // Then: IllegalArgumentException is thrown (per Javadoc contract)
    try {
      msg.send(null);
      fail("Expected IllegalArgumentException for null socket");
    } catch (IllegalArgumentException e) {
      // Expected - verify message contains useful info
      assertThat(e.getMessage().toLowerCase(Locale.ROOT).contains("null"), is(true));
    }
  }

  /**
   * Verifies that send() with DONTWAIT flag returns appropriate values.
   *
   * <p>When using the two-argument send(socket, flags) method with ZMQ.DONTWAIT flag, the method
   * should return false if the operation would block, and true if the message was sent
   * successfully.
   */
  @Test
  public void send_withDontwaitFlag_returnsAppropriately() {
    // Given: A valid OutboundMsg; ZMQ socket pair for send/receive
    String messageId = UUID.randomUUID().toString();
    byte[] body = "test body".getBytes(UTF_8);
    OutboundMsg msg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, messageId, null, body);

    String zmqEndpoint = "inproc://dontwait-test";
    ZContext zmqContext = createContext();
    ZMQ.Socket receiver = zmqContext.createSocket(SocketType.REP);
    receiver.bind(zmqEndpoint);
    ZMQ.Socket sender = zmqContext.createSocket(SocketType.REQ);
    sender.connect(zmqEndpoint);

    // When: send(socket, ZMQ.DONTWAIT) is called on a connected socket
    // Then: Returns true because the socket is ready
    boolean sent = msg.send(sender, ZMQ.DONTWAIT);
    assertThat("Message should be sent successfully with DONTWAIT on ready socket", sent, is(true));

    // Verify the message was actually received
    OutboundMsg received = OutboundMsg.receive(receiver, true);
    assertThat(received, is(notNullValue()));
    assertThat(received.getMessageId(), is(messageId));

    sender.close();
    receiver.close();
    zmqContext.destroy();
  }

  /**
   * Verifies that receive() handles null socket gracefully.
   *
   * <p>Per Javadoc contract, the receive method should throw IllegalArgumentException when called
   * with a null socket parameter.
   */
  @Test
  public void receive_nullSocket_handlesGracefully() {
    // Given: null socket
    // When: receive(null, blocking) is called
    // Then: IllegalArgumentException is thrown (per Javadoc contract)
    try {
      OutboundMsg.receive(null, true);
      fail("Expected IllegalArgumentException for null socket with blocking=true");
    } catch (IllegalArgumentException e) {
      // Expected - verify message contains useful info
      assertThat(e.getMessage().toLowerCase(Locale.ROOT).contains("null"), is(true));
    }

    // Also test the non-blocking variant
    try {
      OutboundMsg.receive(null, false);
      fail("Expected IllegalArgumentException for null socket with blocking=false");
    } catch (IllegalArgumentException e) {
      // Expected
      assertThat(e.getMessage().toLowerCase(Locale.ROOT).contains("null"), is(true));
    }

    // Also test the convenience method (no blocking param)
    try {
      OutboundMsg.receive(null);
      fail("Expected IllegalArgumentException for null socket with convenience method");
    } catch (IllegalArgumentException e) {
      // Expected
      assertThat(e.getMessage().toLowerCase(Locale.ROOT).contains("null"), is(true));
    }
  }

  /**
   * Verifies that writeTo() writes the correct binary format.
   *
   * <p>The writeTo method should write the message in Chronicle Queue binary format: type (1 byte)
   * + length (4 bytes) + body (variable).
   */
  @Test
  public void writeTo_validBytes_writesCorrectFormat() {
    // Given: A valid OutboundMsg with known body content
    byte[] bodyContent = "hello world".getBytes(UTF_8);
    OutboundMsg msg = new OutboundMsg(MessageType.EXEC_CONSTRUCTOR, bodyContent);

    // When: writeTo(bytes) is called
    Bytes<?> out = Bytes.allocateElasticOnHeap(64);
    msg.writeTo(out);

    // Then: Writes type (1 byte) + length (4 bytes) + body in correct format
    // Reset read position to beginning
    out.readPosition(0);

    // Read type byte
    byte type = out.readByte();
    assertThat(
        "Type byte should match MessageType", type, is(MessageType.EXEC_CONSTRUCTOR.getId()));

    // Read length (4 bytes, int)
    int length = out.readInt();
    assertThat("Length should match body length", length, is(bodyContent.length));

    // Read body
    byte[] readBody = new byte[length];
    out.read(readBody, 0, length);
    assertThat("Body content should match original", readBody, is(bodyContent));

    // Verify total written size: 1 (type) + 4 (length) + body.length
    long totalWritten = out.writePosition();
    assertThat(
        "Total bytes written should be 1 + 4 + body.length",
        totalWritten,
        is((long) (1 + 4 + bodyContent.length)));

    out.releaseLast();
  }

  /**
   * Verifies that multiple headers are preserved during send/receive round-trip.
   *
   * <p>When an OutboundMsg has multiple InternalHeaders, all headers should be preserved when the
   * message is sent via ZMQ and then received.
   */
  @Test
  public void sendReceive_multipleHeaders_roundTripPreserved() {
    // Given: OutboundMsg with 3 InternalHeaders
    InternalHeader header1 = new InternalHeader().withHeaderType((byte) 1).withValue("value-one");
    InternalHeader header2 = new InternalHeader().withHeaderType((byte) 2).withValue("value-two");
    InternalHeader header3 = new InternalHeader().withHeaderType((byte) 3).withValue("value-three");
    List<InternalHeader> headers = Arrays.asList(header1, header2, header3);

    String messageId = UUID.randomUUID().toString();
    String responseToId = UUID.randomUUID().toString();
    byte[] body = "multi-header body".getBytes(UTF_8);

    OutboundMsg msgOut =
        new OutboundMsg(
            MessageType.INTERCEPT_MESSAGE, ExecPhase.AFTER, headers, messageId, responseToId, body);

    // When: Message is sent via ZMQ then received
    String zmqEndpoint = "inproc://multi-headers-test";
    ZContext zmqContext = createContext();
    ZMQ.Socket receiver = zmqContext.createSocket(SocketType.REP);
    receiver.bind(zmqEndpoint);
    ZMQ.Socket sender = zmqContext.createSocket(SocketType.REQ);
    sender.connect(zmqEndpoint);

    boolean sent = msgOut.send(sender);
    assertThat("Message should be sent successfully", sent, is(true));

    OutboundMsg msgIn = OutboundMsg.receive(receiver, true);

    // Then: All headers are preserved in received message
    assertThat(msgIn, is(notNullValue()));
    assertThat(msgIn.getHeaders(), is(notNullValue()));
    assertThat("Should have 3 headers", msgIn.getHeaders().size(), is(3));

    // Verify each header is preserved
    assertThat(msgIn.getHeaders().get(0).getHeaderType(), is((byte) 1));
    assertThat(msgIn.getHeaders().get(0).getValue(), is("value-one"));
    assertThat(msgIn.getHeaders().get(1).getHeaderType(), is((byte) 2));
    assertThat(msgIn.getHeaders().get(1).getValue(), is("value-two"));
    assertThat(msgIn.getHeaders().get(2).getHeaderType(), is((byte) 3));
    assertThat(msgIn.getHeaders().get(2).getValue(), is("value-three"));

    // Verify the entire message equals the original
    assertThat(msgIn, is(msgOut));

    sender.close();
    receiver.close();
    zmqContext.destroy();
  }

  /**
   * Verifies that toString() returns a readable string representation.
   *
   * <p>When an OutboundMsg has all fields populated, toString() should return an informative string
   * that includes the message type, execution phase, headers, message ID, response-to ID, body, and
   * size.
   */
  @Test
  public void toString_validMessage_returnsReadableString() {
    // Given: OutboundMsg with all fields populated
    InternalHeader header = new InternalHeader().withHeaderType((byte) 5).withValue("test-header");
    List<InternalHeader> headers = Collections.singletonList(header);

    String messageId = "test-msg-id-12345";
    String responseToId = "response-to-id-67890";
    byte[] body = "test body content".getBytes(UTF_8);

    OutboundMsg msg =
        new OutboundMsg(
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE,
            headers,
            messageId,
            responseToId,
            body);

    // When: toString() is called
    String result = msg.toString();

    // Then: Returns informative string representation containing all field values
    assertThat("toString should contain class name", result.contains("OutboundMsg"), is(true));
    assertThat(
        "toString should contain message type", result.contains("EXEC_INSTANCE_METHOD"), is(true));
    assertThat("toString should contain exec phase", result.contains("BEFORE"), is(true));
    assertThat("toString should contain messageId", result.contains(messageId), is(true));
    assertThat("toString should contain responseToId", result.contains(responseToId), is(true));
    // Body is shown as byte array, so check for "body=" or similar
    assertThat("toString should mention body", result.contains("body="), is(true));
    // Size should be mentioned
    assertThat("toString should mention size", result.contains("size="), is(true));
  }

  /**
   * Verifies that equal OutboundMsg objects have the same hashCode.
   *
   * <p>Per the hashCode contract, two objects that are equal according to equals() must return the
   * same hashCode value.
   */
  @Test
  public void hashCode_equalObjects_sameHashCode() {
    // Given: Two equal OutboundMsg objects (same messageType, execPhase, headers,
    //        messageId, responseToId, and body)
    InternalHeader header = new InternalHeader().withHeaderType((byte) 7).withValue("hash-test");
    List<InternalHeader> headers1 = Collections.singletonList(header);
    List<InternalHeader> headers2 =
        Collections.singletonList(
            new InternalHeader().withHeaderType((byte) 7).withValue("hash-test"));

    String messageId = "hash-msg-id";
    String responseToId = "hash-response-id";
    byte[] body1 = "hash body".getBytes(UTF_8);
    byte[] body2 = "hash body".getBytes(UTF_8);

    OutboundMsg msg1 =
        new OutboundMsg(
            MessageType.EXEC_GET_FIELD, ExecPhase.AFTER, headers1, messageId, responseToId, body1);

    OutboundMsg msg2 =
        new OutboundMsg(
            MessageType.EXEC_GET_FIELD, ExecPhase.AFTER, headers2, messageId, responseToId, body2);

    // Verify objects are equal first
    assertThat("Objects should be equal", msg1, is(msg2));

    // When: hashCode() is called on both
    int hash1 = msg1.hashCode();
    int hash2 = msg2.hashCode();

    // Then: Same hash code value is returned
    assertThat("Equal objects must have same hashCode", hash1, is(hash2));

    // Additional verification: hashCode is consistent (multiple calls return same value)
    assertThat("hashCode should be consistent", msg1.hashCode(), is(hash1));
    assertThat("hashCode should be consistent", msg2.hashCode(), is(hash2));

    // Also verify that different objects have different hash codes (not guaranteed, but likely)
    OutboundMsg msg3 =
        new OutboundMsg(
            MessageType.EXEC_PUT_FIELD, // Different type
            ExecPhase.AFTER,
            headers1,
            messageId,
            responseToId,
            body1);

    assertThat("Different objects should not be equal", msg1, is(not(msg3)));
    // Note: Different objects MAY have same hashCode (hash collisions), but usually won't
  }
}
