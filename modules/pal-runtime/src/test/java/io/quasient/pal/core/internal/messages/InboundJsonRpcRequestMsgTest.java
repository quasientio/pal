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

public class InboundJsonRpcRequestMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void sendViaDealer() {

    // create and connect sockets
    String socketAddress = "inproc://here";
    ZContext zmqContext = createContext();
    ZMQ.Socket dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(socketAddress);
    ZMQ.Socket repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.connect(socketAddress);

    // send
    UUID clientId = UUID.randomUUID();
    String jsonMessage =
        """
        {
          "jsonrpc": "2.0",
          "method": "foo",
          "params": [],
          "id": 1
        }
        """;
    InboundJsonRpcRequestMsg msgOut = new InboundJsonRpcRequestMsg(clientId, jsonMessage);
    msgOut.send(dealerSocket);

    // receive and compare
    InboundJsonRpcRequestMsg msgIn = InboundJsonRpcRequestMsg.receive(repSocket, true);
    assertThat(msgIn, is(msgOut));

    // close
    dealerSocket.close();
    repSocket.close();
    zmqContext.destroy();
  }

  @Test
  public void sendViaPush() {

    final String pushAddress = "inproc://websocket-push";
    ZContext zmqContext = createContext();
    ZMQ.Socket pushSocket = zmqContext.createSocket(SocketType.PUSH);
    pushSocket.bind(pushAddress);
    ZMQ.Socket pullSocket = zmqContext.createSocket(SocketType.PULL);
    pullSocket.connect(pushAddress);

    // send
    UUID clientId = UUID.randomUUID();
    String jsonMessage =
        """
                     {
                       "jsonrpc": "2.0",
                       "method": "foo",
                       "params": [],
                       "id": 1
                     }
                    """;
    InboundJsonRpcRequestMsg msgOut = new InboundJsonRpcRequestMsg(clientId, jsonMessage);
    msgOut.send(pushSocket, false);
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    InboundJsonRpcRequestMsg msgIn = InboundJsonRpcRequestMsg.receive(pullSocket, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    pushSocket.close();
    pullSocket.close();
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
          "method": "foo",
          "params": [],
          "id": 1
        }
        """;
    InboundJsonRpcRequestMsg msg1 = new InboundJsonRpcRequestMsg(clientId, jsonMessage);
    InboundJsonRpcRequestMsg msg2 = new InboundJsonRpcRequestMsg(clientId, jsonMessage);
    assertThat(msg1, is(msg2));

    // test same instance
    assertThat(msg1, is(msg1));

    // test two different instances with different clientId
    UUID clientId2 = UUID.randomUUID();
    InboundJsonRpcRequestMsg msg3 = new InboundJsonRpcRequestMsg(clientId2, jsonMessage);
    assertThat(msg1, is(not(msg3)));

    // test two different instances with different jsonMessage
    String jsonMessage2 =
        """
        {
          "jsonrpc": "2.0",
          "method": "bar",
          "params": [],
          "id": 1
        }
        """;
    InboundJsonRpcRequestMsg msg4 = new InboundJsonRpcRequestMsg(clientId, jsonMessage2);
    assertThat(msg1, is(not(msg4)));
  }

  // ============================================================
  // equals()/hashCode()/toString() test specifications
  // ============================================================

  /**
   * Tests that equals() returns true when comparing an object to itself.
   *
   * <p>Acceptance Criteria: [TEST:InboundJsonRpcRequestMsgTest.testEquals_sameObject_returnsTrue]
   */
  @Test
  public void testEquals_sameObject_returnsTrue() {
    // Given: A single InboundJsonRpcRequestMsg instance
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";
    InboundJsonRpcRequestMsg msg = new InboundJsonRpcRequestMsg(peerId, jsonMessage);

    // When: equals() is called with the same object reference
    // Then: Returns true
    assertThat(msg.equals(msg), is(true));
  }

  /**
   * Tests that equals() returns true when comparing two objects with identical field values.
   *
   * <p>Acceptance Criteria: [TEST:InboundJsonRpcRequestMsgTest.testEquals_equalObjects_returnsTrue]
   */
  @Test
  public void testEquals_equalObjects_returnsTrue() {
    // Given: Two InboundJsonRpcRequestMsg instances with identical field values
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";

    InboundJsonRpcRequestMsg msg1 = new InboundJsonRpcRequestMsg(peerId, jsonMessage);
    InboundJsonRpcRequestMsg msg2 = new InboundJsonRpcRequestMsg(peerId, jsonMessage);

    // When: equals() is called comparing message1 to message2
    // Then: Returns true
    assertThat(msg1.equals(msg2), is(true));
    assertThat(msg2.equals(msg1), is(true));
  }

  /**
   * Tests that equals() returns false when comparing two objects with different field values.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundJsonRpcRequestMsgTest.testEquals_differentObjects_returnsFalse]
   */
  @Test
  public void testEquals_differentObjects_returnsFalse() {
    // Given: Two InboundJsonRpcRequestMsg instances with different field values
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();
    String jsonMessage1 = "{\"jsonrpc\":\"2.0\",\"method\":\"test1\",\"id\":1}";
    String jsonMessage2 = "{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"id\":2}";

    InboundJsonRpcRequestMsg msg1 = new InboundJsonRpcRequestMsg(peerId1, jsonMessage1);
    InboundJsonRpcRequestMsg msg2 = new InboundJsonRpcRequestMsg(peerId2, jsonMessage2);

    // When: equals() is called comparing message1 to message2
    // Then: Returns false
    assertThat(msg1.equals(msg2), is(false));

    // Also test with same peerId, different jsonMessage
    InboundJsonRpcRequestMsg msgSamePeer = new InboundJsonRpcRequestMsg(peerId1, jsonMessage2);
    assertThat(msg1.equals(msgSamePeer), is(false));

    // Different peerId, same jsonMessage
    InboundJsonRpcRequestMsg msgSameJson = new InboundJsonRpcRequestMsg(peerId2, jsonMessage1);
    assertThat(msg1.equals(msgSameJson), is(false));
  }

  /**
   * Tests that equals() returns false when comparing with null.
   *
   * <p>Acceptance Criteria: [TEST:InboundJsonRpcRequestMsgTest.testEquals_null_returnsFalse]
   */
  @Test
  public void testEquals_null_returnsFalse() {
    // Given: An InboundJsonRpcRequestMsg instance
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";
    InboundJsonRpcRequestMsg msg = new InboundJsonRpcRequestMsg(peerId, jsonMessage);

    // When: equals() is called with null
    // Then: Returns false (should not throw NullPointerException)
    assertThat(msg.equals(null), is(false));
  }

  /**
   * Tests that hashCode() returns the same value for equal objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundJsonRpcRequestMsgTest.testHashCode_equalObjects_sameHashCode]
   */
  @Test
  public void testHashCode_equalObjects_sameHashCode() {
    // Given: Two InboundJsonRpcRequestMsg instances with identical field values
    UUID peerId = UUID.randomUUID();
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";

    InboundJsonRpcRequestMsg msg1 = new InboundJsonRpcRequestMsg(peerId, jsonMessage);
    InboundJsonRpcRequestMsg msg2 = new InboundJsonRpcRequestMsg(peerId, jsonMessage);

    // When: hashCode() is called on both objects
    // Then: Both hash codes are equal (required by hashCode contract when equals() returns true)
    assertThat(msg1.hashCode(), is(msg2.hashCode()));
  }

  /**
   * Tests that hashCode() likely returns different values for different objects.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundJsonRpcRequestMsgTest.testHashCode_differentObjects_likelyDifferentHashCode]
   */
  @Test
  public void testHashCode_differentObjects_likelyDifferentHashCode() {
    // Given: Two InboundJsonRpcRequestMsg instances with different field values
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();
    String jsonMessage1 = "{\"jsonrpc\":\"2.0\",\"method\":\"test1\",\"id\":1}";
    String jsonMessage2 = "{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"id\":2}";

    InboundJsonRpcRequestMsg msg1 = new InboundJsonRpcRequestMsg(peerId1, jsonMessage1);
    InboundJsonRpcRequestMsg msg2 = new InboundJsonRpcRequestMsg(peerId2, jsonMessage2);

    // When: hashCode() is called on both objects
    // Then: Hash codes are likely different (not strictly required, but expected)
    assertThat(msg1.hashCode(), is(not(msg2.hashCode())));
  }

  /**
   * Tests that toString() contains relevant field information.
   *
   * <p>Acceptance Criteria: [TEST:InboundJsonRpcRequestMsgTest.testToString_containsRelevantInfo]
   */
  @Test
  public void testToString_containsRelevantInfo() {
    // Given: An InboundJsonRpcRequestMsg with known field values
    UUID peerId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    String jsonMessage = "{\"jsonrpc\":\"2.0\",\"method\":\"testMethod\",\"id\":42}";
    InboundJsonRpcRequestMsg msg = new InboundJsonRpcRequestMsg(peerId, jsonMessage);

    // When: toString() is called
    String result = msg.toString();

    // Then: The returned string contains relevant information
    assertThat(result, containsString("InboundJsonRpcRequestMsg"));
    assertThat(result, containsString(peerId.toString()));
    assertThat(result, containsString("testMethod"));
  }

  // ============================================================
  // receive() single-arg test specification
  // ============================================================

  /**
   * Tests that receive(socket) single-arg version delegates to two-arg version with blocking=false.
   *
   * <p>Acceptance Criteria:
   * [TEST:InboundJsonRpcRequestMsgTest.testReceive_singleArg_delegatesToTwoArgVersion]
   */
  @Test
  public void testReceive_singleArg_delegatesToTwoArgVersion() {
    // Given: A ZMQ socket pair with an InboundJsonRpcRequestMsg sent through it
    String pushAddress = "inproc://test-receive-single-arg";
    ZContext zmqContext = createContext();
    ZMQ.Socket pushSocket = zmqContext.createSocket(SocketType.PUSH);
    pushSocket.bind(pushAddress);
    ZMQ.Socket pullSocket = zmqContext.createSocket(SocketType.PULL);
    pullSocket.connect(pushAddress);

    // Send a valid InboundJsonRpcRequestMsg (without envelope for PUSH/PULL)
    UUID clientId = UUID.randomUUID();
    String jsonMessage =
        """
        {
          "jsonrpc": "2.0",
          "method": "testMethod",
          "params": [],
          "id": 99
        }
        """;
    InboundJsonRpcRequestMsg msgOut = new InboundJsonRpcRequestMsg(clientId, jsonMessage);
    msgOut.send(pushSocket, false);

    // When: receive(socket) is called (single-arg version, non-blocking)
    InboundJsonRpcRequestMsg msgIn = InboundJsonRpcRequestMsg.receive(pullSocket);

    // Then: Returns a valid InboundJsonRpcRequestMsg when message is available
    assertThat(msgIn, is(msgOut));
    assertThat(msgIn.getPeerId(), is(clientId));
    assertThat(msgIn.getJsonMessage(), is(jsonMessage));

    // close
    pushSocket.close();
    pullSocket.close();
    zmqContext.destroy();
  }
}
