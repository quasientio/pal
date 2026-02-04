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

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.types.MessageType;
import java.util.UUID;
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
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single OutboundJsonRpcResponseMsg instance
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";
    OutboundJsonRpcResponseMsg msg =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two OutboundJsonRpcResponseMsg instances with identical field values
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";

    OutboundJsonRpcResponseMsg msg1 =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);
    OutboundJsonRpcResponseMsg msg2 =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two OutboundJsonRpcResponseMsg instances with different field values
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();
    String jsonMessage1 = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";
    String jsonMessage2 = "{\"jsonrpc\":\"2.0\",\"result\":\"failure\",\"id\":2}";

    OutboundJsonRpcResponseMsg msg1 =
        new OutboundJsonRpcResponseMsg(peerId1, jsonMessage1, MessageType.UNKNOWN);
    OutboundJsonRpcResponseMsg msg2 =
        new OutboundJsonRpcResponseMsg(peerId2, jsonMessage2, MessageType.UNKNOWN);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Also test with same peerId, different jsonMessage
    OutboundJsonRpcResponseMsg msgSamePeer =
        new OutboundJsonRpcResponseMsg(peerId1, jsonMessage2, MessageType.UNKNOWN);
    assertThat(msg1.equals(msgSamePeer), is(false));

    // Different peerId, same jsonMessage
    OutboundJsonRpcResponseMsg msgSameJson =
        new OutboundJsonRpcResponseMsg(peerId2, jsonMessage1, MessageType.UNKNOWN);
    assertThat(msg1.equals(msgSameJson), is(false));

    // Note: Current equals() implementation does not check messageType,
    // so different messageType with same peerId and jsonMessage will still be equal
    OutboundJsonRpcResponseMsg msgDifferentType =
        new OutboundJsonRpcResponseMsg(peerId1, jsonMessage1, MessageType.EXEC_CONSTRUCTOR);
    assertThat(msg1.equals(msgDifferentType), is(true)); // messageType is not compared
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:OutboundJsonRpcResponseMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: An OutboundJsonRpcResponseMsg instance
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";
    OutboundJsonRpcResponseMsg msg =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two OutboundJsonRpcResponseMsg instances with identical field values
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";

    OutboundJsonRpcResponseMsg msg1 =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);
    OutboundJsonRpcResponseMsg msg2 =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal (required by hashCode contract when equals() returns true)
    assertThat(msg1.hashCode(), is(msg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two OutboundJsonRpcResponseMsg instances with different field values
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();
    String jsonMessage1 = "{\"jsonrpc\":\"2.0\",\"result\":\"success\",\"id\":1}";
    String jsonMessage2 = "{\"jsonrpc\":\"2.0\",\"result\":\"failure\",\"id\":2}";

    OutboundJsonRpcResponseMsg msg1 =
        new OutboundJsonRpcResponseMsg(peerId1, jsonMessage1, MessageType.UNKNOWN);
    OutboundJsonRpcResponseMsg msg2 =
        new OutboundJsonRpcResponseMsg(peerId2, jsonMessage2, MessageType.UNKNOWN);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different (not strictly required, but expected)
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:OutboundJsonRpcResponseMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: An OutboundJsonRpcResponseMsg with known field values
    UUID peerId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"result\":\"testResult\",\"id\":42}";
    OutboundJsonRpcResponseMsg msg =
        new OutboundJsonRpcResponseMsg(peerId, jsonMessage, MessageType.UNKNOWN);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, org.hamcrest.Matchers.containsString("OutboundJsonRpcResponseMsg"));
    assertThat(result, org.hamcrest.Matchers.containsString(peerId.toString()));
    assertThat(result, org.hamcrest.Matchers.containsString("testResult"));
  }

  // ============================================================
  // receive() single-arg test specification for #531
  // ============================================================

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:OutboundJsonRpcResponseMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with an OutboundJsonRpcResponseMsg sent through it
    String socketAddress = "inproc://test-receive-single-arg";
    ZContext zmqContext = createContext();
    ZMQ.Socket dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(socketAddress);
    ZMQ.Socket repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.connect(socketAddress);

    // First send a fake request from DEALER to REP (required before sending response)
    dealerSocket.send("", ZMQ.SNDMORE); // emulate empty envelope
    dealerSocket.send("fake request", 0);
    String receivedString = repSocket.recvStr();
    assertEquals("fake request", receivedString);

    // Send a valid OutboundJsonRpcResponseMsg
    UUID clientId = UUID.randomUUID();
    String jsonRpcMessage =
        """
        {
          "jsonrpc": "2.0",
          "result": "testResult",
          "id": 123
        }
        """;
    OutboundJsonRpcResponseMsg msgOut =
        new OutboundJsonRpcResponseMsg(clientId, jsonRpcMessage, MessageType.UNKNOWN);
    msgOut.send(repSocket);

    // When: receive(socket) is called (single-arg version, non-blocking)
    OutboundJsonRpcResponseMsg msgIn = OutboundJsonRpcResponseMsg.receive(dealerSocket);

    // Then: Returns a valid OutboundJsonRpcResponseMsg when message is available
    assertThat(msgIn, is(msgOut));
    assertThat(msgIn.getPeerId(), is(clientId));
    assertThat(msgIn.getJsonMessage(), is(jsonRpcMessage));

    // close
    dealerSocket.close();
    repSocket.close();
    zmqContext.destroy();
  }
}
