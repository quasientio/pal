package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class PeerMessageInvoker extends Thread {

    protected static final Logger logger = LogManager.getLogger(PeerMessageInvoker.class);

    private ZContext zmqContext;

    protected AtomicLong requestsDispatched = new AtomicLong(0);

    public PeerMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext) {
        super(group, target, name);
        this.zmqContext = zmqContext;
    }

    @Override
    public void run() {

        // create REP socket
        Socket socket = zmqContext.createSocket(ZMQ.REP);
        socket.connect("inproc://deal");

        boolean running = true;
        DataMessage requestMsg, replyMsg;

        logger.debug("Start getting requests from socket");

        while (running) {
            // recv req
            byte[] req = socket.recv(0);
            requestMsg = null;

            // parse req
            try {
                requestMsg = DataMessage.parseFrom(req);
            } catch (InvalidProtocolBufferException ipbe) {
                logger.error("Caught protobuf exception", ipbe);
            }

            if (requestMsg != null) {

                //dispatch
                replyMsg = dispatch(requestMsg);

                //send reply
                socket.send(replyMsg.toByteArray(), 0);
                logger.debug("Sent dispatched data message reply with uuid: {}", requestMsg.getMessageUuid());


                if (requestsDispatched.incrementAndGet() % 25 == 0) {
                    // print #some stats
                    logger.debug("# of messages dispatched: {}", requestsDispatched);
                }
            }
        }
        socket.close();
    }

    private DataMessage dispatch(DataMessage requestMsg) {
        DataMessage replyMsg = Concentrator.incomingCall(requestMsg, 0);
        logger.debug("Invoker dispatched data message with uuid: " + requestMsg.getMessageUuid() + " for class: " +
                requestMsg.getConstructorCall().getClass_().getName());
        return replyMsg;
    }
}
