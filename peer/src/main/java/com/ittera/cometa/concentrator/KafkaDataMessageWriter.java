package com.ittera.cometa.concentrator;

import com.ittera.cometa.client.PeerLogDirectory;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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
public class KafkaDataMessageWriter extends AbstractExecutionThreadService implements KafkaMessageWriter, Callback {

    protected static final Logger logger = LogManager.getLogger(KafkaDataMessageWriter.class);

    // kafka stuff
    private KafkaProducer producer;
    private String kafkaTopic;
    private final Properties producerProperties = new Properties();

    // zmq stuff
    @Inject
    private ZContext zmqContext;
    private Socket subscriber;
    private Socket offsetPublisher;
    private final String outPubAddress, offsetPubAddress;

    // zookeeper
    @Inject
    private PeerLogDirectory peerLogDirectory;

    private volatile boolean connectionsOpen = false;
    private final AtomicInteger messagesSent = new AtomicInteger(0);
    private int messagesReceived = 0;

    @Inject
    public KafkaDataMessageWriter(@Named("key.serializer") String keySerializer,
                                  @Named("value.serializer") String valueSerializer,
                                  @Named("out.pub") String outPubAddress,
                                  @Named("offset.pub") String offsetPubAddress) {
        this.outPubAddress = outPubAddress;
        this.offsetPubAddress = offsetPubAddress;
        producerProperties.put("key.serializer", keySerializer);
        producerProperties.put("value.serializer", valueSerializer);
        logger.info("Initialized kafka message writer");
    }

    public void openConnections() {

        // start subscriber
        this.subscriber = zmqContext.createSocket(ZMQ.SUB);
        subscriber.bind(outPubAddress);
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        logger.info("Subscriber connected");

        // start offsets publisher
        this.offsetPublisher = zmqContext.createSocket(ZMQ.PUB);
        offsetPublisher.bind(offsetPubAddress);
        logger.info("Publisher connected");

        connectionsOpen = true;
        logger.info("All connections open - except kafka producer");
    }

    @Override
    public void writeToLastLog(String logNamePrefix) throws Exception {

        this.kafkaTopic = peerLogDirectory.getLastLog(logNamePrefix);

        Properties logProps = peerLogDirectory.getLogProperties(kafkaTopic);
        String bootstrapServers = logProps.getProperty("bootstrap.servers");
        producerProperties.put("bootstrap.servers", bootstrapServers);
        // start kafka writer
        this.producer = new KafkaProducer<>(producerProperties);
        logger.info("Will write to log: {} and bootstrapServers: {}", kafkaTopic, bootstrapServers);
    }

    @Override
    public void writeToLog(String logName) throws Exception {

        this.kafkaTopic = logName;

        Properties logProps = peerLogDirectory.getLogProperties(kafkaTopic);
        String bootstrapServers = logProps.getProperty("bootstrap.servers");
        producerProperties.put("bootstrap.servers", bootstrapServers);
        // start kafka writer
        this.producer = new KafkaProducer<>(producerProperties);
        logger.info("Will write to log: {} and bootstrapServers: {}", kafkaTopic, bootstrapServers);
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
        logger.debug("sending new message with uuid: {}", message.getMessageUuid());
        ProducerRecord newRecord = new ProducerRecord(kafkaTopic, message);
        producer.send(newRecord, this);
        messagesSent.getAndIncrement();
        logger.debug("new message sent with uuid: {} replying to message uuid: {}", message.getMessageUuid(),
                message.getFollowingUuid());
    }

    @Override
    protected void shutDown() throws Exception {
        producer.close();
        logger.debug("Closed kafka producer");
        subscriber.close();
        logger.debug("Closed subscriber socket");
        offsetPublisher.close();
        logger.debug("Closed offsetPublisher socket");

    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }

    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        //publish new record offset
        offsetPublisher.send(String.valueOf(recordMetadata.offset()), 0);
        logger.debug("New offset {}", recordMetadata.offset());
    }
}
