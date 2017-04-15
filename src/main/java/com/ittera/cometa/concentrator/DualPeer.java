package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.InvalidProtocolBufferException;

public class DualPeer {

    // static
    protected final static Logger logger = LogManager.getLogger(DualPeer.class);
    private static AtomicLong peerIdSeq = new AtomicLong(0);

    private final long peerId = peerIdSeq.incrementAndGet();
    protected final DataMessageBuilder dataMessageBuilder;

    // kafka stuff
    private final String kafkaTopic;
    private final TopicPartition topicPartition;
    private final Long pollTimeout;

    private final Properties kafkaProducerProps = new Properties();
    private final KafkaProducer producer;
    private final KafkaConsumer<String, String> consumer;
    private final Properties kafkaConsumerProps = new Properties();

    // zmq stuff
    private final UUID peerUuid = UUID.randomUUID();
    private final ZContext zmqContext;
    private final Socket peerSocket;
    private String currentPeerAddress;
    private boolean talkingToPeer = false;


    public DualPeer(String propertiesFile) throws IOException {
        this(propertiesFile, null);
    }

    public DualPeer(String propertiesFile, String initialPeerAddress) throws IOException {
        currentPeerAddress = initialPeerAddress;

        //load properties
        final Properties properties = new Properties();
        try (final InputStream stream = DualPeer.class.getResourceAsStream(propertiesFile)) {
            properties.load(stream);
        }

        kafkaTopic = properties.getProperty("kafkaTopic");

        /** Configure and Initialize Kafka Producer **/
        for (String propKey : properties.stringPropertyNames()) {
            if (propKey.startsWith("kafka.producer.")) {
                kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
            } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.consumer")) {
                kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
            }
        }

        kafkaProducerProps.put("client.id", String.valueOf(peerId));
        producer = new KafkaProducer<>(kafkaProducerProps);
        logger.debug("Kafka producer initialized: {}", producer);


        /** Configure and Initialize Kafka Consumer **/
        for (String propKey : properties.stringPropertyNames()) {
            if (propKey.startsWith("kafka.consumer.")) {
                kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka.consumer."), properties.getProperty(propKey));
            } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.producer")) {
                kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
            }
        }

        pollTimeout = Long.parseLong((String) kafkaConsumerProps.remove("pollTimeout"));
        kafkaConsumerProps.put("group.id", properties.getProperty("id"));
        consumer = new KafkaConsumer<>(kafkaConsumerProps);
        logger.debug("Kafka consumer initialized: {}", consumer);

        //manual assignment of partition so we can control offset seek
        topicPartition = new TopicPartition(kafkaTopic, 0);
        consumer.assign(Arrays.asList(topicPartition));
//    consumer.seekToBeginning(Arrays.asList(topicPartition));

        // create zmq context
        logger.debug("Initializing zmq context");
        zmqContext = new ZContext();
        peerSocket = zmqContext.createSocket(ZMQ.REQ);
        if (currentPeerAddress != null) {
            connectToPeer(currentPeerAddress);
        }

        //init msg builder
        dataMessageBuilder = new ProtobufDataMessageBuilder(null);

    }

    private void connectSocket() {
        peerSocket.setIdentity(("Dual-Peer-" + String.valueOf(peerId)).getBytes(ZMQ.CHARSET));
        peerSocket.connect(currentPeerAddress);
    }

    protected DataMessage sendAndReceive(DataMessage message) {
        if (talkingToPeer) {
            return sendToPeer(message);
        } else {
            return sendAndReceiveToLog(message);
        }
    }

    public long getPeerId() {
        return peerId;
    }

    public UUID getPeerUuid() {
        return peerUuid;
    }

    private DataMessage sendAndReceiveToLog(DataMessage message) {

        //send to kafka
        Long sentRecordOffset;
        Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
        try {
            RecordMetadata recordMetadata = recordMetadataFuture.get();
            if (logger.isDebugEnabled()) {
                logger.debug("Message sent:\n{}", getRecordInfo(recordMetadata));
            }
            sentRecordOffset = recordMetadata.offset();
        } catch (Exception e) {
            logger.error("Error getting sent record metadata", e);
            return null;
        }

        //now poll to consume
        logger.debug("Consumer seeking to offset: {}", sentRecordOffset);
        consumer.seek(topicPartition, sentRecordOffset);

        //wait for the reply to the sent message (reply should contain following = sentRecordOffset in message)
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
            for (ConsumerRecord record : records) {
                final DataMessage dataMessage = (DataMessage) record.value();
                long receivedMsgOffset = record.offset();
                if (dataMessage.hasFollowing() && dataMessage.getFollowing() == sentRecordOffset) {
                    logger.info("Got reply with offset {}", receivedMsgOffset);
                    if (dataMessage.hasConcentratorPeerAddr()) {
                        String newPeerAddress = dataMessage.getConcentratorPeerAddr();
                        if (currentPeerAddress != newPeerAddress) {
                            connectToPeer(newPeerAddress);
                        }
                    }
                    return dataMessage;
                } else {
                    logger.debug("Skipping record with offset {}", receivedMsgOffset);
                }
            }
        }
    }

    private void connectToPeer(String peerAddress) {
        logger.info("Switching to direct talk with peer @ {}", peerAddress);
        currentPeerAddress = peerAddress;
        connectSocket();
        talkingToPeer = true;
    }

    private DataMessage sendToPeer(DataMessage message) {

        // send message request to peer
        peerSocket.send(message.toByteArray(), 0);

        byte[] reply = peerSocket.recv(0);

        DataMessage replyMsg = null;
        try {
            replyMsg = DataMessage.parseFrom(reply);
        } catch (InvalidProtocolBufferException ipbe) {
            System.err.println("Caught protobuf exception: " + ipbe.getMessage());
            ipbe.printStackTrace(System.err);
            logger.error("Caught protobuf exception", ipbe);
        }

        logger.debug("Got back Data Message with uuid: " + replyMsg.getMessageUuid());

        return replyMsg;
    }

    private static String getRecordInfo(RecordMetadata recordMetadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n checksum: ").append(recordMetadata.checksum()).append('\n').append(
                " timestamp: ").append(recordMetadata.timestamp()).append('\n').append(
                " offset: ").append(recordMetadata.offset()).append('\n').append(
                " #bytes in value: ").append(recordMetadata.serializedValueSize()).append("\n}");

        return builder.toString();
    }

    @Override
    public void finalize() {
        logger.debug("Finalizing after tests...");
        peerSocket.close();
        zmqContext.destroy();
        logger.debug("Peer socket and context closed.");
        producer.close();
        logger.debug("Producer closed.");
        consumer.close();
        logger.debug("Consumer closed.");
    }
}

