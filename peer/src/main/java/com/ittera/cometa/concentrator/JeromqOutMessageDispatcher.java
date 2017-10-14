package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

@Singleton
public class JeromqOutMessageDispatcher extends AbstractExecutionThreadService implements OutgoingMessageDispatcher {

    protected static final Logger logger = LogManager.getLogger(JeromqOutMessageDispatcher.class);

    // counters
    private final AtomicLong totalReadBlockingQueueNanos = new AtomicLong(0);
    private final AtomicLong totalPollingNanos = new AtomicLong(0);
    private final AtomicInteger totalReadCalls = new AtomicInteger(0);
    private final AtomicInteger totalPolls = new AtomicInteger(0);
    private final AtomicInteger messagesQueuedToSend = new AtomicInteger(0);
    private final AtomicInteger messagesRcvd = new AtomicInteger(0);

    // zmq stuff
    @Inject
    private ZContext context;
    private Socket broker, publisher;
    private final String outCellAddress, outPubAddress;

    @Inject
    public JeromqOutMessageDispatcher(@Named("out.cell") String outCellAddress,
                                      @Named("out.pub") String outPubAddress) {
        this.outCellAddress = outCellAddress;
        this.outPubAddress = outPubAddress;
        logger.info("Initialized OUT message dispatcher");
    }

    protected void openConnections() {

        broker = context.createSocket(ZMQ.REP);
        broker.bind(outCellAddress);

        publisher = context.createSocket(ZMQ.PUB);
        publisher.connect(outPubAddress);

        logger.info("All connections open");
    }

    @Override
    public final void run() {
        while (isRunning() && !Thread.currentThread().isInterrupted()) {

            byte[] reply = broker.recv(0);
            DataMessage dataMessage = null;
            try {
                dataMessage = DataMessage.parseFrom(reply);
            } catch (InvalidProtocolBufferException ipbe) {
                logger.error("Caught protobuf exception", ipbe);
            }

            // got a message
            if (dataMessage != null) {
                logger.debug("Got for dispatch data message with uuid: " + dataMessage.getMessageUuid());

                // send to subscribers
                publisher.send(dataMessage.toByteArray(), 0);

                // finally, if message has no actors, reply 0 to signal to go ahead with sent message
                if (!hasActors(dataMessage)) {
                    broker.send("0");
                }
            }
        }
    }

    //TODO
    private boolean hasActors(DataMessage message) {
        return false;
    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }

    @Override
    protected void shutDown() throws Exception {

        //print some statistics
        printDebugStats();

        broker.close();
        context.destroy();

        logger.info("Message dispatcher shut down");
    }

    protected void printDebugStats() {
        logger.debug("--------STATS--------");
        logger.debug("# of messages queued to send: {}", messagesQueuedToSend.get());
        logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
        logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
        logger.debug("# polls: {}", totalPolls.get());
        logger.debug("# queue reads: {}", totalReadCalls.get());
        logger.debug("Total waiting time reading from queue in nanoseconds: {}", totalReadBlockingQueueNanos.get());
        logger.debug("-----END OF STATS-----");
    }

}
