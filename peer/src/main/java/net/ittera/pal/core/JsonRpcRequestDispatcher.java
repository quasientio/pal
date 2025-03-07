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

package net.ittera.pal.core;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * This class is responsible for receiving JSON-RPC requests from WebSocket clients and dispatching
 * them to the queue of RPCMessageInvoker threads.
 *
 * <p>To avoid sharing the dealer socket among multiple threads, we use the PUSH/PULL pattern. The
 * WebSocket server thread pushes the requests to the push socket and the main dispatcher thread
 * pulls them and sends them to the dealer socket.
 */
@Singleton
class JsonRpcRequestDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestDispatcher.class);

  // websocket stuff
  private final String websocketAddress;
  private JsonRpcWebSocketServer webSocketServer;

  // zmq stuff
  private final String dealerAddress;
  private Socket dealerSocket;

  private final BlockingQueue<InboundJsonRpcRequestMsg> requestQueue = new LinkedBlockingQueue<>();

  @Inject
  public JsonRpcRequestDispatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("JsonRpcRequestDispatcher.service") String serviceName,
      @Named("in.jsonrpc") String websocketAddress,
      @Named("json.in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.websocketAddress = websocketAddress;
    this.dealerAddress = dealerAddress;
  }

  @Override
  protected void openConnections() {
    // to get remote requests
    String hostnameAndPort = Strings.stringAfter(websocketAddress, "ws://");
    String hostname = Strings.stringBefore(hostnameAndPort, ":");
    int port = Integer.parseInt(Strings.stringAfter(hostnameAndPort, ":"));
    webSocketServer =
        new JsonRpcWebSocketServer(new InetSocketAddress(hostname, port), requestQueue);

    // to send the requests to the dispatcher threads
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);
  }

  @Override
  public final void run() {

    // create poller and register socket
    ZMQ.Poller poller = zmqContext.createPoller(1);
    poller.register(dealerSocket, ZMQ.Poller.POLLIN);

    // start WS server
    webSocketServer.start();
    boolean socketError = false;

    while (!shutdownRequested && !Thread.interrupted() && !socketError) {

      try {
        // process incoming WebSocket requests from the queue
        InboundJsonRpcRequestMsg requestMsg = requestQueue.poll();
        if (requestMsg != null) {
          boolean sent = requestMsg.send(dealerSocket);
          if (logger.isDebugEnabled()) {
            logger.debug("Sent message from peer w/id: {} to dispatchers", requestMsg.getPeerId());
          }
          if (!sent) {
            logger.error("Error dealing message for dispatch: {}", requestMsg);
          }
        }

        // get responses from DEALER socket and forward to WebSocket clients
        int events = poller.poll(0);
        if (events > 0 && poller.pollin(0)) {
          OutboundJsonRpcResponseMsg responseMsg =
              OutboundJsonRpcResponseMsg.receive(dealerSocket, true);
          if (responseMsg != null) {
            String jsonRpcResponse = responseMsg.getJsonMessage();
            webSocketServer.sendResponseToWebSocketClient(responseMsg.getPeerId(), jsonRpcResponse);
          } else {
            logger.warn("Unexpected null response message");
          }
        }

        // Sleep briefly to prevent busy waiting
        Thread.sleep(1);

      } catch (InterruptedException e) {
        logger.info("Dispatcher thread interrupted, shutting down.");
        Thread.currentThread().interrupt();
        break;
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM || errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("ZeroMQ exception during polling. Error code: {}", errorCode);
          }
          socketError = true;
        } else {
          throw ex;
        }
      }
    }
  }

  @Override
  protected void closeConnections() {
    webSocketServer.close();
    closeConnection(dealerSocket, "Error closing JSON-RPC dealer socket");
  }
}
