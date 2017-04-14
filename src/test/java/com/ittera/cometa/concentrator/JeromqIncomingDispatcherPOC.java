package com.ittera.cometa.concentrator;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.UUID;

public class JeromqIncomingDispatcherPOC {
    protected final static Logger logger = LogManager.getLogger("tests");

    private static ZContext incomingDispatcherCtxt;
    private static final int NBR_CLIENTS = 14;
    private static final int NBR_INVOKER_THREADS = 8;
    private static final int NBR_KAFKA_INVOKER_THREADS = 3;
    private static long requestsDispatched;
    private static boolean fullSpeed = false;
    private static ExecutorService executor = Executors.newCachedThreadPool();


    private static void log(String msg) {
        System.out.printf("thread: %s %s\n", Thread.currentThread().getId(), msg);
    }

    private static class Client extends Thread {

        protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
        protected final UUID clientUuid = UUID.randomUUID();

        @Override
        public void run() {

            final ZContext zmqContext = new ZContext();
            Socket client = zmqContext.createSocket(ZMQ.REQ);
            client.connect("tcp://localhost:5671");

            boolean done = false;
            while (!done) {

                // create request
                String className = "com.ittera.cometa.demos.App";
                DataMessage dataMsg = dataMessageBuilder.buildEmptyConstructor(clientUuid, className);

                // send request
                client.send(dataMsg.toByteArray(), 0);
                log("Sent new Data Message with uuid: " + dataMsg.getMessageUuid());

                // get and parse reply
                byte[] reply = client.recv(0);
                DataMessage replyMsg = null;

                try {
                    replyMsg = DataMessage.parseFrom(reply);
                } catch (InvalidProtocolBufferException ipbe) {
                    System.err.println("Caught protobuf exception: " + ipbe.getMessage());
                    ipbe.printStackTrace(System.err);
                }

                log("Got back Data Message with uuid: " + replyMsg.getMessageUuid());

                // take a break
                if (!fullSpeed) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            client.close();
            zmqContext.destroy();
        }
    }

    private static class KafkaPublisher extends Thread {

        private final ZContext zmqContext;
        protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
        protected final UUID clientUuid = UUID.randomUUID();

        KafkaPublisher(ZContext context) {
           this.zmqContext =  context;
        }

        @Override
        public void run() {

            Socket kafkaSubscriber = zmqContext.createSocket(ZMQ.PUB);

//            kafkaSubscriber.bind("inproc://kafka_in");
            kafkaSubscriber.bind("tcp://*:9999");

            int messagesReceived = 0;
            boolean done = false;
            while (!done) {

                // fake some request coming from kafka
                String className = "com.ittera.cometa.demos.AppKafka";
                DataMessage dataMsg = dataMessageBuilder.buildEmptyConstructor(clientUuid, className);

                // send request
                kafkaSubscriber.send(dataMsg.toByteArray(), 0);
                log("Published new Data Message with uuid: " + dataMsg.getMessageUuid());

                // take a break
                if (!fullSpeed) {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
            kafkaSubscriber.close();
        }
    }

    private static class Conc {
        static DataMessage dispatch(DataMessage dataMessage) {

            return dataMessage;
        }

    }

    private static class ConcInvokerThread extends Thread {
        // per-thread REP socket to rcv messages from dispatcher
        private static final ThreadLocal<Socket> threadSocket = new ThreadLocal<Socket>() {
            @Override
            protected Socket initialValue() {
                Socket worker = incomingDispatcherCtxt.createSocket(ZMQ.REP);
                worker.connect("inproc://deal");
                return worker;
            }
        };

        @Override
        public void run() {

            DataMessage requestMsg, replyMsg;
            long messagesReceived = 0;
            boolean running = true;
            Socket socket = threadSocket.get();

            while (running) {
                // recv req
                byte[] req = socket.recv(0);
                requestMsg = null;

                // parse req
                try {
                    requestMsg = DataMessage.parseFrom(req);
                } catch (InvalidProtocolBufferException ipbe) {
                    System.err.println("Caught protobuf exception: " + ipbe.getMessage());
                    ipbe.printStackTrace(System.err);
                }

                if (requestMsg != null) {
                    messagesReceived++;

                    //dispatch
                    replyMsg = dispatch(requestMsg);

                    //send reply
                    socket.send(replyMsg.toByteArray(), 0);
                    log("Conk sent dispatched data message reply with uuid: " + requestMsg.getMessageUuid());


                    if (++requestsDispatched % 25 == 0) {
                        // print #some stats
                        log("# of messages dispatched: " + requestsDispatched);
                    }
                }
            }
            socket.close();
        }

        private DataMessage dispatch(DataMessage requestMsg) {
            DataMessage replyMsg = Conc.dispatch(requestMsg);
            log("Conk invoker dispatched data message with uuid: " + requestMsg.getMessageUuid()+" for class: "+
                    requestMsg.getConstructorCall().getClass_().getName());
            return replyMsg;
        }
    }

    private static class KafkaConcInvokerThread extends Thread {
        // per-thread SUB socket to rcv messages from kafka
        private static final ThreadLocal<Socket> kafkaThreadSocket = new ThreadLocal<Socket>() {
            @Override
            protected Socket initialValue() {
                Socket worker = incomingDispatcherCtxt.createSocket(ZMQ.SUB);
                worker.connect("tcp://*:9999");
                worker.subscribe(ZMQ.SUBSCRIPTION_ALL);
                return worker;
            }
        };

        @Override
        public void run() {

            DataMessage requestMsg;
            long messagesReceived = 0;
            boolean running = true;
            Socket kafkaSocket = kafkaThreadSocket.get();

            while (running) {

                //now almost the same to process kafka messages

                 // recv req
                byte[] req = kafkaSocket.recv(0);
                requestMsg = null;

                // parse req
                try {
                    requestMsg = DataMessage.parseFrom(req);
                } catch (InvalidProtocolBufferException ipbe) {
                    System.err.println("Caught protobuf exception: " + ipbe.getMessage());
                    ipbe.printStackTrace(System.err);
                }

                if (requestMsg != null) {
                    messagesReceived++;

                    //dispatch
                    dispatchAsync(requestMsg);

                    if (++requestsDispatched % 25 == 0) {
                        // print #some stats
                        log("# of messages dispatched: " + requestsDispatched);
                    }
                }
            }
            kafkaSocket.close();
        }

        private void dispatchAsync(final DataMessage requestMsg) {
            executor.submit(new Runnable() {
                                @Override
                                public void run() {
                                    Conc.dispatch(requestMsg);
                                }
                            });
            log("Conk invoker dispatched async data message with uuid: " + requestMsg.getMessageUuid()+" for class: "+
                    requestMsg.getConstructorCall().getClass_().getName());
        }
    }



//    @Test
    public void doit() {
        ZContext context = new ZContext();
        incomingDispatcherCtxt = context;

        // to get requests for conc
        Socket router = context.createSocket(ZMQ.ROUTER);
        router.bind("tcp://*:5671");

        // to send requests to conc
        Socket dealer = context.createSocket(ZMQ.DEALER);
        dealer.bind("inproc://deal");

        // create some REQuest clients
        for (int clientNbr = 0; clientNbr < NBR_CLIENTS; clientNbr++) {
            Thread client = new Client();
            client.start();
        }

        // create some invoker threads for REQ msgs
        for (int invokerNbr = 0; invokerNbr < NBR_INVOKER_THREADS; invokerNbr++) {
            Thread invoker = new ConcInvokerThread();
            invoker.start();
        }

        // create 1 invoker thread for kafka msgs
        Thread kafkaInvoker = new KafkaConcInvokerThread();
        kafkaInvoker.start();

         // create the kafka publisher thread
        KafkaPublisher kafkaPublisher = new KafkaPublisher(incomingDispatcherCtxt);
        kafkaPublisher.start();

        // create router-dealer proxy
        ZMQ.proxy(router, dealer, null);

        router.close();
        dealer.close();
        context.destroy();
    }

}
