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
import io.quasient.pal.messages.Marshallable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class InterceptEventMsgTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendRegister() {

    final byte[] body = "actual body is a intercept message".getBytes(StandardCharsets.UTF_8);
    final var type = InterceptEventMsg.Type.REGISTER;

    InterceptEventMsg msg = new InterceptEventMsg(body);

    // verify getters
    assertThat(msg.getBody(), is(body));
    assertThat(msg.getType(), is(type));

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msg.send(out);

    // receive and compare
    InterceptEventMsg msgIn = InterceptEventMsg.receive(in, true);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendUnregister() {

    final String interceptMsgId = UUID.randomUUID().toString();
    final var type = InterceptEventMsg.Type.UNREGISTER;

    InterceptEventMsg msg = new InterceptEventMsg(interceptMsgId);

    // verify getters
    assertThat(msg.getType(), is(type));
    assertThat(msg.getInterceptMessageId(), is(interceptMsgId));

    // send
    String socketAddress = "inproc://here";
    ZContext mqzContext = createContext();
    ZMQ.Socket in = mqzContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = mqzContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);
    msg.send(out);
    logger.debug("sent msg= {}", msg);

    // receive and compare
    InterceptEventMsg msgIn = InterceptEventMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msg));

    out.close();
    in.close();
    mqzContext.destroy();
  }

  @Test
  public void testNullPointerException() {
    try {
      new InterceptEventMsg((Marshallable) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEventMsg((byte[]) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
    try {
      new InterceptEventMsg((String) null);
      fail("Should have raised NPE");
    } catch (NullPointerException e) {
      // ok then
    }
  }

  @Test
  public void testEquals() {
    // REGISTER type
    byte[] body = "actual body is not a string".getBytes(StandardCharsets.UTF_8);
    InterceptEventMsg msg = new InterceptEventMsg(body);

    // assert equality
    assertThat(new InterceptEventMsg(body), is(msg));

    // different body
    assertThat(
        new InterceptEventMsg("another body".getBytes(StandardCharsets.UTF_8)), is(not(msg)));

    // UNREGISTER type
    String interceptMsgId = UUID.randomUUID().toString();
    msg = new InterceptEventMsg(interceptMsgId);

    // assert equality
    assertThat(new InterceptEventMsg(interceptMsgId), is(msg));

    // different messageId
    assertThat(new InterceptEventMsg(UUID.randomUUID().toString()), is(not(msg)));
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications for #530
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single InterceptEventMsg instance
    // - Create an InterceptEventMsg with:
    //   - For REGISTER type: a byte[] body
    //   - Or for UNREGISTER type: an interceptMessageId string

    // When: equals() is called with the same object reference

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two InterceptEventMsg instances with identical field values
    // - For REGISTER type: both messages have identical byte[] body
    // - Or for UNREGISTER type: both have identical interceptMessageId string

    // When: equals() is called comparing message1 to message2

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two InterceptEventMsg instances with different field values
    // - message1: REGISTER type with body {1, 2, 3}
    // - message2: REGISTER type with body {4, 5, 6}

    // When: equals() is called comparing message1 to message2

    // Then: Returns false

    // Also test with:
    // - Different types (REGISTER vs UNREGISTER)
    // - Different interceptMessageId for UNREGISTER type

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_null_returnsFalse() {
    // Given: An InterceptEventMsg instance
    // - Create message with any valid field values (REGISTER or UNREGISTER type)

    // When: equals() is called with null

    // Then: Returns false (should not throw NullPointerException)

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two InterceptEventMsg instances with identical field values
    // - For REGISTER type: both messages have identical byte[] body
    // - Or for UNREGISTER type: both have identical interceptMessageId string

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
   * [TEST:InterceptEventMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two InterceptEventMsg instances with different field values
    // - message1: REGISTER type with body {1, 2, 3}
    // - message2: REGISTER type with body {4, 5, 6}

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
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testToString_containsRelevantInfo() {
    // Given: An InterceptEventMsg with known field values
    // - For REGISTER type: type=REGISTER, body=byte array
    // - Or for UNREGISTER type: type=UNREGISTER, interceptMessageId=string

    // When: toString() is called

    // Then: The returned string contains:
    // - The class name or identifier "InterceptEventMsg"
    // - The type (REGISTER or UNREGISTER)
    // - For REGISTER: representation of the body
    // - For UNREGISTER: the interceptMessageId

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }
}
