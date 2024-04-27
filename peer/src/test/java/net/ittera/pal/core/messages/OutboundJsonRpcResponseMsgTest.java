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
import static org.junit.Assert.*;

import java.util.UUID;
import net.ittera.pal.core.ZmqEnabledTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class OutboundJsonRpcResponseMsgTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  @Test
  public void send() {

    // create and connect sockets
    String socketAddr = "inproc://here";
    ZContext zContext = createContext();
    ZMQ.Socket dealerSocket = zContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(socketAddr);
    ZMQ.Socket repSocket = zContext.createSocket(SocketType.REP);
    repSocket.connect(socketAddr);

    // before sending a reply from REP to DEALER, we need to receive a request from DEALER
    dealerSocket.send("", ZMQ.SNDMORE); // emulate empty envelope
    dealerSocket.send("fake request", 0);
    String recvdStr = repSocket.recvStr();
    assertEquals("fake request", recvdStr);

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
    OutboundJsonRpcResponseMsg msgOut = new OutboundJsonRpcResponseMsg(clientId, jsonRpcMessage);
    msgOut.send(repSocket);

    // receive and compare
    OutboundJsonRpcResponseMsg msgIn = OutboundJsonRpcResponseMsg.recvMsg(dealerSocket, true);
    assertThat(msgIn, is(msgOut));

    // close
    dealerSocket.close();
    repSocket.close();
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
              "result": "foo",
              "id": 1
            }
            """;
    OutboundJsonRpcResponseMsg msg1 = new OutboundJsonRpcResponseMsg(clientId, jsonMessage);
    OutboundJsonRpcResponseMsg msg2 = new OutboundJsonRpcResponseMsg(clientId, jsonMessage);
    assertThat(msg1, is(msg2));

    // test same instance
    assertThat(msg1, is(msg1));

    // test two different instances with different clientId
    UUID clientId2 = UUID.randomUUID();
    OutboundJsonRpcResponseMsg msg3 = new OutboundJsonRpcResponseMsg(clientId2, jsonMessage);
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
    OutboundJsonRpcResponseMsg msg4 = new OutboundJsonRpcResponseMsg(clientId, jsonMessage2);
    assertThat(msg1, is(not(msg4)));
  }
}
