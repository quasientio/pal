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

import java.util.Properties;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Future;

import java.io.IOException;
import java.io.InputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.*;

public abstract class AbstractConcentratorTest {

    protected final static Logger logger = LogManager.getLogger("tests");

    protected final static UUID clientId = UUID.randomUUID();
    private static String kafkaTopic;

    protected static DataMessageBuilder dataMessageBuilder;

    //producer
    private static final Properties kafkaProducerProps = new Properties();
    private static KafkaProducer producer;

    //consumer
    private static Long pollTimeout;
    private static KafkaConsumer<String, String> consumer;
    private static Properties kafkaConsumerProps = new Properties();
    private static TopicPartition topicPartition;

    @BeforeClass
    public static void initialize() throws IOException {
        //load properties
        final Properties properties = new Properties();
        try (final InputStream stream = AbstractConcentratorTest.class.getResourceAsStream("/tests.properties")) {
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

        kafkaProducerProps.put("client.id", String.valueOf(clientId));
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
        //consumer.subscribe(Arrays.asList(Concentrator.kafkaTopic));

        //manual assignment of partition so we can control offset seek
        topicPartition = new TopicPartition(kafkaTopic, 0);
        consumer.assign(Arrays.asList(topicPartition));
//    consumer.seekToBeginning(Arrays.asList(topicPartition));

        //init msg builder
        dataMessageBuilder = new ProtobufDataMessageBuilder();

    }

    protected static DataMessage sendAndReceive(DataMessage message) {
        //send
        Long sentRecordOffset = send(message);

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
                    return dataMessage;
                } else {
                    logger.debug("Skipping record with offset {}", receivedMsgOffset);
                }
            }
        }
    }

    protected static Long send(DataMessage message) {
        Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
        try {
            RecordMetadata recordMetadata = recordMetadataFuture.get();
            if (logger.isDebugEnabled()) {
                logger.debug("Message sent:\n{}", getRecordInfo(recordMetadata));
            }
            return recordMetadata.offset();
        } catch (Exception e) {
            logger.error("Error getting sent record metadata", e);
            return null;
        }
    }

    private static String getRecordInfo(RecordMetadata recordMetadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n checksum: ").append(recordMetadata.checksum()).append('\n').append(
                " timestamp: ").append(recordMetadata.timestamp()).append('\n').append(
                " offset: ").append(recordMetadata.offset()).append('\n').append(
                " #bytes in value: ").append(recordMetadata.serializedValueSize()).append("\n}");

        return builder.toString();
    }

    /**
     * Helper assertion methods
     * This method is also useful as it encapsulates details of the protobuf serialization
     *
     * @param returnValue
     * @param className
     * @return
     */
    private void isObjectOfRightType(ReturnValue returnValue, String className, boolean isObjRef, boolean isNull, boolean isArray) {
        assertFalse(returnValue.getIsVoid());
        assertFalse(returnValue.getIsClass());
        assertTrue(returnValue.hasClazz());
        assertEquals(className, returnValue.getClazz().getName());
        assertTrue(returnValue.hasObject());

        Primitives.Object retObj = returnValue.getObject();
        assertEquals(isArray, retObj.getIsArray());
        assertEquals(isNull, retObj.getIsNull());
        assertEquals(isObjRef, retObj.hasRef());
        assertTrue(retObj.hasClass_());
        assertFalse(retObj.getClass_().getUnknown());
        assertEquals(className, retObj.getClass_().getName());

    }

    protected void assertValueIsObjectOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, false, false);
    }

    protected void assertValueIsObjectRefOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, true, false, false);
    }

    protected void assertValueIsWrappedArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, false, true);
    }

    protected void assertValueIsArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, true, false, true);
    }

    protected void assertValueIsNullObjectOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, true, false);
    }

    protected void assertValueIsNullArrayOfRightType(ReturnValue returnValue, String className) {
        isObjectOfRightType(returnValue, className, false, true, true);
    }

    @AfterClass
    public static void finalizeStuff() {
        logger.debug("Finalizing after tests...");
        producer.close();
        logger.debug("Producer closed.");
        consumer.close();
        logger.debug("Consumer closed.");
    }
}

