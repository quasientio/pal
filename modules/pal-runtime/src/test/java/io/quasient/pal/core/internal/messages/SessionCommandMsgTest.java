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
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.SessionCommandType;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SessionCommandMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  // ============================================================
  // equals()/hashCode()/toString() test specifications for #531
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single SessionCommandMsg instance
    // - Create a SessionCommandMsg with:
    //   - commandType: SessionCommandType.DELETE_SESSION
    //   - sessionId: a random UUID

    // When: equals() is called with the same object reference

    // Then: Returns true

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two SessionCommandMsg instances with identical field values
    // - Both messages have:
    //   - commandType: SessionCommandType.STORE_OBJECT
    //   - sessionId: same UUID value
    //   - objectRef: same ObjectRef value

    // When: equals() is called comparing message1 to message2

    // Then: Returns true

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two SessionCommandMsg instances with different field values
    // - message1 with commandType: DELETE_SESSION, sessionId: UUID1
    // - message2 with commandType: DELETE_SESSION, sessionId: UUID2

    // When: equals() is called comparing message1 to message2

    // Then: Returns false

    // Also test with:
    // - Different commandType values
    // - Same commandType, different sessionId
    // - Same commandType and sessionId, different objectRef (for STORE_OBJECT/DELETE_OBJECT)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testEquals_null_returnsFalse() {
    // Given: A SessionCommandMsg instance
    // - Create message with commandType: CLEAR_SESSIONS

    // When: equals() is called with null

    // Then: Returns false (should not throw NullPointerException)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two SessionCommandMsg instances with identical field values
    // - Both messages have:
    //   - commandType: SessionCommandType.DELETE_SESSION
    //   - sessionId: same UUID value

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
   * [TEST:SessionCommandMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two SessionCommandMsg instances with different field values
    // - message1 with commandType: DELETE_SESSION, sessionId: UUID1
    // - message2 with commandType: DELETE_SESSION, sessionId: UUID2

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
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testToString_containsRelevantInfo() {
    // Given: A SessionCommandMsg with known field values
    // - commandType: SessionCommandType.STORE_OBJECT
    // - sessionId: a specific UUID
    // - objectRef: a specific ObjectRef

    // When: toString() is called

    // Then: The returned string contains:
    // - The class name or identifier "SessionCommandMsg"
    // - The command type name
    // - The session ID
    // - The object reference (if applicable)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:SessionCommandMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  @Ignore("Awaiting implementation in #532")
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with a SessionCommandMsg sent through it
    // - Create a DEALER/REP socket pair
    // - Send a valid SessionCommandMsg (e.g., CLEAR_SESSIONS command)

    // When: receive(socket) is called (single-arg version, non-blocking)

    // Then:
    // - Returns a valid SessionCommandMsg when message is available
    // - The message fields match the sent message
    // - The single-arg method delegates to receive(socket, false)

    // TODO(#532): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  public void sendAndReceiveDeleteSessionCmd() {
    UUID sessionId = UUID.randomUUID();
    SessionCommandMsg msgOut = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);

    // receive and compare
    SessionCommandMsg msgIn = SessionCommandMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendAndReceiveStoreObjectCmd() {
    UUID sessionId = UUID.randomUUID();
    ObjectRef objectRef = ObjectRef.from("239487234");
    SessionCommandMsg msgOut =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);

    // receive and compare
    SessionCommandMsg msgIn = SessionCommandMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendAndReceiveClearSessionsCmd() {
    SessionCommandMsg msgOut = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    SessionCommandMsg msgIn = SessionCommandMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }
}
