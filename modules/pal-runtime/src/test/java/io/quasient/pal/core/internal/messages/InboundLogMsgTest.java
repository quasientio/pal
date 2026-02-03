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

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.MessageFormatType;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single InboundLogMsg instance
    Headers headers = new RecordHeaders();
    byte[] body = new byte[] {1, 2, 3};
    InboundLogMsg msg = new InboundLogMsg(100L, MessageFormatType.BINARY, headers, body);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two InboundLogMsg instances with identical field values
    Headers headers1 = new RecordHeaders();
    Headers headers2 = new RecordHeaders();
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {1, 2, 3};

    InboundLogMsg msg1 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers1, body1);
    InboundLogMsg msg2 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers2, body2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two InboundLogMsg instances with different field values
    Headers headers = new RecordHeaders();
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {4, 5, 6};

    InboundLogMsg msg1 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers, body1);
    InboundLogMsg msg2 = new InboundLogMsg(200L, MessageFormatType.BINARY, headers, body2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Also test with different messageFormat values
    InboundLogMsg msgDifferentFormat =
        new InboundLogMsg(100L, MessageFormatType.JSON, headers, body1);
    assertThat(msg1.equals(msgDifferentFormat), is(false));

    // Different body arrays (same length, different content)
    byte[] body3 = new byte[] {1, 2, 4};
    InboundLogMsg msgDifferentBody =
        new InboundLogMsg(100L, MessageFormatType.BINARY, headers, body3);
    assertThat(msg1.equals(msgDifferentBody), is(false));

    // Different offset only
    InboundLogMsg msgDifferentOffset =
        new InboundLogMsg(101L, MessageFormatType.BINARY, headers, body1);
    assertThat(msg1.equals(msgDifferentOffset), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: An InboundLogMsg instance
    Headers headers = new RecordHeaders();
    byte[] body = new byte[] {1, 2, 3};
    InboundLogMsg msg = new InboundLogMsg(100L, MessageFormatType.BINARY, headers, body);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two InboundLogMsg instances with identical field values
    Headers headers1 = new RecordHeaders();
    Headers headers2 = new RecordHeaders();
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {1, 2, 3};

    InboundLogMsg msg1 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers1, body1);
    InboundLogMsg msg2 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers2, body2);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal (required by hashCode contract when equals() returns true)
    assertThat(msg1.hashCode(), is(msg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundLogMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two InboundLogMsg instances with different field values
    Headers headers = new RecordHeaders();
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {4, 5, 6};

    InboundLogMsg msg1 = new InboundLogMsg(100L, MessageFormatType.BINARY, headers, body1);
    InboundLogMsg msg2 = new InboundLogMsg(200L, MessageFormatType.BINARY, headers, body2);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different (not strictly required, but expected)
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:InboundLogMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: An InboundLogMsg with known field values
    Headers headers = new RecordHeaders();
    byte[] body = new byte[] {10, 20, 30};
    InboundLogMsg msg = new InboundLogMsg(12345L, MessageFormatType.BINARY, headers, body);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, org.hamcrest.Matchers.containsString("InboundLogMsg"));
    assertThat(result, org.hamcrest.Matchers.containsString("12345"));
    assertThat(result, org.hamcrest.Matchers.containsString("BINARY"));
  }
}
