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

package com.quasient.pal.core;

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
class ZmqRpcRequestDispatcher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ZmqRpcRequestDispatcher.class);

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
   * Constructs a ZmqRpcRequestDispatcher, initializing network addresses and configuring the
   * service.
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
  public ZmqRpcRequestDispatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("ZmqRpcRequestDispatcher.service") String serviceName,
      @Named("in.rpc") String routerAddress,
      @Named("in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.routerAddress = routerAddress;
    this.dealerAddress = dealerAddress;
    logger.info(
        "ZmqRpcRequestDispatcher created with routerAddress:{}, dealerAddress:{}",
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
    closeConnection(rpcRouterSocket, "Error closing RPC router socket");
    closeConnection(dealerSocket, "Error closing RPC dealer socket");
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
