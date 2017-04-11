package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.concentrator.messages.protobuf.data.Primitives;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;


import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Properties;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Future;

import java.io.IOException;
import java.io.InputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Random;
import java.util.UUID;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

import static org.junit.Assert.*;
import org.junit.Test;

public class JeromqDispatcherPOCTest {
    protected final static Logger logger = LogManager.getLogger("tests");
    private static Random rand = new Random(System.currentTimeMillis ());

    private static final int NBR_WORKERS = 30;

    private static class Worker extends Thread {

        private final ZContext zmqContext;
        protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
        protected final UUID clientUuid = UUID.randomUUID();

        Worker(ZContext context) {
           this.zmqContext =  context;
        }

        @Override
        public void run() {

            Socket worker = zmqContext.createSocket(ZMQ.REQ);
//            worker.connect("tcp://localhost:5671");
            worker.connect("inproc://cell");

            boolean done = false;
            while (!done) {

                // create request
                String className = "com.ittera.cometa.demos.App";
                DataMessage dataMsg = dataMessageBuilder.buildEmptyConstructor(clientUuid, className);

                // send request
                worker.send(dataMsg.toByteArray(), 0);
                System.out.println("Sent new Data Message with uuid: "+dataMsg.getMessageUuid());

                // get reply
                byte[] reply = worker.recv(0);
                final String replyString = new String (reply, ZMQ.CHARSET);
                System.out.println("Got reply " + replyString);
                if (replyString.equals("bye")) {
                    done = true;
                    System.out.println("Done");
                }
            }
            worker.close();
        }
    }

    private static class Subscriber extends Thread {

        private final ZContext zmqContext;
        protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
        protected final UUID clientUuid = UUID.randomUUID();

        Subscriber(ZContext context) {
           this.zmqContext =  context;
        }

        @Override
        public void run() {

            Socket kafkaSubscriber = zmqContext.createSocket(ZMQ.SUB);

//            worker.connect("tcp://localhost:5671");
            kafkaSubscriber.connect("inproc://pub");
            kafkaSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

            int messagesReceived = 0;
            boolean done = false;
            while (!done) {

                byte[] reply = kafkaSubscriber.recv(0);
                DataMessage dataMessage = null;
                try {
                    dataMessage = DataMessage.parseFrom(reply);
                } catch(InvalidProtocolBufferException ipbe) {
                    System.err.println("Caught protobuf exception: "+ ipbe.getMessage());
                    ipbe.printStackTrace(System.err);
                }

                if (dataMessage != null) {
                    messagesReceived++;
                    System.out.println("Kafka writer got data message with uuid: "+ dataMessage.getMessageUuid());
                }

            }
            kafkaSubscriber.close();
        }
    }

//    @Test
    public void doit() {
        ZContext context = new ZContext();
        Socket broker = context.createSocket(ZMQ.REP);
//        broker.bind("tcp://*:5671");
        broker.bind("inproc://cell");

        Socket publisher = context.createSocket(ZMQ.PUB);
        publisher.bind("inproc://pub");

        //create some REQuest workers
        for (int workerNbr = 0; workerNbr < NBR_WORKERS; workerNbr++)
        {
            Thread worker = new Worker (context);
            worker.start ();
        }

        //then start 1 subscriber (Kafka writer)
        Subscriber kafkaWriter = new Subscriber(context);
        kafkaWriter.start();

        //  Run for some time and then tell workers to end
        long endTime = System.currentTimeMillis () + 3000;

        int workersFired = 0;
        int messagesReceived = 0;
        while (!Thread.currentThread ().isInterrupted ()) {

            byte[] reply = broker.recv(0);
            DataMessage dataMessage = null;
            try {
                dataMessage = DataMessage.parseFrom(reply);
            } catch(InvalidProtocolBufferException ipbe) {
                System.err.println("Caught protobuf exception: "+ ipbe.getMessage());
                ipbe.printStackTrace(System.err);
            }

            // got a message
            if (dataMessage != null) {
                messagesReceived++;
                System.out.println("Got for dispatch data message with uuid: "+ dataMessage.getMessageUuid());
//                System.out.println(dataMessage.toString());

                // send to subscribers
                publisher.send(dataMessage.toByteArray(), 0);
            }




            if (System.currentTimeMillis () < endTime) {
                String request = "world";
                broker.send(request.getBytes(ZMQ.CHARSET), 0);
            }
            else {
                broker.send ("bye");
                if (++workersFired == NBR_WORKERS) {
                    System.out.println("We're done serving requests. Total served = " + messagesReceived);
                    break;
                }
            }
        }


        broker.close();
        context.destroy();
    }

}
