/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.messages.types.MessageType;
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
}
