package com.ittera.cometa.concentrator;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
public class JeromqInRequestDispatcher extends AbstractExecutionThreadService implements InRequestMessageDispatcher {

    protected static final Logger logger = LogManager.getLogger(JeromqInRequestDispatcher.class);

    // counters
    private final AtomicInteger messagesRcvd = new AtomicInteger(0);

    // zmq stuff
    private final String routerAddress, dealerAddress;

    @Inject
    private ZContext context;
    private Socket router, dealer;

    private boolean connectionsOpen = false;

    @Inject
    public JeromqInRequestDispatcher(@Named("in.router") String routerAddress, @Named("in.dealer") String dealerAddress) {
        this.routerAddress = routerAddress;
        this.dealerAddress = dealerAddress;
        logger.info("Initialized IN message dispatcher for concentrator");
    }

    protected void openConnections() {
        // to get requests for conc
        this.router = context.createSocket(ZMQ.ROUTER);
        router.bind(routerAddress);

        // to send requests to conc
        this.dealer = context.createSocket(ZMQ.DEALER);
        dealer.bind(dealerAddress);

        connectionsOpen = true;
        logger.info("All connections open");
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
    }

    @Override
    protected void shutDown() throws Exception {

        //print some statistics
        printDebugStats();

        router.close();
        dealer.close();
        context.destroy();

        logger.info("IN request dispatcher shut down");
    }

    protected void printDebugStats() {
        logger.debug("--------STATS--------");
//        logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
        logger.debug("-----END OF STATS-----");
    }

}
