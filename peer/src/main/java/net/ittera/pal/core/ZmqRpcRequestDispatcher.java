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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
class ZmqRpcRequestDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(ZmqRpcRequestDispatcher.class);

  // zmq stuff
  private final String routerAddress;
  private final String dealerAddress;
  private static final String PROXY_CTRL_ADDRESS = "inproc://rdprxyctrl";

  private Socket rpcRouterSocket;
  private Socket dealerSocket;
  private Socket ctrlSocket;

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

  @Override
  public final void run() {
    // create router-dealer proxy
    ZMQ.proxy(rpcRouterSocket, dealerSocket, null, ctrlSocket);
  }

  @Override
  protected void closeConnections() {
    closeConnection(rpcRouterSocket, "Error closing RPC router socket");
    closeConnection(dealerSocket, "Error closing RPC dealer socket");
    closeConnection(ctrlSocket, "Error closing RPC ctrl socket");
  }

  private void sendProxyTermCmd() {
    ZMQ.Socket ctrlCliSocket = zmqContext.createSocket(SocketType.PAIR);
    ctrlCliSocket.connect(PROXY_CTRL_ADDRESS);
    ctrlCliSocket.send(ZMQ.PROXY_TERMINATE);
    ctrlCliSocket.close();
  }

  @Override
  protected void triggerStop() {
    sendProxyTermCmd();
  }
}
