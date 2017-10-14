package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import com.google.protobuf.InvalidProtocolBufferException;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
public class LogMessageAsyncInvoker extends AbstractExecutionThreadService implements LogMessageInvoker {

    protected static final Logger logger = LogManager.getLogger(LogMessageAsyncInvoker.class);

    protected AtomicLong requestsDispatched = new AtomicLong(0);

    // zmq stuff
    private ZContext zmqContext;
    private final String inLogAddress;

    private final LogExecutor executor;


    @Inject
    LogMessageAsyncInvoker(@Named("in.log") String inLogAddress, ZContext zmqContext, LogExecutor executor) {
        this.inLogAddress = inLogAddress;
        this.zmqContext = zmqContext;
        this.executor = executor;
    }

    @Override
    public void run() {

        DataMessage requestMsg;
        boolean running = true;

        // connect SUB socket
        Socket kafkaSocket = zmqContext.createSocket(ZMQ.SUB);
        logger.info("Connecting to {}", inLogAddress);
        kafkaSocket.bind(inLogAddress);
        kafkaSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);

        while (running) {

            // recv req
            String offset = kafkaSocket.recvStr();
            logger.debug("Getting message with kafka offset: {}", offset);
            long logOffset = Long.parseLong(offset);

            byte[] req = kafkaSocket.recv(0);
            requestMsg = null;

            logger.debug("received {} bytes", req.length);
            // parse req
            try {
                requestMsg = DataMessage.parseFrom(req);
                logger.debug("message received:\n{}", requestMsg);
            } catch (InvalidProtocolBufferException ipbe) {
                logger.error("Caught protobuf exception", ipbe);
            }

            if (requestMsg != null) {

                // we dispatch only if concentrator uuid isn't ours
                if (!Concentrator.uuid.toString().equals(requestMsg.getConcentratorUuid())) {
                    //dispatch
                    dispatchAsync(requestMsg, logOffset);
                } else {
                    logger.debug("Discarding message with uuid: {}", requestMsg.getMessageUuid());
                }
            }
        }
        kafkaSocket.close();
    }

    protected void dispatchAsync(final DataMessage requestMsg, final long recordOffset) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                DataMessage replyMsg = Concentrator.incomingCall(requestMsg);
                logger.debug("Invoker dispatched log request message uuid: {} and recordOffset: {}, reply uuid: {}",
                        requestMsg.getMessageUuid(), recordOffset, replyMsg.getMessageUuid());
                requestsDispatched.getAndIncrement();
            }
        });
    }
}
