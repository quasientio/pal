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

import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.common.util.UUIDUtils;
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

@Singleton
class JSONRPCRequestDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(JSONRPCRequestDispatcher.class);
  private final Map<WebSocket, UUID> webSocketClientMapping = new HashMap<>();

  // websocket stuff
  private final String websocketAddress;
  private WebSocketServer webSocketServer;

  // zmq stuff
  private final String dealerAddress;
  private Socket dealerSocket;

  @Inject
  public JSONRPCRequestDispatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("JSONRPCRequestDispatcher.service") String serviceName,
      @Named("in.websocket") String websocketAddress,
      @Named("json.in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.websocketAddress = websocketAddress;
    this.dealerAddress = dealerAddress;
  }

  @Override
  protected void openConnections() {
    // to get requests for dispatchers
    String hostname = Strings.stringBefore(websocketAddress, ":");
    int port = Integer.parseInt(Strings.stringAfter(websocketAddress, ":"));
    webSocketServer = new InternalWebSocketServer(new InetSocketAddress(hostname, port));

    // to send requests to dispatchers
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);
  }

  @Override
  public final void run() {
    webSocketServer.start();
    boolean socketError = false;
    while (!shutdownRequested && !Thread.interrupted()) {
      try {
        // TODO
        // get requests from DEALER socket and forward to WebSocket clients
        byte[] buff = dealerSocket.recv(0);
        String clientId = null;
        String message = null;

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
  }

  @Override
  protected void closeConnections() {
    try {
      webSocketServer.stop(
          InternalWebSocketServer.STOP_TIMEOUT_MS, InternalWebSocketServer.CLOSE_MSG);
    } catch (InterruptedException e) {
      logger.error("Error closing WebSocket server", e);
    }

    closeConnection(dealerSocket, "Error closing JSONRPC dealer socket");
  }

  private void sendMessageToDispatchers(UUID clientId, String message) {
    byte[] buff = UUIDUtils.toBytes(clientId);
    dealerSocket.send(buff, ZMQ.SNDMORE);
    buff = message.getBytes();
    dealerSocket.send(buff, 0);
  }

  private WebSocket getClientSocketFromId(String clientId) {
    return webSocketClientMapping.entrySet().stream()
        .filter(e -> e.getValue().equals(clientId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public void sendResponseToWebSocketClient(String clientId, String response) {
    WebSocket clientSocket = getClientSocketFromId(clientId);
    clientSocket.send(response);
  }

  private class InternalWebSocketServer extends WebSocketServer {

    private static final int STOP_TIMEOUT_MS = 2000;
    private static final String CLOSE_MSG = "Closing WebSocket server. Bye!";

    public InternalWebSocketServer(InetSocketAddress address) {
      super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
      // TODO
      // Assign unique ID and add to mapping
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      // TODO
      // Remove client from mapping
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
      // Forward message to ZeroMQ DEALER socket with client ID
      UUID clientId = webSocketClientMapping.get(conn);
      sendMessageToDispatchers(clientId, message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
      // Handle errors
    }

    @Override
    public void onStart() {
      // Server started
    }
  }
}
