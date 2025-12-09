/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.websocket;

import com.quasient.pal.common.util.Strings;
import com.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import com.quasient.pal.core.internal.messages.OutboundJsonRpcResponseMsg;
import com.quasient.pal.core.service.ConnectedService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
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
 * <p>It establishes a WebSocket server to handle incoming requests and uses a ZeroMQ DEALER socket
 * to forward these requests to dispatcher threads. The class leverages a push-pull pattern to avoid
 * sharing the dealer socket among multiple threads.
 */
@SuppressFBWarnings(
    value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
    justification = "Server and socket initialized in openConnections() - two-phase initialization")
@Singleton
public class JsonRpcRequestServer extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(JsonRpcRequestServer.class);

  /** Address used to bind and establish the WebSocket endpoint. */
  private final String websocketAddress;

  /**
   * Instance of the WebSocket server responsible for handling JSON-RPC request communication. It is
   * initialized in the openConnections method.
   */
  private JsonRpcWebSocketServer webSocketServer;

  /** Address for the ZeroMQ DEALER socket used in dispatching JSON-RPC requests. */
  private final String dealerAddress;

  /**
   * ZeroMQ DEALER socket used to send JSON-RPC requests to dispatcher threads. This socket is
   * created and bound in the openConnections method.
   */
  private Socket dealerSocket;

  /**
   * Queue for inbound JSON-RPC request messages received from WebSocket clients. The queue
   * decouples the WebSocket receiving thread from the processing dispatcher.
   */
  private final BlockingQueue<InboundJsonRpcRequestMsg> requestQueue = new LinkedBlockingQueue<>();

  /**
   * Constructs a new instance of JsonRpcRequestServer.
   *
   * <p>This constructor initializes the dispatcher with the ZeroMQ context, address configurations,
   * service thread group, and a unique identifier for the peer.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context used for creating and managing sockets.
   * @param syncSocketAddress synchronization socket address to coordinate service readiness.
   * @param serviceThreadGroup thread group for managing the dispatcher and related threads.
   * @param serviceName human-readable name identifying the service instance.
   * @param websocketAddress address on which the JSON-RPC WebSocket server is bound.
   * @param dealerAddress address for the ZeroMQ DEALER socket used to dispatch requests.
   */
  @Inject
  public JsonRpcRequestServer(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("JsonRpcRequestServer.service") String serviceName,
      @Named("in.json.rpc") String websocketAddress,
      @Named("json.in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.websocketAddress = websocketAddress;
    this.dealerAddress = dealerAddress;
  }

  /**
   * Opens network connections required for JSON-RPC request dispatching.
   *
   * <p>This method extracts the hostname and port from the websocketAddress to initialize and start
   * the JSON-RPC WebSocket server. It then creates a DEALER socket from the ZeroMQ context, binding
   * it to the configured dealerAddress for dispatching messages.
   */
  @Override
  protected void openConnections() {
    // to get remote requests
    String hostnameAndPort = Strings.stringAfter(websocketAddress, "ws://");
    String hostname = Strings.stringBefore(hostnameAndPort, ":");
    int port = Integer.parseInt(Strings.stringAfter(hostnameAndPort, ":"));
    CountDownLatch wsServerReadyLatch = new CountDownLatch(1);
    webSocketServer =
        new JsonRpcWebSocketServer(
            new InetSocketAddress(hostname, port), requestQueue, wsServerReadyLatch);

    // start WS server (async)
    webSocketServer.start();

    // create and bind dealer socket, to send the requests to the dispatcher threads
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);

    // wait for the websocket server to be ready
    try {
      wsServerReadyLatch.await();
    } catch (InterruptedException e) {
      // best we can do here is re-throw
      throw new RuntimeException(e);
    }
  }

  /**
   * Main execution loop for dispatching JSON-RPC messages.
   *
   * <p>This method continuously processes inbound messages from the WebSocket request queue,
   * sending each via the ZeroMQ DEALER socket. Additionally, it polls the DEALER socket for any
   * response messages and forwards them to the appropriate WebSocket clients. The loop terminates
   * when a shutdown is requested, the thread is interrupted, or a critical socket error occurs.
   */
  @Override
  public final void run() {

    // create poller and register socket
    ZMQ.Poller poller = zmqContext.createPoller(1);
    poller.register(dealerSocket, ZMQ.Poller.POLLIN);

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
            webSocketServer.sendResponseToWebSocketClient(
                responseMsg.getPeerId(),
                responseMsg.getJsonMessage(),
                responseMsg.getMessageType());
          } else {
            logger.warn("Unexpected null response message");
          }
        }

        // Sleep briefly to prevent busy waiting
        Thread.sleep(1);

      } catch (InterruptedException e) {
        logger.info("Dispatcher thread interrupted, shutting down.");
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

  /**
   * Closes network connections and cleans up resources.
   *
   * <p>This method stops the JSON-RPC WebSocket server and safely closes the ZeroMQ DEALER socket.
   */
  @Override
  protected void closeConnections() {
    webSocketServer.close();
    closeConnection(dealerSocket, "Error closing JSON-RPC dealer socket");
  }
}
