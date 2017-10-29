package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers;

import org.apache.commons.lang3.StringUtils;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.InputStream;

import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.google.protobuf.InvalidProtocolBufferException;

public class ThinPeer {

    // static
    protected final static Logger logger = LoggerFactory.getLogger(ThinPeer.class);
    private static AtomicLong peerIdSeq = new AtomicLong(0);

    private final long peerId = peerIdSeq.incrementAndGet();
    protected final DataMessageBuilder dataMessageBuilder;

    // kafka stuff
    private final String kafkaTopicPrefix, kafkaTopic;
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

    // zookeeper
    private PeerLogDirectory peerLogDirectory;

    public ThinPeer(String propertiesFile) throws Exception {
        this(propertiesFile, null, null);
    }

    public ThinPeer(String propertiesFile, String initialPeerAddress, LogInfo logInfo) throws Exception {
        currentPeerAddress = initialPeerAddress;

        //load properties
        final Properties properties = new Properties();
        try (final InputStream stream = ThinPeer.class.getResourceAsStream(propertiesFile)) {
            properties.load(stream);
        }

        kafkaTopicPrefix = properties.getProperty("kafkaTopicPrefix");

        // connect to log and peer directory
        peerLogDirectory = new ZkClient(properties.getProperty("zookeeper.url"));
        try {
            // register self as new peer
            final Properties peerProperties = new Properties();
            peerLogDirectory.registerPeer(peerUuid, peerProperties);
        } catch (Exception ex) {
            logger.error("Error registering peer", ex);
        }

        // get log to connect to
        String bootstrapServers = null;
        if (logInfo != null) {
            this.kafkaTopic = logInfo.getName();
            bootstrapServers = logInfo.getBootstrapServers();
        }
        else {
            // get last log with prefix = kafkaTopic
            LogInfo lastLog = peerLogDirectory.getLastLog(kafkaTopicPrefix);
            this.kafkaTopic = lastLog.getName();
            bootstrapServers = lastLog.getBootstrapServers();
        }

        logger.info("Will read and write to log: {}", this.kafkaTopic);
        peerLogDirectory.close();


        /** Configure and Initialize Kafka Producer **/
        for (String propKey : properties.stringPropertyNames()) {
            if (propKey.startsWith("kafka.producer.")) {
                kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
            } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.consumer")) {
                kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
            }
        }

        kafkaProducerProps.put("cxn.id", String.valueOf(peerId));
        kafkaProducerProps.put("bootstrap.servers", bootstrapServers);
        logger.info("Will connect to bootstrap servers: {}", bootstrapServers);
        producer = new KafkaProducer<>(kafkaProducerProps);

        /** Configure and Initialize Kafka Consumer **/
        for (String propKey : properties.stringPropertyNames()) {
            if (propKey.startsWith("kafka.consumer.")) {
                kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka.consumer."), properties.getProperty(propKey));
            } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.producer")) {
                kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
            }
        }

        pollTimeout = Long.parseLong((String) kafkaConsumerProps.remove("pollTimeout"));
        kafkaConsumerProps.put("group.id", String.valueOf(peerId));
        kafkaConsumerProps.put("bootstrap.servers", bootstrapServers);
        consumer = new KafkaConsumer<>(kafkaConsumerProps);
        logger.debug("Kafka consumer initialized: {}", consumer);

        //manual assignment of partition so we can control offset seek
        topicPartition = new TopicPartition(kafkaTopic, 0);
        consumer.assign(Arrays.asList(topicPartition));

        // create zmq context
        logger.debug("Initializing zmq context");
        zmqContext = new ZContext();
        peerSocket = zmqContext.createSocket(ZMQ.REQ);
        if (currentPeerAddress != null) {
            connectToPeer(currentPeerAddress);
        }

        //init msg builder
        dataMessageBuilder = new ProtobufDataMessageBuilder();
    }

    private void connectSocket() {
        peerSocket.setIdentity(("Dual-Peer-" + String.valueOf(peerId)).getBytes(ZMQ.CHARSET));
        peerSocket.connect(currentPeerAddress);
    }

    public DataMessage sendAndReceive(DataMessage message) {
        if (talkingToPeer) {
            return sendToPeer(message);
        } else {
            return sendAndReceiveToLog(message);
        }
    }

    public DataMessage waitFor(Wrappers.Type type, String fieldName) {
        logger.info("Starting wait for type: {} and field name: {}", type, fieldName);
        DataMessage reply = null;
        // TODO extra param to seek before
        //consumer.seek(topicPartition, sentRecordOffset);

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
            for (ConsumerRecord record : records) {
                final DataMessage dataMessage = (DataMessage) record.value();
                long receivedMsgOffset = record.offset();

                if (dataMessage.hasStaticFieldPutDone() &&
                        fieldName.equals(dataMessage.getStaticFieldPutDone().getField().getName())) {
                    logger.info("Got matching message with offset {}:\n{}", receivedMsgOffset, dataMessage);
                    return dataMessage;
                } else {
                    logger.debug("Skipping record with offset {}", receivedMsgOffset);
                }
            }
        }
    }

    public DataMessage getMessageAtOffset(long seek) {

        logger.info("Getting message @ offset #{}", seek);
        consumer.seek(topicPartition, seek);

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
            for (ConsumerRecord record : records) {
                if (seek == record.offset()) {
                    return (DataMessage) record.value();
                }
            }
        }
    }

    public List<ConsumerRecord> getMessages(long startOffset, long numMessages) {

        logger.info("Getting {} messages starting @ offset #{}", numMessages, startOffset);
        consumer.seek(topicPartition, startOffset);
        List<ConsumerRecord> messages = new ArrayList();
        boolean gotAllMessages = false;

        while (!gotAllMessages) {
            ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
            logger.debug("got {} records after poll", records.count());
            for (ConsumerRecord record : records) {
                if (record.offset() < startOffset + numMessages) {
                    messages.add(record);
                    gotAllMessages = messages.size() == numMessages;
                }
            }
        }

        return messages;
    }

    public long getPeerId() {
        return peerId;
    }

    public UUID getPeerUuid() {
        return peerUuid;
    }

    public void sendToLogAndForget(DataMessage message) {
        //send to kafka
        producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
        logger.debug("Message sent to log, and we're done:\n{}", message);
    }

    private DataMessage sendAndReceiveToLog(DataMessage message) {

        //send to kafka
        Long sentRecordOffset;
        Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
        try {
            RecordMetadata recordMetadata = recordMetadataFuture.get();
            if (logger.isDebugEnabled()) {
                logger.debug("Message sent with uuid: {} \n{}", message.getMessageUuid(), getRecordInfo(recordMetadata));
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
            if (records.count() != 0) {
                logger.debug("Received {} records", records.count());
            }
            for (ConsumerRecord record : records) {
                final DataMessage dataMessage = (DataMessage) record.value();
                long receivedMsgOffset = record.offset();
                if (dataMessage.hasFollowingUuid() && message.getMessageUuid().equals(dataMessage.getFollowingUuid())) {
                    logger.info("Got reply with offset {} and uuid {} ", receivedMsgOffset, dataMessage.getMessageUuid());
                    String concentratorUuid = dataMessage.getConcentratorUuid();
                    String newPeerAddress = null;
                    try {
                        // we getPeerProperties and close after since we assume we'll get here only once
                        Properties peerProps = peerLogDirectory.getPeerProperties(UUID.fromString(concentratorUuid));
                        peerLogDirectory.close();
                        newPeerAddress = peerProps.getProperty("listenAddress");
                    } catch (Exception ex) {
                        logger.error("Couldn't get peer properties", ex);
                    }
                    if (currentPeerAddress != newPeerAddress) {
                        connectToPeer(newPeerAddress);
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

        final long waitStart = System.currentTimeMillis();
        byte[] reply = peerSocket.recv(0);
        final long waitEnd = System.currentTimeMillis();

        DataMessage replyMsg = null;
        try {
            replyMsg = DataMessage.parseFrom(reply);
        } catch (InvalidProtocolBufferException ipbe) {
            System.err.println("Caught protobuf exception: " + ipbe.getMessage());
            ipbe.printStackTrace(System.err);
            logger.error("Caught protobuf exception", ipbe);
        }

        logger.debug("Got reply message with uuid: {}, waited {} ms", replyMsg.getMessageUuid(), (waitEnd - waitStart));

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


    public void close() {
        try {
            peerSocket.close();
            logger.debug("Peer socket closed.");
            zmqContext.destroy();
            logger.debug("Zmq context closed.");
        } catch (Exception ex) {
            logger.error("Error closing zmq connection", ex);
        }
        producer.close();
        logger.debug("Producer closed.");
        consumer.close();
        logger.debug("Consumer closed.");
    }
}

