/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.internal.messages;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.quasient.pal.core.ZmqEnabledTest;
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
}
