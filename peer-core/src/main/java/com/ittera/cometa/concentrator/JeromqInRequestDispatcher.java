package com.ittera.cometa.concentrator;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
public class JeromqInRequestDispatcher extends AbstractExecutionThreadService implements InRequestMessageDispatcher {

	protected static final Logger logger = LoggerFactory.getLogger(JeromqInRequestDispatcher.class);

	// zmq stuff
	private final String routerAddress, dealerAddress;

	@Inject
	private ZContext context;
	private Socket router, dealer;

	@Inject
	public JeromqInRequestDispatcher(@Named("in.router") String routerAddress, @Named("in.dealer") String dealerAddress) {
		this.routerAddress = routerAddress;
		this.dealerAddress = dealerAddress;
	}

	protected void openConnections() {
		// to get requests for conc
		this.router = context.createSocket(ZMQ.ROUTER);
		router.bind(routerAddress);

		// to send requests to conc
		this.dealer = context.createSocket(ZMQ.DEALER);
		dealer.bind(dealerAddress);

		logger.info("All connections open");
	}

	protected void closeConnections() {
		if (router != null) {
			router.close();
		}

		if (dealer != null) {
			dealer.close();
		}

		logger.info("All connections closed");
	}

	@Override
	public final void run() {

		logger.info("Running router-dealer proxy");

		// create router-dealer proxy
		ZMQ.proxy(router, dealer, null);
	}

	@Override
	protected void startUp() throws Exception {
		openConnections();

		logger.info("Initialized IN message dispatcher");
	}

	@Override
	protected void triggerShutdown() {

		closeConnections();

		logger.info("IN Message dispatcher shutting down.");
	}

	@Override
	protected void shutDown() throws Exception {

		logger.info("IN Message dispatcher shut down");
	}
}
