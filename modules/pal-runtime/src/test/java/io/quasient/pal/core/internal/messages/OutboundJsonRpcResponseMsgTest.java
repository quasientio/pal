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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.MessageType;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class OutboundJsonRpcResponseMsgTest extends ZmqEnabledTest {

  @Test
  public void send() {

    // create and connect sockets
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(socketAddress);
    ZMQ.Socket repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.connect(socketAddress);

    // before sending a response from REP to DEALER, we need to receive a request from DEALER
    dealerSocket.send("", ZMQ.SNDMORE); // emulate empty envelope
    dealerSocket.send("fake request", 0);
    String receivedString = repSocket.recvStr();
    assertEquals("fake request", receivedString);

    // create and send a OutboundJsonRpcResponseMsg instance from REP to DEALER
    UUID clientId = UUID.randomUUID();
    String jsonRpcMessage =
        """
        {
          "jsonrpc": "2.0",
          "result": "foo",
          "id": 1
        }
        """;
    OutboundJsonRpcResponseMsg msgOut =
        new OutboundJsonRpcResponseMsg(clientId, jsonRpcMessage, MessageType.UNKNOWN);
    msgOut.send(repSocket);

    // receive and compare
    OutboundJsonRpcResponseMsg msgIn = OutboundJsonRpcResponseMsg.receive(dealerSocket, true);
    assertThat(msgIn, is(msgOut));

    // close
    dealerSocket.close();
    repSocket.close();
    zmqContext.destroy();
  }

  @Test
  public void testEquals() {
    // test two different instances
    UUID clientId = UUID.randomUUID();
    String jsonMessage =
        """
            {
              "jsonrpc": "2.0",
              "result": "foo",
              "id": 1
            }
            """;
    OutboundJsonRpcResponseMsg msg1 =
        new OutboundJsonRpcResponseMsg(clientId, jsonMessage, MessageType.UNKNOWN);
    OutboundJsonRpcResponseMsg msg2 =
        new OutboundJsonRpcResponseMsg(clientId, jsonMessage, MessageType.UNKNOWN);
    assertThat(msg1, is(msg2));

    // test same instance
    assertThat(msg1, is(msg1));

    // test two different instances with different clientId
    UUID clientId2 = UUID.randomUUID();
    OutboundJsonRpcResponseMsg msg3 =
        new OutboundJsonRpcResponseMsg(clientId2, jsonMessage, MessageType.UNKNOWN);
    assertThat(msg1, is(not(msg3)));

    // test two different instances with different jsonMessage
    String jsonMessage2 =
        """
            {
              "jsonrpc": "2.0",
              "error": "bar",
              "id": 1
            }
            """;
    OutboundJsonRpcResponseMsg msg4 =
        new OutboundJsonRpcResponseMsg(clientId, jsonMessage2, MessageType.UNKNOWN);
    assertThat(msg1, is(not(msg4)));
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications for #530
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:OutboundJsonRpcResponseMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single OutboundJsonRpcResponseMsg instance
    // - Create an OutboundJsonRpcResponseMsg with:
    //   - peerId: a random UUID
    //   - jsonMessage: a valid JSON-RPC response string
    //   - messageType: MessageType.UNKNOWN or any valid type

    // When: equals() is called with the same object reference

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two OutboundJsonRpcResponseMsg instances with identical field values
    // - Both messages have:
    //   - peerId: same UUID value
    //   - jsonMessage: identical JSON-RPC response string
    //   - messageType: same MessageType value

    // When: equals() is called comparing message1 to message2

    // Then: Returns true

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two OutboundJsonRpcResponseMsg instances with different field values
    // - message1 with peerId: UUID1, jsonMessage: "response1"
    // - message2 with peerId: UUID2, jsonMessage: "response2"

    // When: equals() is called comparing message1 to message2

    // Then: Returns false

    // Also test with:
    // - Same peerId, different jsonMessage
    // - Different peerId, same jsonMessage
    // - Different messageType (note: current equals() may not check messageType)

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:OutboundJsonRpcResponseMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testEquals_null_returnsFalse() {
    // Given: An OutboundJsonRpcResponseMsg instance
    // - Create message with any valid field values

    // When: equals() is called with null

    // Then: Returns false (should not throw NullPointerException)

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two OutboundJsonRpcResponseMsg instances with identical field values
    // - Both messages have:
    //   - peerId: same UUID value
    //   - jsonMessage: identical JSON-RPC response string
    //   - messageType: same MessageType value

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
   * [TEST:OutboundJsonRpcResponseMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two OutboundJsonRpcResponseMsg instances with different field values
    // - message1 with peerId: UUID1, jsonMessage: "response1"
    // - message2 with peerId: UUID2, jsonMessage: "response2"

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
   * <p>Acceptance Criteria: [TEST:OutboundJsonRpcResponseMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  @Ignore("Awaiting implementation in #530")
  public void testToString_containsRelevantInfo() {
    // Given: An OutboundJsonRpcResponseMsg with known field values
    // - peerId: a specific UUID
    // - jsonMessage: a JSON-RPC response string
    // - messageType: MessageType.UNKNOWN or any valid type

    // When: toString() is called

    // Then: The returned string contains:
    // - The class name or identifier "OutboundJsonRpcResponseMsg"
    // - The peerId value
    // - The jsonMessage or a representation of it

    // TODO(#530): Implement test logic
    fail("Not yet implemented");
  }
}
