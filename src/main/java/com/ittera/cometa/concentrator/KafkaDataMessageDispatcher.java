package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.exec.ExecutionService;
import com.ittera.cometa.concentrator.messages.IncomingMessageDispatcher;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import java.util.Properties;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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

@Singleton
public class KafkaDataMessageDispatcher extends AbstractExecutionThreadService implements IncomingMessageDispatcher {

    protected static final Logger logger = LogManager.getLogger(KafkaDataMessageDispatcher.class);

    private static final Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap = new ConcurrentHashMap<Long, BlockingQueue<DataMessage>>();

    private final ExecutionService executionService;
    private volatile boolean acceptingConnections = false;

    // counters
    private final AtomicLong totalPollingNanos = new AtomicLong(0);
    private final AtomicInteger totalReadCalls = new AtomicInteger(0);
    private final AtomicInteger totalPolls = new AtomicInteger(0);
    private final AtomicInteger messagesRcvd = new AtomicInteger(0);

    // kafka stuff
    private final long pollTimeout;
    private final String kafkaTopic;
    private KafkaConsumer<String, String> consumer;
    private final Properties consumerProperties = new Properties();

    @Inject
    public KafkaDataMessageDispatcher(@Named("bootstrap.servers") String bootstrapServers,
                                      @Named("key.deserializer") String keyDeserializer,
                                      @Named("value.deserializer") String valueDeserializer,
                                      @Named("enable.auto.commit") String autoCommit,
                                      @Named("auto.commit.interval.ms") String autoCommitInterval,
                                      @Named("auto.offset.reset") String autoOffsetReset,
                                      @Named("session.timeout.ms") String sessionTimeout,
                                      @Named("id") String concentratorId,
                                      ExecutionService executionService,
                                      @Named("pollTimeout") String pollTimeout,
                                      @Named("kafkaTopic") String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
        this.pollTimeout = Long.parseLong(pollTimeout);
        this.executionService = executionService;
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
            logger.info("Initialized dispatcher for concentrator with id '{}', topic '{}' and properties: [{}]", concentratorId, kafkaTopic, propsStr.toString());
        }
    }

    protected void openConnections() {
        this.consumer = new KafkaConsumer<>(consumerProperties);
        //manual assignment of partition so we can control offset seek
        final List<TopicPartition> topicPartitionList = Arrays.asList(new TopicPartition(kafkaTopic, 0));
        consumer.assign(topicPartitionList);
        consumer.seekToBeginning(topicPartitionList);

        logger.info("Initialized kafka consumer and producer");
    }

    @Override
    public final void run() {

        long iterations = 0;

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
            synchronized (consumer) {
                t0 = System.nanoTime();
                records = consumer.poll(pollTimeout);
                totalPollingNanos.getAndAdd(System.nanoTime() - t0);
                totalPolls.getAndIncrement();
            }
            if (records.count() > 0) {
                logger.info("Records read: {}", records.count());
            }
            for (ConsumerRecord record : records) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing received record:\n {}", record);
                }

                messagesRcvd.getAndIncrement();
                final DataMessage dataMessage = (DataMessage) record.value();
                final long threadId = dataMessage.getThreadId();
                final long recordOffset = record.offset();

                // we dispatch only if concentrator uuid isn't ours
                if (!Concentrator.uuid.toString().equals(dataMessage.getConcentratorUuid())) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Thread queue has thread with ids: {}", threadBlockingQueueMap.keySet());
                        logger.debug("No thread for incoming call, dispatching to thread pool...");
                    }
                    executionService.submit(new Runnable() {
                        @Override
                        public void run() {
                            Concentrator.incomingCall(dataMessage, recordOffset);
                        }
                    });
                } else {
                    //TODO: should/can we do
                    logger.debug("Discarding kafka message originated in self, with uuid: {}", dataMessage.getConcentratorUuid());
                }
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
            synchronized (consumer) {
                consumer.close();
                logger.info("Closed kafka consumer");
            }
        }
        logger.info("Message dispatcher shut down");
    }

    protected void printDebugStats() {
        logger.debug("--------STATS--------");
        logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
        logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
        logger.debug("# polls: {}", totalPolls.get());
        logger.debug("# queue reads: {}", totalReadCalls.get());
        logger.debug("-----END OF STATS-----");
    }

    @Override
    public void acceptConnections(boolean acceptConnections) {
        this.acceptingConnections = acceptConnections;
    }
}
