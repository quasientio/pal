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
package io.quasient.pal.core.transport.zmq;

import io.quasient.pal.core.service.ConnectedService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * Handles dispatching of ZeroMQ RPC requests using a router-dealer proxy.
 *
 * <p>This class extends the ConnectedService to manage ZeroMQ socket connections between a router
 * (for incoming requests) and a dealer (for request forwarding), as well as a control socket for
 * terminating the proxy. It is intended to be instantiated as a singleton.
 */
@Singleton
public class ZmqRpcServer extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ZmqRpcServer.class);

  /** Network address where the router socket binds; used to receive incoming RPC requests. */
  private final String routerAddress;

  /** Network address where the dealer socket binds; used to forward RPC requests to dispatchers. */
  private final String dealerAddress;

  /** Internal in-process address for controlling the proxy termination. */
  private static final String PROXY_CTRL_ADDRESS = "inproc://rdprxyctrl";

  /** ZeroMQ socket handling incoming RPC requests with the ROUTER protocol. */
  private Socket rpcRouterSocket;

  /** ZeroMQ socket acting as a DEALER to forward RPC requests. */
  private Socket dealerSocket;

  /** ZeroMQ socket used to receive control commands for proxy termination. */
  private Socket ctrlSocket;

  /**
   * Constructs a ZmqRpcServer, initializing network addresses and configuring the service.
   *
   * @param peerUuid Unique identifier of this peer instance for communication purposes.
   * @param context Shared ZeroMQ context for creating and managing sockets.
   * @param syncSocketAddress Address used for synchronizing service readiness.
   * @param serviceThreadGroup Thread group assigned to manage this service's thread lifecycle.
   * @param serviceName Descriptive name identifying this service instance.
   * @param routerAddress Network address where the router socket binds to accept RPC requests.
   * @param dealerAddress Network address where the dealer socket binds to forward RPC requests.
   */
  @Inject
  public ZmqRpcServer(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("ZmqRpcServer.service") String serviceName,
      @Named("in.zmq.rpc") String routerAddress,
      @Named("in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.routerAddress = routerAddress;
    this.dealerAddress = dealerAddress;
    logger.info(
        "ZmqRpcServer created with routerAddress:{}, dealerAddress:{}",
        routerAddress,
        dealerAddress);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Establishes and binds the ZeroMQ sockets necessary for processing RPC requests:
   *
   * <ul>
   *   <li>A ROUTER socket to receive incoming requests.
   *   <li>A DEALER socket to forward these requests.
   *   <li>A control PAIR socket for managing proxy termination commands.
   * </ul>
   */
  @Override
  protected void openConnections() {
    // to get requests for dispatchers
    this.rpcRouterSocket = zmqContext.createSocket(SocketType.ROUTER);
    rpcRouterSocket.bind(routerAddress);
    // to send requests to dispatchers
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);
    // to get proxy termination command
    this.ctrlSocket = zmqContext.createSocket(SocketType.PAIR);
    ctrlSocket.bind(PROXY_CTRL_ADDRESS);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initiates the ZeroMQ proxy which bridges the router and dealer sockets. This call blocks
   * while the proxy is active.
   */
  @Override
  public final void run() {
    // create router-dealer proxy
    ZMQ.proxy(rpcRouterSocket, dealerSocket, null, ctrlSocket);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the active ZeroMQ connections by attempting to shut down the router, dealer, and
   * control sockets. In the event of a failure to close any socket, an error is logged.
   */
  @Override
  protected void closeConnections() {
    closeConnection(rpcRouterSocket, "Error closing ZMQ-RPC router socket");
    closeConnection(dealerSocket, "Error closing ZMQ-RPC dealer socket");
    closeConnection(ctrlSocket, "Error closing RPC ctrl socket");
  }

  /**
   * Sends a command to terminate the active ZeroMQ proxy.
   *
   * <p>This method creates a temporary PAIR socket, connects to the internal control address, sends
   * the termination command, and then closes the socket.
   */
  private void sendProxyTermCmd() {
    ZMQ.Socket ctrlCliSocket = zmqContext.createSocket(SocketType.PAIR);
    ctrlCliSocket.connect(PROXY_CTRL_ADDRESS);
    ctrlCliSocket.send(ZMQ.PROXY_TERMINATE);
    ctrlCliSocket.close();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initiates a stop request for the service by sending a termination command to the running
   * proxy.
   */
  @Override
  protected void triggerStop() {
    sendProxyTermCmd();
  }
}
