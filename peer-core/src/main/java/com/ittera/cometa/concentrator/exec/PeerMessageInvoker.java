package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class PeerMessageInvoker extends Thread {

    protected static final Logger logger = LoggerFactory.getLogger(PeerMessageInvoker.class);
    // zmq stuff
    private ZContext zmqContext;
    private final String dealerAddress;

    protected AtomicLong requestsDispatched = new AtomicLong(0);

    public PeerMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext, String dealerAddress) {
        super(group, target, name);
        this.zmqContext = zmqContext;
        this.dealerAddress = dealerAddress;
        logger.debug("Initialized new peer message invoker with dealerAddress: {}", dealerAddress);
    }

    @Override
    public void run() {

        // create REP socket
        Socket socket = zmqContext.createSocket(ZMQ.REP);
        socket.connect(dealerAddress);

        boolean running = true;
        DataMessage requestMsg, replyMsg;

        logger.debug("Start getting requests from socket");

        while (running) {

            // recv req
            byte[] req = socket.recv(0);

            final long started = System.currentTimeMillis();

            requestMsg = null;

            // parse req
            try {
                requestMsg = DataMessage.parseFrom(req);
            } catch (Exception e) {
                logger.error("Caught exception parsing message", e);
            }

            logger.debug("Received req message with uuid: {}", requestMsg.getMessageUuid());

            if (requestMsg != null) {

                //dispatch
                replyMsg = dispatch(requestMsg);

                //send reply
                socket.send(replyMsg.toByteArray(), 0);

                if (logger.isDebugEnabled()) {
                    final long took = System.currentTimeMillis() - started;
                    logger.debug("Dispatched and sent data message reply with uuid: {} in {} millisecs", requestMsg.getMessageUuid(), took);
                }


                if (requestsDispatched.incrementAndGet() % 25 == 0) {
                    // print #some stats
                    logger.debug("# of messages dispatched: {}", requestsDispatched);
                }
            }
        }
        socket.close();
    }

    private DataMessage dispatch(DataMessage requestMsg) {
        DataMessage replyMsg = Concentrator.incomingCall(requestMsg);
        logger.debug("Invoker dispatched peer request message uuid: {}, reply uuid: {}", requestMsg.getMessageUuid(), replyMsg.getMessageUuid());
        return replyMsg;
    }
}
