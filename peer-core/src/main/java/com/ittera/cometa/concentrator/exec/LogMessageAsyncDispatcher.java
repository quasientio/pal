package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
public class LogMessageAsyncDispatcher extends AbstractExecutionThreadService implements LogMessageDispatcher {

    protected static final Logger logger = LoggerFactory.getLogger(LogMessageAsyncDispatcher.class);

    protected AtomicLong requestsDispatched = new AtomicLong(0);

    // zmq stuff
    private ZContext zmqContext;
    private final String inLogAddress;
    private Socket logSocket;

    private final LogExecutor executor;


    @Inject
    LogMessageAsyncDispatcher(@Named("in.log") String inLogAddress, ZContext zmqContext, LogExecutor executor) {
        this.inLogAddress = inLogAddress;
        this.zmqContext = zmqContext;
        this.executor = executor;
    }

    protected void closeConnections() {

        if (logSocket != null) {
            logSocket.close();
        }

        logger.debug("All connections closed");
    }

    @Override
    public void run() {

        DataMessage requestMsg;

        // connect SUB socket
        logSocket = zmqContext.createSocket(ZMQ.SUB);
        logger.info("Connecting to {}", inLogAddress);
        logSocket.bind(inLogAddress);
        logSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);

        while (isRunning() && !Thread.interrupted()) {

            // recv req
            String offset = null;
            try {
                offset = logSocket.recvStr();
            } catch (ZMQException ex) {
                int errorCode = ex.getErrorCode();
                if (errorCode == ZError.ETERM) {
                    logger.debug("Caught ETERM during blocking read. Breaking out.");
                    break;
                } else if (errorCode == ZError.EINTR) {
                    logger.debug("Caught EINTR during blocking read. Breaking out.");
                    break;
                } else {
                    throw ex;
                }
            }

            logger.debug("Getting message with kafka offset: {}", offset);
            long logOffset = Long.parseLong(offset);

            // recv req body: message
            byte[] req = null;
            try {
                req = logSocket.recv();
            } catch (ZMQException ex) {
                int errorCode = ex.getErrorCode();
                if (errorCode == ZError.ETERM) {
                    logger.debug("Caught ETERM during blocking read. Breaking out.");
                    break;
                } else {
                    throw ex;
                }
            }

            requestMsg = null;

            logger.debug("received {} bytes", req.length);
            // parse req
            try {
                requestMsg = DataMessage.parseFrom(req);
                logger.debug("message received:\n{}", requestMsg);
            } catch (Exception e) {
                logger.error("Caught exception parsing message", e);
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

        closeConnections();
    }

    @Override
    protected void triggerShutdown() {

        logger.info("Log message invoker shutting down");
    }

    @Override
    protected void shutDown() throws Exception {

        logger.info("Log message invoker shut down");
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
