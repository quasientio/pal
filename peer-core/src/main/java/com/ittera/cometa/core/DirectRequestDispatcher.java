package com.ittera.cometa.core;

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
  private final String routerAddress, dealerAddress;
  private final String PROXY_CTRL_ADDR = "inproc://rdprxyctrl";

  private Socket router, dealer, ctrl;

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
    this.router = zmqContext.createSocket(SocketType.ROUTER);
    router.bind(routerAddress);
    // to send requests to dispatchers
    this.dealer = zmqContext.createSocket(SocketType.DEALER);
    dealer.bind(dealerAddress);
    // to get proxy termination command
    this.ctrl = zmqContext.createSocket(SocketType.PAIR);
    ctrl.bind(PROXY_CTRL_ADDR);
  }

  @Override
  public final void run() {
    // create router-dealer proxy
    ZMQ.proxy(router, dealer, null, ctrl);
  }

  @Override
  protected void closeConnections() {
    closeConnection(router, "Error closing router");
    closeConnection(dealer, "Error closing dealer");
    closeConnection(ctrl, "Error closing ctrl socket");
  }

  private void sendProxyTermCmd() {
    ZMQ.Socket ctrlCli = zmqContext.createSocket(SocketType.PAIR);
    ctrlCli.connect(PROXY_CTRL_ADDR);
    ctrlCli.send(ZMQ.PROXY_TERMINATE);
    ctrlCli.close();
  }

  @Override
  protected void triggerStop() {
    sendProxyTermCmd();
  }
}
