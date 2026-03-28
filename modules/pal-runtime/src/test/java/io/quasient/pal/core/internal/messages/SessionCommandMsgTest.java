/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.internal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.SessionCommandType;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SessionCommandMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  // ============================================================
  // equals()/hashCode()/toString() test specifications
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single SessionCommandMsg instance
    UUID sessionId = UUID.randomUUID();
    SessionCommandMsg msg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two SessionCommandMsg instances with identical field values
    UUID sessionId = UUID.randomUUID();
    ObjectRef objectRef = ObjectRef.from("12345");

    SessionCommandMsg msg1 =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    SessionCommandMsg msg2 =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two SessionCommandMsg instances with different field values
    UUID sessionId1 = UUID.randomUUID();
    UUID sessionId2 = UUID.randomUUID();

    // Different sessionId
    SessionCommandMsg msg1 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId1);
    SessionCommandMsg msg2 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Different commandType values
    SessionCommandMsg msgDifferentType = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);
    assertThat(msg1.equals(msgDifferentType), is(false));

    // Same commandType and sessionId, different objectRef (for STORE_OBJECT)
    ObjectRef objectRef1 = ObjectRef.from("12345");
    ObjectRef objectRef2 = ObjectRef.from("67890");
    SessionCommandMsg msgWithRef1 =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId1, objectRef1);
    SessionCommandMsg msgWithRef2 =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId1, objectRef2);
    assertThat(msgWithRef1.equals(msgWithRef2), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: A SessionCommandMsg instance
    SessionCommandMsg msg = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two SessionCommandMsg instances with identical field values
    UUID sessionId = UUID.randomUUID();

    SessionCommandMsg msg1 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);
    SessionCommandMsg msg2 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal
    assertThat(msg1.hashCode(), is(msg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:SessionCommandMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two SessionCommandMsg instances with different field values
    UUID sessionId1 = UUID.randomUUID();
    UUID sessionId2 = UUID.randomUUID();

    SessionCommandMsg msg1 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId1);
    SessionCommandMsg msg2 = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId2);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:SessionCommandMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: A SessionCommandMsg with known field values
    UUID sessionId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    ObjectRef objectRef = ObjectRef.from("98765");
    SessionCommandMsg msg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, containsString("SessionCommandMsg"));
    assertThat(result, containsString("STORE_OBJECT"));
    assertThat(result, containsString(sessionId.toString()));
    assertThat(result, containsString("98765"));
  }

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:SessionCommandMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with a SessionCommandMsg sent through it
    String socketAddress = "inproc://test-receive-single-arg";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);

    // Send a valid SessionCommandMsg
    SessionCommandMsg msgOut = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);
    msgOut.send(out);

    // When: receive(socket) is called (single-arg version, non-blocking)
    // The message should be available since we already sent it
    SessionCommandMsg msgIn = SessionCommandMsg.receive(in);

    // Then: Returns a valid SessionCommandMsg when message is available
    assertThat(msgIn, is(msgOut));
    assertThat(msgIn.getCommand(), is(SessionCommandType.CLEAR_SESSIONS));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
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
