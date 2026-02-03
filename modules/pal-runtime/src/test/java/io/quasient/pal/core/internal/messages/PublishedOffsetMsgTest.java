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
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class PublishedOffsetMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {
    long offset = 472;
    String messageId = UUID.randomUUID().toString();

    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageId);

    // verify getters
    assertThat(msgOut.getOffset(), is(offset));
    assertThat(msgOut.getMessageId(), is(messageId));

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void testEquals() {
    long offset = 472;
    String messageId = UUID.randomUUID().toString();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageId);

    // assert content equality
    assertThat(new PublishedOffsetMsg(offset, messageId), is(msg1));

    // different offset
    assertThat(new PublishedOffsetMsg(offset + 1, messageId), is(not(msg1)));

    // different messageId
    assertThat(new PublishedOffsetMsg(offset, UUID.randomUUID().toString()), is(not(msg1)));
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications for #531
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single PublishedOffsetMsg instance
    // - Create a PublishedOffsetMsg with:
    //   - offset: 12345L
    //   - messageId: a UUID string

    // When: equals() is called with the same object reference

    // Then: Returns true

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two PublishedOffsetMsg instances with identical field values
    // - Both messages have:
    //   - offset: same long value
    //   - messageId: same String value

    // When: equals() is called comparing message1 to message2

    // Then: Returns true

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two PublishedOffsetMsg instances with different field values
    // - message1 with offset: 100L, messageId: "id1"
    // - message2 with offset: 200L, messageId: "id2"

    // When: equals() is called comparing message1 to message2

    // Then: Returns false

    // Also test with:
    // - Same offset, different messageId
    // - Different offset, same messageId

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_null_returnsFalse() {
    // Given: A PublishedOffsetMsg instance
    // - Create message with any valid field values

    // When: equals() is called with null

    // Then: Returns false (should not throw NullPointerException)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two PublishedOffsetMsg instances with identical field values
    // - Both messages have:
    //   - offset: same long value
    //   - messageId: same String value

    // When: hashCode() is called on both objects

    // Then: Both hash codes are equal
    // Note: This is required by the hashCode contract when equals() returns true

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:PublishedOffsetMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two PublishedOffsetMsg instances with different field values
    // - message1 with offset: 100L, messageId: "id1"
    // - message2 with offset: 200L, messageId: "id2"

    // When: hashCode() is called on both objects

    // Then: Hash codes are likely different
    // Note: This is not strictly required by the hashCode contract,
    // but a good hash function should minimize collisions

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testToString_containsRelevantInfo() {
    // Given: A PublishedOffsetMsg with known field values
    // - offset: 12345L
    // - messageId: a specific UUID string

    // When: toString() is called

    // Then: The returned string contains:
    // - The class name or identifier "PublishedOffsetMsg"
    // - The offset value
    // - The messageId value

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:PublishedOffsetMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with a PublishedOffsetMsg sent through it
    // - Create a REQ/REP socket pair
    // - Send a valid PublishedOffsetMsg

    // When: receive(socket) is called (single-arg version, non-blocking)

    // Then:
    // - Returns a valid PublishedOffsetMsg when message is available
    // - The message fields match the sent message
    // - The single-arg method delegates to receive(socket, false)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that send() writes message data correctly to the ZMQ socket.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testSend_writesMessageToSocket]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testSend_writesMessageToSocket() {
    // Given: A valid PublishedOffsetMsg instance and a ZMQ socket pair
    // - Create a PublishedOffsetMsg with:
    //   - offset: 98765L
    //   - messageId: a specific UUID string
    // - Create a REQ/REP socket pair

    // When: send(socket) is called

    // Then:
    // - Returns true indicating successful send
    // - The message size is correctly calculated (8 bytes for offset + messageId bytes)
    // - The message can be received and reconstructed with matching fields

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }
}
