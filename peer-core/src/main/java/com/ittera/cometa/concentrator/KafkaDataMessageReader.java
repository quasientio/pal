package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.ittera.cometa.concentrator.messages.IncomingMessageDispatcher;

import java.util.Properties;
import java.util.Arrays;
import java.util.List;
import java.util.AbstractQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * TODO Optimize - :sampling with visualvm shows this class as the one with highest memory allocation per thread.
 * We are reading everything from the log. Is it absolutely required? Can it be optional? If so, what to skip reading?
 */
@Singleton
public class KafkaDataMessageReader extends AbstractExecutionThreadService implements IncomingMessageDispatcher {

    protected static final Logger logger = LoggerFactory.getLogger(KafkaDataMessageReader.class);

    private volatile boolean acceptingConnections = false;
    private volatile boolean connectionsOpen = false;

    // zmq stuff
    @Inject
    private ZContext zmqContext;
    private Socket logDealer;
    private Socket offsetSubscriber;
    private final String inLogAddress, offsetPubAddress;

    // counters
    private final AtomicLong totalPollingNanos = new AtomicLong(0);
    private final AtomicInteger totalPolls = new AtomicInteger(0);
    private final AtomicInteger messagesRcvd = new AtomicInteger(0);

    // kafka stuff
    private final long pollTimeout;
    private String kafkaTopic;
    private TopicPartition topicPartition;
    private KafkaConsumer<String, String> consumer;
    private final Properties consumerProperties = new Properties();
    private volatile long lastOffsetRead = -1;
    private LogInfo currentLog;

    // zookeeper
    @Inject
    private PeerLogDirectory peerLogDirectory;

    // shared by threads OffsetUpdater and KafkaDataMessageReader: TODO avoid sharing
    final private AbstractQueue<Long> skipOffsets = new ConcurrentLinkedQueue<>();

    private final class OffsetUpdater extends Thread {

        private Socket offsetSubscriber;

        OffsetUpdater(Socket offsetSubscriber) {
            super("Offset informer");
            this.offsetSubscriber = offsetSubscriber;
        }

        @Override
        public void run() {
            logger.debug("Offset informer running");

            String rcvd;
            boolean breakOut = false;

            while (!Thread.interrupted() && !breakOut) {
                rcvd = null;
                try {
                    rcvd = offsetSubscriber.recvStr();
                } catch (ZMQException ex) {
                  int errorCode = ex.getErrorCode();
                  if (errorCode == ZError.ETERM) {
                      logger.debug("Caught ETERM during blocking read. Breaking out.");
                      break;
                  } else if (errorCode == ZError.EINTR) {
                      logger.debug("Caught EINTR during blocking read. Breaking out.");
                      break;
                  } else {
                      throw ex;
                  }
                }

                while (rcvd != null) {
                    long offset = Long.valueOf(rcvd);
                    rcvd = null;
                    skipOffsets.add(offset);
                    try {
                        rcvd = offsetSubscriber.recvStr();
                    } catch (ZMQException ex) {
                      int errorCode = ex.getErrorCode();
                      if (errorCode == ZError.ETERM) {
                          logger.debug("Caught ETERM during blocking read. Breaking out.");
                          breakOut = true;
                          break;
                      } else if (errorCode == ZError.EINTR) {
                          logger.debug("Caught EINTR during blocking read. Breaking out.");
                          break;
                      } else {
                          throw ex;
                      }
                    }
                }

                // short pause, not to be eager
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    logger.error("Interrupted in sleep", e);
                }
            }
        }
    }

    @Inject
    public KafkaDataMessageReader(@Named("key.deserializer") String keyDeserializer,
                                  @Named("value.deserializer") String valueDeserializer,
                                  @Named("enable.auto.commit") String autoCommit,
                                  @Named("auto.commit.interval.ms") String autoCommitInterval,
                                  @Named("auto.offset.reset") String autoOffsetReset,
                                  @Named("session.timeout.ms") String sessionTimeout,
                                  @Named("id") String concentratorId,
                                  @Named("pollTimeout") String pollTimeout,
                                  @Named("in.log") String inLogAddress,
                                  @Named("offset.pub") String offsetPubAddress) {
        this.inLogAddress = inLogAddress;
        this.offsetPubAddress = offsetPubAddress;
        this.pollTimeout = Long.parseLong(pollTimeout);
        // prepare Kafka consumer
        consumerProperties.put("group.id", concentratorId);
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
            logger.info("Initialized kafka publisher for concentrator with id '{}' and properties: [{}]",
              concentratorId, propsStr.toString());
        }
    }

    @Override
    public void readFromLastLog(String logNamePrefix) throws Exception {

        LogInfo lastLog = peerLogDirectory.getLastLog(logNamePrefix);
        readFromLog(lastLog.getName());
    }

    @Override
    public void readFromLog(String logName) throws Exception {

        this.kafkaTopic = logName;
        LogInfo logInfo = peerLogDirectory.getLogInfo(logName);
        this.currentLog = logInfo;
        consumerProperties.put("bootstrap.servers", logInfo.getBootstrapServers());
        logger.info("Now reading from log: {} and bootstrapServers: {}", logInfo.getName(),
          logInfo.getBootstrapServers());
    }

    protected void openConnections() {
        this.consumer = new KafkaConsumer<>(consumerProperties);
        //manual assignment of partition so we can control offset seek
        topicPartition = new TopicPartition(kafkaTopic, 0);
        final List<TopicPartition> topicPartitionList = Arrays.asList(topicPartition);
        consumer.assign(topicPartitionList);
        consumer.seekToBeginning(topicPartitionList);

        logger.info("Initialized kafka consumer");

        this.logDealer = zmqContext.createSocket(ZMQ.DEALER);
        logDealer.bind(inLogAddress);

        // subscriber to get the offsets written by the message writer
        this.offsetSubscriber = zmqContext.createSocket(ZMQ.SUB);
        offsetSubscriber.connect(offsetPubAddress);
        offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        logger.info("Initialized zmq sockets");

        new OffsetUpdater(offsetSubscriber).start();
        logger.info("Initialized offset notifier thread");

        connectionsOpen = true;
        logger.info("All connections open");
    }

    protected void closeConnections() {

        if (consumer != null) {
            consumer.close();
            logger.info("Closed kafka consumer");
        }

        if (logDealer != null) {
            logDealer.close();
        }

        if (offsetSubscriber != null) {
            offsetSubscriber.close();
        }

        logger.info("All connections closed");
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

        ConsumerRecords<String, String> records;
        long t0;

        while (isRunning() && !Thread.interrupted()) {

            if (!acceptingConnections) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }

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
                    logger.debug("Processing received record # {} with offset {} :\n {}", messagesRcvd, record.offset(),
                      record);
                }

                final DataMessage dataMessage = (DataMessage) record.value();
                final long messageOffset = record.offset();
                lastOffsetRead = messageOffset;

                // send request to DEALER socket
                logDealer.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
                logDealer.send(String.valueOf(messageOffset), ZMQ.SNDMORE);
                logDealer.send(dataMessage.toByteArray(), 0);
                logger.debug("Dealt new log Data Message with uuid: {}", dataMessage.getMessageUuid());

                // get next offset to poll
                Long nextOffset = nextOffset();
                if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
                    logger.debug("Skipping received records. Jumping from offset: {} to: {}", lastOffsetRead,
                      nextOffset);
                    consumer.seek(topicPartition, nextOffset);
                    break;
                }
            }

            // short pause, not to be eager
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
              break;
            }

            // get next offset to poll
            Long nextOffset = nextOffset();
            if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
                logger.debug("Jumping from offset: {} to: {}", lastOffsetRead, nextOffset);
                consumer.seek(topicPartition, nextOffset);
            }
        }

        closeConnections();
    }

    private Long nextOffset() {
        if (logger.isTraceEnabled()) {
            final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
            logger.trace("in w/ lastOffsetRead = {}, and queue: {}", lastOffsetRead, queueStr);
        }

        // initial candidate == last read + 1
        Long nextToRead = lastOffsetRead + 1;

        Long nextOffsetToSkip = skipOffsets.peek();

        // clean up all possible offsets up to and including last read
        while ((nextOffsetToSkip != null) && (nextOffsetToSkip < nextToRead)) {
            skipOffsets.poll();
            nextOffsetToSkip = skipOffsets.peek();
        }

        // while queue not empty, pop next offsets in sequence
        while (nextToRead.equals(nextOffsetToSkip)) {
            skipOffsets.poll();
            nextToRead++;
            nextOffsetToSkip = skipOffsets.peek();
        }

        if (logger.isTraceEnabled()) {
            final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
            logger.trace("out w/ nextToRead = {} with lastOffsetRead = {}, and final queue: {}",
                    nextToRead, lastOffsetRead, queueStr);
        }
        return nextToRead;
    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }

    @Override
    protected void triggerShutdown() {

        logger.info("Data message reader shutting down.");

        //TODO: clean up, send uncommitted offset, etc.
        acceptingConnections = false;
    }

    @Override
    protected void shutDown() {

        logger.info("Data message reader shut down.");
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
