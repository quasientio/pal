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

import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
class DirectRequestDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(DirectRequestDispatcher.class);

  // zmq stuff
  private final String routerAddress;
  private final String dealerAddress;
  private static final String PROXY_CTRL_ADDR = "inproc://rdprxyctrl";

  private Socket routerSocket;
  private Socket dealerSocket;
  private Socket ctrlSocket;

  @Inject
  public DirectRequestDispatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("DirectRequestDispatcher.service") String serviceName,
      @Named("in.req.tcp") String routerAddress,
      @Named("in.dealer") String dealerAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.routerAddress = routerAddress;
    this.dealerAddress = dealerAddress;
  }

  @Override
  protected void openConnections() {
    // to get requests for dispatchers
    this.routerSocket = zmqContext.createSocket(SocketType.ROUTER);
    routerSocket.bind(routerAddress);
    // to send requests to dispatchers
    this.dealerSocket = zmqContext.createSocket(SocketType.DEALER);
    dealerSocket.bind(dealerAddress);
    // to get proxy termination command
    this.ctrlSocket = zmqContext.createSocket(SocketType.PAIR);
    ctrlSocket.bind(PROXY_CTRL_ADDR);
  }

  @Override
  public final void run() {
    // create router-dealer proxy
    ZMQ.proxy(routerSocket, dealerSocket, null, ctrlSocket);
  }

  @Override
  protected void closeConnections() {
    closeConnection(routerSocket, "Error closing router");
    closeConnection(dealerSocket, "Error closing dealer");
    closeConnection(ctrlSocket, "Error closing ctrl socket");
  }

  private void sendProxyTermCmd() {
    ZMQ.Socket ctrlCliSocket = zmqContext.createSocket(SocketType.PAIR);
    ctrlCliSocket.connect(PROXY_CTRL_ADDR);
    ctrlCliSocket.send(ZMQ.PROXY_TERMINATE);
    ctrlCliSocket.close();
  }

  @Override
  protected void triggerStop() {
    sendProxyTermCmd();
  }
}
