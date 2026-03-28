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

import io.quasient.pal.core.ZmqEnabledTest;
import java.util.UUID;
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
  // equals()/hashCode()/toString() test specifications
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single PublishedOffsetMsg instance
    long offset = 12345L;
    String messageId = UUID.randomUUID().toString();
    PublishedOffsetMsg msg = new PublishedOffsetMsg(offset, messageId);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two PublishedOffsetMsg instances with identical field values
    long offset = 12345L;
    String messageId = UUID.randomUUID().toString();

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageId);
    PublishedOffsetMsg msg2 = new PublishedOffsetMsg(offset, messageId);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two PublishedOffsetMsg instances with different field values
    String messageId1 = "test-id-1";
    String messageId2 = "test-id-2";

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(100L, messageId1);
    PublishedOffsetMsg msg2 = new PublishedOffsetMsg(200L, messageId2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Same offset, different messageId
    PublishedOffsetMsg msgSameOffset = new PublishedOffsetMsg(100L, messageId2);
    assertThat(msg1.equals(msgSameOffset), is(false));

    // Different offset, same messageId
    PublishedOffsetMsg msgSameId = new PublishedOffsetMsg(200L, messageId1);
    assertThat(msg1.equals(msgSameId), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: A PublishedOffsetMsg instance
    PublishedOffsetMsg msg = new PublishedOffsetMsg(12345L, "test-message-id");

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two PublishedOffsetMsg instances with identical field values
    long offset = 12345L;
    String messageId = "test-message-id";

    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(offset, messageId);
    PublishedOffsetMsg msg2 = new PublishedOffsetMsg(offset, messageId);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal
    assertThat(msg1.hashCode(), is(msg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:PublishedOffsetMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two PublishedOffsetMsg instances with different field values
    PublishedOffsetMsg msg1 = new PublishedOffsetMsg(100L, "id1");
    PublishedOffsetMsg msg2 = new PublishedOffsetMsg(200L, "id2");

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: A PublishedOffsetMsg with known field values
    long offset = 12345L;
    String messageId = "test-unique-message-id";
    PublishedOffsetMsg msg = new PublishedOffsetMsg(offset, messageId);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, containsString("PublishedOffsetMsg"));
    assertThat(result, containsString("12345"));
    assertThat(result, containsString("test-unique-message-id"));
  }

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:PublishedOffsetMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with a PublishedOffsetMsg sent through it
    String socketAddress = "inproc://test-receive-single-arg";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);

    // Send a valid PublishedOffsetMsg
    long offset = 98765L;
    String messageId = UUID.randomUUID().toString();
    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageId);
    msgOut.send(out);

    // When: receive(socket) is called (single-arg version, non-blocking)
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.receive(in);

    // Then: Returns a valid PublishedOffsetMsg when message is available
    assertThat(msgIn, is(msgOut));
    assertThat(msgIn.getOffset(), is(offset));
    assertThat(msgIn.getMessageId(), is(messageId));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }

  /**
   * Tests that send() writes message data correctly to the ZMQ socket.
   *
   * <p>Acceptance Criteria: [TEST:PublishedOffsetMsgTest.testSend_writesMessageToSocket]
   */
  @Test
  public void testSend_writesMessageToSocket() {
    // Given: A valid PublishedOffsetMsg instance and a ZMQ socket pair
    long offset = 98765L;
    String messageId = "test-send-message-id";
    PublishedOffsetMsg msgOut = new PublishedOffsetMsg(offset, messageId);

    String socketAddress = "inproc://test-send";
    ZContext zmqContext = createContext();
    ZMQ.Socket in = zmqContext.createSocket(SocketType.REP);
    in.bind(socketAddress);
    ZMQ.Socket out = zmqContext.createSocket(SocketType.REQ);
    out.connect(socketAddress);

    // When: send(socket) is called
    boolean sendResult = msgOut.send(out);

    // Then: Returns true indicating successful send
    assertThat(sendResult, is(true));

    // The message size is correctly calculated (8 bytes for offset + messageId bytes)
    int expectedSize = 8 + messageId.getBytes(ZMQ.CHARSET).length;
    assertThat(msgOut.getSize(), is(expectedSize));

    // The message can be received and reconstructed with matching fields
    PublishedOffsetMsg msgIn = PublishedOffsetMsg.receive(in, true);
    assertThat(msgIn.getOffset(), is(offset));
    assertThat(msgIn.getMessageId(), is(messageId));

    // close
    out.close();
    in.close();
    zmqContext.destroy();
  }
}
