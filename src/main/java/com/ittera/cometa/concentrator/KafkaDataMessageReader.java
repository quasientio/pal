package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.IncomingMessageDispatcher;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * TODO Optimize - :sampling with visualvm shows this class as the one with highest memory allocation per thread.
 * We are reading everything from the log. Is it absolutely required? Can it be optional? If so, what to skip reading?
 */
@Singleton
public class KafkaDataMessageReader extends AbstractExecutionThreadService implements IncomingMessageDispatcher {

    protected static final Logger logger = LogManager.getLogger(KafkaDataMessageReader.class);

    private volatile boolean acceptingConnections = false;
    private volatile boolean connectionsOpen = false;

    // zmq stuff
    @Inject
    private ZContext zmqContext;
    private Socket kafkaPublisher;
    private final String inLogAddress;

    // counters
    private final AtomicLong totalPollingNanos = new AtomicLong(0);
    private final AtomicInteger totalPolls = new AtomicInteger(0);
    private final AtomicInteger messagesRcvd = new AtomicInteger(0);

    // kafka stuff
    private final long pollTimeout;
    private final String kafkaTopic;
    private KafkaConsumer<String, String> consumer;
    private final Properties consumerProperties = new Properties();


    @Inject
    public KafkaDataMessageReader(@Named("bootstrap.servers") String bootstrapServers,
                                  @Named("key.deserializer") String keyDeserializer,
                                  @Named("value.deserializer") String valueDeserializer,
                                  @Named("enable.auto.commit") String autoCommit,
                                  @Named("auto.commit.interval.ms") String autoCommitInterval,
                                  @Named("auto.offset.reset") String autoOffsetReset,
                                  @Named("session.timeout.ms") String sessionTimeout,
                                  @Named("id") String concentratorId,
                                  @Named("pollTimeout") String pollTimeout,
                                  @Named("kafkaTopic") String kafkaTopic,
                                  @Named("in.log") String inLogAddress) {
        this.kafkaTopic = kafkaTopic;
        this.inLogAddress = inLogAddress;
        this.pollTimeout = Long.parseLong(pollTimeout);
        //create Kafka consumer
        consumerProperties.put("group.id", concentratorId);
        consumerProperties.put("bootstrap.servers", bootstrapServers);
        consumerProperties.put("key.deserializer", keyDeserializer);
        consumerProperties.put("value.deserializer", valueDeserializer);
        consumerProperties.put("enable.auto.commit", autoCommit);
        consumerProperties.put("auto.commit.interval.ms", autoCommitInterval);
        consumerProperties.put("auto.offset.reset", autoOffsetReset);
        consumerProperties.put("session.timeout.ms", sessionTimeout);

        if (logger.isInfoEnabled()) {
            StringBuffer propsStr = new StringBuffer(50);
            for (String propKey : consumerProperties.stringPropertyNames()) {
                propsStr.append(propKey).append('=').append(consumerProperties.getProperty(propKey)).append(", ");
            }
            logger.info("Initialized kafka publisher for concentrator with id '{}', topic '{}' and properties: [{}]", concentratorId, kafkaTopic, propsStr.toString());
        }
    }

    protected void openConnections() {
        this.consumer = new KafkaConsumer<>(consumerProperties);
        //manual assignment of partition so we can control offset seek
        TopicPartition topicPartition = new TopicPartition(kafkaTopic, 0);
        final List<TopicPartition> topicPartitionList = Arrays.asList(topicPartition);
        consumer.assign(topicPartitionList);
        consumer.seekToBeginning(topicPartitionList);

        logger.info("Initialized kafka consumer and producer");

        this.kafkaPublisher = zmqContext.createSocket(ZMQ.PUB);
        kafkaPublisher.bind(inLogAddress);

        connectionsOpen = true;
        logger.info("All connections open");
    }

    @Override
    public final void run() {

        long iterations = 0;

        //wait for connections established
        while (!connectionsOpen) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                //what to do
            }
        }

        while (isRunning()) {
            if (!acceptingConnections) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Interrupted in sleep", e);
                } finally {
                    continue;
                }
            }


            //print stats every 10000 iterations
            if ((++iterations % 10000 == 0) && logger.isDebugEnabled()) {
                printDebugStats();
            }

            final ConsumerRecords<String, String> records;
            final long t0;

            // read from kafka
            t0 = System.nanoTime();
            records = consumer.poll(pollTimeout);
            totalPollingNanos.getAndAdd(System.nanoTime() - t0);
            totalPolls.getAndIncrement();

            if (logger.isDebugEnabled() && records.count() > 0) {
                logger.debug("Records read: {}", records.count());
            }

            // process records if any
            for (ConsumerRecord record : records) {
                messagesRcvd.getAndIncrement();

                if (logger.isDebugEnabled()) {
                    logger.debug("Processing received record # {} :\n {}", messagesRcvd, record);
                }

                final DataMessage dataMessage = (DataMessage) record.value();
                final long messageOffset = record.offset();

                // send request to PUB socket
                kafkaPublisher.send(String.valueOf(messageOffset), ZMQ.SNDMORE);
                kafkaPublisher.send(dataMessage.toByteArray(), 0);
                logger.debug("Published new log Data Message with uuid: {}", dataMessage.getMessageUuid());
            }

            // short pause, not to be eager
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                logger.error("Interrupted in sleep", e);
            }
        }
    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }

    @Override
    protected void shutDown() throws Exception {

        //print some statistics
        printDebugStats();

        //TODO: clean up, send uncommitted offset, etc.
        acceptingConnections = false;

        if (consumer != null) {
            consumer.close();
            logger.info("Closed kafka consumer");
        }
        logger.info("Message dispatcher shut down");
    }

    protected void printDebugStats() {
        logger.debug("--------STATS--------");
        logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
        logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
        logger.debug("# polls: {}", totalPolls.get());
        logger.debug("-----END OF STATS-----");
    }

    @Override
    public void acceptConnections(boolean acceptConnections) {
        this.acceptingConnections = acceptConnections;
    }
}
