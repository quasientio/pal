package com.ittera.cometa.core;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.UUID;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class DirectRequestDispatcher extends ConnectedService {

	private static final Logger logger = LoggerFactory.getLogger(DirectRequestDispatcher.class);

	// zmq stuff
	private final String routerAddress, dealerAddress;
	private final String PROXY_CTRL_ADDR = "inproc://rdprxyctrl";

	private Socket router, dealer, ctrl;

	@Inject
	public DirectRequestDispatcher(UUID peerUuid,
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
		// to get requests for conc
		this.router = zmqContext.createSocket(SocketType.ROUTER);
		router.bind(routerAddress);
		// to send requests to conc
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
		if (router != null) {
			try {
				router.close();
			} catch (Exception e) {
				logger.debug("Error closing router", e);
			}
		}
		if (dealer != null) {
			try {
				dealer.close();
			} catch (Exception e) {
				logger.debug("Error closing dealer", e);
			}
		}
		if (ctrl != null) {
			try {
				ctrl.close();
			} catch (Exception e) {
				logger.debug("Error closing ctrl socket", e);
			}
		}
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
