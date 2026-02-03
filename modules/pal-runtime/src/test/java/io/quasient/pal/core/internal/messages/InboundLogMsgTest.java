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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.MessageFormatType;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InboundLogMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 199;
    byte[] body = "whatever".getBytes(StandardCharsets.UTF_8);

    Headers emptyHeaders = new RecordHeaders();
    InboundLogMsg msgOut = new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.DEALER);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    InboundLogMsg msgIn = InboundLogMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 199;
    Headers emptyHeaders = new RecordHeaders();
    byte[] body = "whatever".getBytes(StandardCharsets.UTF_8);

    InboundLogMsg msg1 = new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body);

    // equal
    assertThat(new InboundLogMsg(offset, MessageFormatType.BINARY, emptyHeaders, body), is(msg1));

    // different offset
    assertThat(
        new InboundLogMsg(offset + 1, MessageFormatType.BINARY, emptyHeaders, body), is(not(msg1)));

    // different format
    assertThat(
        new InboundLogMsg(offset, MessageFormatType.JSON, emptyHeaders, body), is(not(msg1)));

    // different body
    assertThat(
        new InboundLogMsg(
            offset,
            MessageFormatType.BINARY,
            emptyHeaders,
            "whatevah".getBytes(StandardCharsets.UTF_8)),
        is(not(msg1)));
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications for #530
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single InboundLogMsg instance
    // - Create an InboundLogMsg with:
    //   - offset: 100L
    //   - messageFormat: MessageFormatType.COLFER
    //   - headers: empty RecordHeaders
    //   - body: byte array {1, 2, 3}

    // When: equals() is called with the same object reference

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two InboundLogMsg instances with identical field values
    // - Both messages have:
    //   - offset: 100L
    //   - messageFormat: MessageFormatType.COLFER
    //   - headers: identical RecordHeaders (empty or with same key-value pairs)
    //   - body: identical byte arrays {1, 2, 3}

    // When: equals() is called comparing message1 to message2

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two InboundLogMsg instances with different field values
    // - message1 with offset: 100L, body: {1, 2, 3}
    // - message2 with offset: 200L, body: {4, 5, 6}

    // When: equals() is called comparing message1 to message2

    // Then: Returns false

    // Also test with:
    // - Different messageFormat values
    // - Different headers
    // - Different body arrays (same length, different content)

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_null_returnsFalse() {
    // Given: An InboundLogMsg instance
    // - Create message with any valid field values

    // When: equals() is called with null

    // Then: Returns false (should not throw NullPointerException)

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two InboundLogMsg instances with identical field values
    // - Both messages have:
    //   - offset: 100L
    //   - messageFormat: MessageFormatType.COLFER
    //   - headers: identical RecordHeaders
    //   - body: identical byte arrays {1, 2, 3}

    // When: hashCode() is called on both objects

    // Then: Both hash codes are equal
    // Note: This is required by the hashCode contract when equals() returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundLogMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two InboundLogMsg instances with different field values
    // - message1 with offset: 100L, body: {1, 2, 3}
    // - message2 with offset: 200L, body: {4, 5, 6}

    // When: hashCode() is called on both objects

    // Then: Hash codes are likely different
    // Note: This is not strictly required by the hashCode contract,
    // but a good hash function should minimize collisions

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testToString_containsRelevantInfo() {
    // Given: An InboundLogMsg with known field values
    // - offset: 12345L
    // - messageFormat: MessageFormatType.COLFER
    // - headers: RecordHeaders (may be empty)
    // - body: byte array {10, 20, 30}

    // When: toString() is called

    // Then: The returned string contains:
    // - The class name or identifier "InboundLogMsg"
    // - The offset value (12345)
    // - The message format information

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }
}
