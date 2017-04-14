package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

//    @Inject
//    private ExecutionService executor;
    private static ExecutorService executor = Executors.newCachedThreadPool();

    //TODO if we only need this one thread, then the ThreadLocal is pointless
    // per-thread SUB socket to rcv messages from kafka publisher
    private final ThreadLocal<Socket> kafkaThreadSocket = new ThreadLocal<Socket>() {
        @Override
        protected Socket initialValue() {
            Socket socket = zmqContext.createSocket(ZMQ.SUB);
            socket.connect(inLogAddress);
            socket.subscribe(ZMQ.SUBSCRIPTION_ALL);
            return socket;
        }
    };

    @Inject
    LogMessageAsyncInvoker(@Named("in.log") String inLogAddress, ZContext zmqContext) {
       this.inLogAddress = inLogAddress;
       this.zmqContext = zmqContext;
    }

    @Override
    public void run() {

        DataMessage requestMsg;
        boolean running = true;
        Socket kafkaSocket = kafkaThreadSocket.get();

        while (running) {

            // recv req
            String offset = kafkaSocket.recvStr();
            long logOffset = Long.parseLong(offset);

            byte[] req = kafkaSocket.recv(0);
            requestMsg = null;

            // parse req
            try {
                requestMsg = DataMessage.parseFrom(req);
            } catch (InvalidProtocolBufferException ipbe) {
                logger.error("Caught protobuf exception", ipbe);
            }

            if (requestMsg != null) {

                // we dispatch only if concentrator uuid isn't ours
                if (!Concentrator.uuid.toString().equals(requestMsg.getConcentratorUuid())) {
                    //dispatch
                    dispatchAsync(requestMsg, logOffset);
                } else {
                    logger.debug("Discarding message with uuid: ", requestMsg.getMessageUuid());
                }
            }
        }
        kafkaSocket.close();
    }

    protected void dispatchAsync(final DataMessage requestMsg, final long recordOffset) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                Concentrator.incomingCall(requestMsg, recordOffset);
                logger.debug("Invoker dispatched log data message with uuid: " + requestMsg.getMessageUuid() + " for class: " +
                        requestMsg.getConstructorCall().getClass_().getName());
                requestsDispatched.getAndIncrement();
            }
        });
    }
}
