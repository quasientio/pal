package com.ittera.cometa.concentrator;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
class DirectRequestDispatcher extends AbstractExecutionThreadService {

	private static final Logger logger = LoggerFactory.getLogger(DirectRequestDispatcher.class);

	// zmq stuff
	private final String routerAddress, dealerAddress, proxyCtrlAddress;

	private ZContext context;
	private Socket router, dealer, ctrl;

	@Inject
	public DirectRequestDispatcher(@Named("in.router")String routerAddress,
																 @Named("in.dealer") String dealerAddress,
																 @Named("in.proxy.ctrl") String proxyCtrlAddress,
																 ZContext context) {
		this.routerAddress = routerAddress;
		this.dealerAddress = dealerAddress;
		this.proxyCtrlAddress = proxyCtrlAddress;
		this.context = context;
	}

	private void openConnections() {
		// to get requests for conc
		this.router = context.createSocket(SocketType.ROUTER);
		router.bind(routerAddress);

		// to send requests to conc
		this.dealer = context.createSocket(SocketType.DEALER);
		dealer.bind(dealerAddress);

		// to get proxy termination command
		this.ctrl = context.createSocket(SocketType.PAIR);
		ctrl.bind(proxyCtrlAddress);

		logger.info("All connections open");
	}

	private void closeConnections() {
		if (router != null) {
			router.close();
		}

		if (dealer != null) {
			dealer.close();
		}

		if (ctrl != null) {
			ctrl.close();
		}

		logger.info("All connections closed");
	}

	@Override
	public final void run() {

		logger.info("Running router-dealer proxy");

		// create router-dealer proxy
		ZMQ.proxy(router, dealer, null, ctrl);
		logger.info("Finished running router-dealer proxy");
	}

	@Override
	protected void startUp() {
		openConnections();

		logger.info("Initialized IN message dispatcher");
	}

	@Override
	protected void triggerShutdown() {

		closeConnections();

		logger.info("IN Message dispatcher shutting down.");
	}

	@Override
	protected void shutDown() {

		logger.info("IN Message dispatcher shut down");
	}
}
