package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * TODO A 2nd thread that sends non-urgent messages from a queue.
 */

@Singleton
public class KafkaDataMessageWriter extends AbstractExecutionThreadService implements KafkaMessageWriter {

    protected static final Logger logger = LogManager.getLogger(KafkaDataMessageWriter.class);

    // kafka stuff
    private KafkaProducer producer;
    private final String kafkaTopic;
    private final Properties producerProperties = new Properties();

    // zmq stuff
    @Inject
    private ZContext zmqContext;
    private Socket subscriber;
    private final String outPubAddress;

    private volatile boolean connectionsOpen = false;
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    private int messagesReceived = 0;

    @Inject
    public KafkaDataMessageWriter(@Named("bootstrap.servers") String bootstrapServers,
                                  @Named("key.serializer") String keySerializer,
                                  @Named("value.serializer") String valueSerializer,
                                  @Named("kafkaTopic") String kafkaTopic,
                                  @Named("out.pub") String outPubAddress) {
        this.kafkaTopic = kafkaTopic;
        this.outPubAddress = outPubAddress;
        producerProperties.put("key.serializer", keySerializer);
        producerProperties.put("value.serializer", valueSerializer);
        producerProperties.put("bootstrap.servers", bootstrapServers);
        logger.info("Initialized kafka message writer for concentrator with topic '{}'", kafkaTopic);
    }

    public void openConnections() {

        // start kafka writer
        this.producer = new KafkaProducer<>(producerProperties);

        // start subscriber
        this.subscriber = zmqContext.createSocket(ZMQ.SUB);

        subscriber.connect(outPubAddress);
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

        connectionsOpen = true;
        logger.info("All connections open");
    }

    @Override
    public void run() {

        //wait for connections established
        while (!connectionsOpen) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                //what to do
            }
        }

        logger.debug("Starting to dispatch messages to kafka");
        while (isRunning()) {
            byte[] reply;
            reply = subscriber.recv(ZMQ.NOBLOCK);
            while (reply != null) {
                DataMessage dataMessage = null;
                try {
                    dataMessage = DataMessage.parseFrom(reply);
                } catch (InvalidProtocolBufferException ipbe) {
                    logger.error("Caught protobuf exception", ipbe);
                }

                // got a message
                if (dataMessage != null) {
                    messagesReceived++;

                    // send to kafka immediately
                    sendToKafka(dataMessage);
                }

                reply = subscriber.recv(ZMQ.NOBLOCK);
            }

            // short pause, not to be eager
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                logger.error("Interrupted in sleep", e);
            }
        }
    }

    private void sendToKafka(DataMessage message) {
        ProducerRecord newRecord = new ProducerRecord(kafkaTopic, message);
        producer.send(newRecord);
        messagesSent.getAndIncrement();
        logger.debug("new message sent with uuid: {} replying to message uuid: {}", message.getMessageUuid(),
                message.getFollowingUuid());
    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }
}
