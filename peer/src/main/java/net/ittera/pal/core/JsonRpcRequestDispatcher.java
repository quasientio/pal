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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
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
  private final Map<WebSocket, UUID> webSocketConnectionMapping = new HashMap<>();
  private final Map<UUID, ConnectionStats> peerStatsMap = new HashMap<>();

  // websocket stuff
  private final String websocketAddress;
  private WebSocketServer webSocketServer;
  private static final int WS_THREAD_POOL_SIZE = 1;

  // zmq stuff
  private final String dealerAddress;
  private final String pushAddress = "inproc://websocket-push";
  private Socket dealerSocket;
  private Socket pushSocket;

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
        new InternalWebSocketServer(new InetSocketAddress(hostname, port), WS_THREAD_POOL_SIZE);

    // to send the requests to the dispatcher threads
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);

    // PUSH socket for sending messages by the WebSocket server thread
    this.pushSocket = zmqContext.createSocket(SocketType.PUSH);
    pushSocket.bind(pushAddress);
  }

  @Override
  public final void run() {

    // to pull incoming messages from WebSocket server
    Socket pullSocket = zmqContext.createSocket(SocketType.PULL);
    pullSocket.connect(pushAddress);

    // create poller and register sockets
    ZMQ.Poller poller = zmqContext.createPoller(2);
    poller.register(dealerSocket, ZMQ.Poller.POLLIN);
    poller.register(pullSocket, ZMQ.Poller.POLLIN);

    webSocketServer.start();
    boolean socketError = false;
    while (!shutdownRequested && !Thread.interrupted() && !socketError) {
      int signaled;
      try {
        signaled = poller.poll();
      } catch (ZError.IOException e) {
        if (e.getCause() instanceof ClosedChannelException) {
          // we are probably shutting down
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedChannelException during poll. Breaking out.");
          }
          break;
        } else {
          logger.error("Caught unexpected ZError exception during poll", e);
          throw e;
        }
      }

      if (signaled < 1) {
        if (logger.isInfoEnabled()) {
          logger.info("Poller returned from poll with {} sockets ready. Breaking out.", signaled);
        }
        break;
      }

      try {
        // get responses from DEALER socket and forward to WebSocket clients
        if (poller.pollin(0)) {
          OutboundJsonRpcResponseMsg responseMsg =
              OutboundJsonRpcResponseMsg.receive(dealerSocket, true);
          assert responseMsg != null;
          String jsonRpcResponse = responseMsg.getJsonMessage();
          sendResponseToWebSocketClient(responseMsg.getPeerId(), jsonRpcResponse);
        }

        // get requests from WebSocket clients and forward to DEALER socket
        if (poller.pollin(1)) {
          InboundJsonRpcRequestMsg requestMsg = InboundJsonRpcRequestMsg.receive(pullSocket, true);
          assert requestMsg != null;
          boolean sent = requestMsg.send(dealerSocket);
          if (logger.isDebugEnabled()) {
            logger.debug("Sent message from peer w/id: {} to dispatchers", requestMsg.getPeerId());
          }
          if (!sent) {
            logger.error("Error dealing message for dispatch: {}", requestMsg);
          }
        }
      } catch (ClosedSelectorException ex) {
        if (logger.isDebugEnabled()) {
          logger.debug("Caught ClosedSelectorException. Breaking out.");
        }
        socketError = true;
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          socketError = true;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          socketError = true;
        } else {
          throw ex;
        }
      }
    }

    poller.close();
  }

  @Override
  protected void closeConnections() {
    try {
      webSocketServer.stop(
          InternalWebSocketServer.STOP_TIMEOUT_MS, InternalWebSocketServer.CLOSE_MSG);
    } catch (InterruptedException e) {
      logger.error("Error closing WebSocket server", e);
    }

    closeConnection(dealerSocket, "Error closing JSON-RPC dealer socket");
    closeConnection(pushSocket, "Error closing JSON-RPC push socket");
  }

  @Override
  protected void triggerStop() {
    super.triggerStop();
    try {
      webSocketServer.stop();
    } catch (InterruptedException e) {
      logger.error("Error stopping WebSocket server", e);
    }
  }

  private WebSocket getConnectionSocketFromId(UUID connId) {
    return webSocketConnectionMapping.entrySet().stream()
        .filter(e -> e.getValue().equals(connId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public void sendResponseToWebSocketClient(UUID peerId, String response) {
    WebSocket connSocket = getConnectionSocketFromId(peerId);
    if (connSocket == null) {
      logger.error("Error sending back response: no WebSocket peer found for id: {}", peerId);
      return;
    }
    try {
      connSocket.send(response);
      peerStatsMap.get(peerId).incrementTotalMessagesSent();
      logger.debug("Sent back response to peer w/id: {}", peerId);
    } catch (Exception e) {
      logger.error("Error sending back response to peer w/id: {}", peerId, e);
    }
  }

  private class InternalWebSocketServer extends WebSocketServer {

    private static final int STOP_TIMEOUT_MS = 2000;
    private static final String CLOSE_MSG = "Closing WebSocket server. Bye!";

    public InternalWebSocketServer(InetSocketAddress address, int workerCount) {
      super(address, workerCount);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
      UUID peerId;
      if (logger.isDebugEnabled()) {
        logger.debug(
            "New connection from: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());
      }
      if (handshake.hasFieldValue("peer-id")) {
        peerId = UUID.fromString(handshake.getFieldValue("peer-id"));
      } else {
        peerId = UUID.randomUUID();
        logger.debug("No peer-id header found in handshake. Assigned new id: {}", peerId);
      }

      webSocketConnectionMapping.put(conn, peerId);
      peerStatsMap.put(peerId, new ConnectionStats());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Closed connection from: {}",
            conn.getRemoteSocketAddress().getAddress().getHostAddress());
      }
      UUID peerId = webSocketConnectionMapping.remove(conn);
      if (logger.isDebugEnabled()) {
        // log connection status
        ConnectionStats stats = peerStatsMap.get(peerId);
        logger.debug(
            "Stats for peer w/id: {} -> Messages sent: {}, Messages received: {}",
            peerId,
            stats.getTotalMessagesSent(),
            stats.getTotalMessagesReceived());
      }
      peerStatsMap.remove(peerId);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      UUID peerId = webSocketConnectionMapping.get(conn);
      peerStatsMap.get(peerId).incrementTotalMessagesReceived();
      logger.debug("New message from peer w/id: {}", peerId);
      if (logger.isTraceEnabled()) {
        logger.trace("Message received: {}", message);
      }
      InboundJsonRpcRequestMsg requestMsg = new InboundJsonRpcRequestMsg(peerId, message);
      boolean sentOk = requestMsg.send(pushSocket, false);
      if (logger.isDebugEnabled()) {
        logger.debug("Pushed message from peer w/id: {} for dispatch", peerId);
      }
      if (!sentOk) {
        logger.error("Error pushing message for dispatch: {}", message);
      }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
      logger.error("Error on WebSocket connection", ex);
    }

    @Override
    public void onStart() {
      logger.info("WebSocket server started on: {}", websocketAddress);
    }
  }

  private static class ConnectionStats {
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);
    private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);

    public void incrementTotalMessagesSent() {
      totalMessagesSent.incrementAndGet();
    }

    public void incrementTotalMessagesReceived() {
      totalMessagesReceived.incrementAndGet();
    }

    public long getTotalMessagesSent() {
      return totalMessagesSent.get();
    }

    public long getTotalMessagesReceived() {
      return totalMessagesReceived.get();
    }
  }
}
