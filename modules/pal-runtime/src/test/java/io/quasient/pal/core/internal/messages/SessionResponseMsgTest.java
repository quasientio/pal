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
import io.quasient.pal.messages.types.SessionStatusType;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SessionResponseMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendAndReceiveResponseMsgNoObjects() {
    SessionResponseMsg msgOut = new SessionResponseMsg(SessionStatusType.OK);

    // send
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);
    msgOut.send(out);

    // receive and compare
    SessionResponseMsg msgIn = SessionResponseMsg.receive(in, true);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  @Test
  public void sendAndReceiveResponseMsgWithObjects() {

    Set<ObjectRef> objectRefs = new HashSet<>();
    objectRefs.add(ObjectRef.from("498324"));
    objectRefs.add(ObjectRef.from("2348632"));
    SessionResponseMsg msgOut = new SessionResponseMsg(SessionStatusType.OK, objectRefs);

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
    SessionResponseMsg msgIn = SessionResponseMsg.receive(in, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single SessionResponseMsg instance
    SessionResponseMsg msg = new SessionResponseMsg(SessionStatusType.OK);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two SessionResponseMsg instances with identical field values
    Set<ObjectRef> objectRefs1 = new HashSet<>();
    objectRefs1.add(ObjectRef.from("12345"));
    objectRefs1.add(ObjectRef.from("67890"));

    Set<ObjectRef> objectRefs2 = new HashSet<>();
    objectRefs2.add(ObjectRef.from("12345"));
    objectRefs2.add(ObjectRef.from("67890"));

    SessionResponseMsg msg1 = new SessionResponseMsg(SessionStatusType.OK, objectRefs1);
    SessionResponseMsg msg2 = new SessionResponseMsg(SessionStatusType.OK, objectRefs2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two SessionResponseMsg instances with different field values
    Set<ObjectRef> objectRefs1 = new HashSet<>();
    objectRefs1.add(ObjectRef.from("12345"));

    Set<ObjectRef> objectRefs2 = new HashSet<>();
    objectRefs2.add(ObjectRef.from("67890"));

    SessionResponseMsg msg1 = new SessionResponseMsg(SessionStatusType.OK, objectRefs1);
    SessionResponseMsg msg2 = new SessionResponseMsg(SessionStatusType.OK, objectRefs2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Different statusType values
    SessionResponseMsg msgDifferentStatus =
        new SessionResponseMsg(SessionStatusType.NO_SUCH_SESSION, objectRefs1);
    assertThat(msg1.equals(msgDifferentStatus), is(false));

    // Same statusType, one with objectRefs and one without
    SessionResponseMsg msgNoRefs = new SessionResponseMsg(SessionStatusType.OK);
    assertThat(msg1.equals(msgNoRefs), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: A SessionResponseMsg instance
    SessionResponseMsg msg = new SessionResponseMsg(SessionStatusType.OK);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two SessionResponseMsg instances with identical field values
    SessionResponseMsg msg1 = new SessionResponseMsg(SessionStatusType.OK);
    SessionResponseMsg msg2 = new SessionResponseMsg(SessionStatusType.OK);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal
    assertThat(msg1.hashCode(), is(msg2.hashCode()));

    // Also test with objectRefs
    Set<ObjectRef> objectRefs1 = new HashSet<>();
    objectRefs1.add(ObjectRef.from("12345"));
    Set<ObjectRef> objectRefs2 = new HashSet<>();
    objectRefs2.add(ObjectRef.from("12345"));

    SessionResponseMsg msgWithRefs1 = new SessionResponseMsg(SessionStatusType.OK, objectRefs1);
    SessionResponseMsg msgWithRefs2 = new SessionResponseMsg(SessionStatusType.OK, objectRefs2);
    assertThat(msgWithRefs1.hashCode(), is(msgWithRefs2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:SessionResponseMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two SessionResponseMsg instances with different field values
    Set<ObjectRef> objectRefs1 = new HashSet<>();
    objectRefs1.add(ObjectRef.from("12345"));

    Set<ObjectRef> objectRefs2 = new HashSet<>();
    objectRefs2.add(ObjectRef.from("67890"));

    SessionResponseMsg msg1 = new SessionResponseMsg(SessionStatusType.OK, objectRefs1);
    SessionResponseMsg msg2 =
        new SessionResponseMsg(SessionStatusType.NO_SUCH_SESSION, objectRefs2);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Note: toString() already has coverage per analysis, but this test ensures comprehensive
   * verification of the output format.
   *
   * <p>Acceptance Criteria: [TEST:SessionResponseMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: A SessionResponseMsg with known field values
    Set<ObjectRef> objectRefs = new HashSet<>();
    objectRefs.add(ObjectRef.from("98765"));
    SessionResponseMsg msg = new SessionResponseMsg(SessionStatusType.OK, objectRefs);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, containsString("SessionResponseMsg"));
    assertThat(result, containsString("OK"));
    assertThat(result, containsString("98765"));
  }

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:SessionResponseMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with a SessionResponseMsg sent through it
    String socketAddress = "inproc://test-receive-single-arg";
    ZContext zmqContext = createContext();
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.bind(socketAddress);
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.connect(socketAddress);

    // Send a valid SessionResponseMsg
    SessionResponseMsg msgOut = new SessionResponseMsg(SessionStatusType.OK);
    msgOut.send(out);

    // When: receive(socket) is called (single-arg version, non-blocking)
    SessionResponseMsg msgIn = SessionResponseMsg.receive(in);

    // Then: Returns a valid SessionResponseMsg when message is available
    assertThat(msgIn, is(msgOut));
    assertThat(msgIn.getStatus(), is(SessionStatusType.OK));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }
}
