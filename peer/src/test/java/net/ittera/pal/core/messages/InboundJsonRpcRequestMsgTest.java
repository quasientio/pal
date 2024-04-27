/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core.messages;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.util.UUID;
import net.ittera.pal.core.ZmqEnabledTest;
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
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket dealerSocket = zContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(socketAddr);
    ZMQ.Socket repSocket = zContext.createSocket(SocketType.REP);
    repSocket.connect(socketAddr);

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
    logger.debug("sent msgOut= {}", msgOut);

    // receive and compare
    InboundJsonRpcRequestMsg msgIn = InboundJsonRpcRequestMsg.recvMsg(repSocket, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    dealerSocket.close();
    repSocket.close();
    zContext.destroy();
  }

  @Test
  public void sendViaPush() {

    final String pushAddress = "inproc://websocket-push";
    ZContext zContext = createContext();
    ZMQ.Socket pushSocket = zContext.createSocket(SocketType.PUSH);
    pushSocket.bind(pushAddress);
    ZMQ.Socket pullSocket = zContext.createSocket(SocketType.PULL);
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
    InboundJsonRpcRequestMsg msgIn = InboundJsonRpcRequestMsg.recvMsg(pullSocket, true);
    logger.debug("received msgIn= {}", msgIn);
    assertThat(msgIn, is(msgOut));

    // close
    pushSocket.close();
    pullSocket.close();
    zContext.destroy();
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
