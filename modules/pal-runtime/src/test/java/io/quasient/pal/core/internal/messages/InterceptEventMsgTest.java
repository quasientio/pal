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
  public void testEquals_sameObject_returnsTrue() {
    // Given: InterceptEventMsg instances for both REGISTER and UNREGISTER types
    byte[] body = new byte[] {1, 2, 3};
    InterceptEventMsg registerMsg = new InterceptEventMsg(body);

    String interceptMsgId = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg = new InterceptEventMsg(interceptMsgId);

    // When: equals() is called with the same object reference
    // Then: Returns true for both types
    assertThat(registerMsg.equals(registerMsg), is(true));
    assertThat(unregisterMsg.equals(unregisterMsg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two InterceptEventMsg instances with identical field values
    // Test REGISTER type
    byte[] body = new byte[] {1, 2, 3};
    InterceptEventMsg registerMsg1 = new InterceptEventMsg(body);
    InterceptEventMsg registerMsg2 = new InterceptEventMsg(body);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(registerMsg1.equals(registerMsg2), is(true));
    assertThat(registerMsg2.equals(registerMsg1), is(true));

    // Test UNREGISTER type
    String interceptMsgId = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg1 = new InterceptEventMsg(interceptMsgId);
    InterceptEventMsg unregisterMsg2 = new InterceptEventMsg(interceptMsgId);

    assertThat(unregisterMsg1.equals(unregisterMsg2), is(true));
    assertThat(unregisterMsg2.equals(unregisterMsg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two InterceptEventMsg instances with different field values
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {4, 5, 6};

    InterceptEventMsg registerMsg1 = new InterceptEventMsg(body1);
    InterceptEventMsg registerMsg2 = new InterceptEventMsg(body2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(registerMsg1.equals(registerMsg2), is(false));

    // Also test with different types (REGISTER vs UNREGISTER)
    String interceptMsgId = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg = new InterceptEventMsg(interceptMsgId);
    assertThat(registerMsg1.equals(unregisterMsg), is(false));

    // Different interceptMessageId for UNREGISTER type
    String interceptMsgId2 = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg2 = new InterceptEventMsg(interceptMsgId2);
    assertThat(unregisterMsg.equals(unregisterMsg2), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: InterceptEventMsg instances for both types
    byte[] body = new byte[] {1, 2, 3};
    InterceptEventMsg registerMsg = new InterceptEventMsg(body);

    String interceptMsgId = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg = new InterceptEventMsg(interceptMsgId);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(registerMsg.equals(null), is(false));
    assertThat(unregisterMsg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two InterceptEventMsg instances with identical field values
    // Test REGISTER type
    byte[] body = new byte[] {1, 2, 3};
    InterceptEventMsg registerMsg1 = new InterceptEventMsg(body);
    InterceptEventMsg registerMsg2 = new InterceptEventMsg(body);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal (required by hashCode contract when equals() returns true)
    assertThat(registerMsg1.hashCode(), is(registerMsg2.hashCode()));

    // Test UNREGISTER type
    String interceptMsgId = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg1 = new InterceptEventMsg(interceptMsgId);
    InterceptEventMsg unregisterMsg2 = new InterceptEventMsg(interceptMsgId);

    assertThat(unregisterMsg1.hashCode(), is(unregisterMsg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:InterceptEventMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two InterceptEventMsg instances with different field values
    byte[] body1 = new byte[] {1, 2, 3};
    byte[] body2 = new byte[] {4, 5, 6};

    InterceptEventMsg registerMsg1 = new InterceptEventMsg(body1);
    InterceptEventMsg registerMsg2 = new InterceptEventMsg(body2);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different (not strictly required, but expected)
    assertThat(registerMsg1.hashCode(), is(not(registerMsg2.hashCode())));

    // Also test UNREGISTER type
    String interceptMsgId1 = UUID.randomUUID().toString();
    String interceptMsgId2 = UUID.randomUUID().toString();
    InterceptEventMsg unregisterMsg1 = new InterceptEventMsg(interceptMsgId1);
    InterceptEventMsg unregisterMsg2 = new InterceptEventMsg(interceptMsgId2);

    assertThat(unregisterMsg1.hashCode(), is(not(unregisterMsg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:InterceptEventMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: InterceptEventMsg instances for both types

    // Test REGISTER type
    byte[] body = new byte[] {10, 20, 30};
    InterceptEventMsg registerMsg = new InterceptEventMsg(body);

    // When: toString() is called
    String registerResult = registerMsg.toString();

    // Then: The returned string contains relevant information
    assertThat(registerResult, org.hamcrest.Matchers.containsString("InterceptEventMsg"));
    assertThat(registerResult, org.hamcrest.Matchers.containsString("REGISTER"));
    assertThat(registerResult, org.hamcrest.Matchers.containsString("[10, 20, 30]"));

    // Test UNREGISTER type
    String interceptMsgId = "test-intercept-id-12345";
    InterceptEventMsg unregisterMsg = new InterceptEventMsg(interceptMsgId);

    String unregisterResult = unregisterMsg.toString();

    assertThat(unregisterResult, org.hamcrest.Matchers.containsString("InterceptEventMsg"));
    assertThat(unregisterResult, org.hamcrest.Matchers.containsString("UNREGISTER"));
    assertThat(unregisterResult, org.hamcrest.Matchers.containsString("test-intercept-id-12345"));
  }

  // ============================================================
  // receive() single-arg test specification for #531
  // ============================================================

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:InterceptEventMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with an InterceptEventMsg sent through it
    // - Create a REQ/REP socket pair
    // - Send a valid InterceptEventMsg with:
    //   - For REGISTER type: a byte[] body
    //   - Or for UNREGISTER type: an interceptMessageId string

    // When: receive(socket) is called (single-arg version, non-blocking)

    // Then:
    // - Returns a valid InterceptEventMsg when message is available
    // - The message fields (type, body or interceptMessageId) match the sent message
    // - The single-arg method delegates to receive(socket, false)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }
}
