package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.exec.ExecutionService;
import com.ittera.cometa.concentrator.messages.DataMessageDispatcher;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import java.util.Properties;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

@Singleton
public class KafkaDataMessageDispatcher extends AbstractExecutionThreadService implements DataMessageDispatcher {

  protected static final Logger logger = LogManager.getLogger(KafkaDataMessageDispatcher.class);

  private static final Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap = new ConcurrentHashMap<Long, BlockingQueue<DataMessage>>();
  private static final BlockingQueue<DataMessage> outgoingMessages = new LinkedBlockingQueue<>();

  private final ExecutionService executionService;
  private volatile boolean acceptingConnections = false;

  // counters
  private final AtomicLong totalReadBlockingQueueNanos = new AtomicLong(0);
  private final AtomicLong totalPollingNanos = new AtomicLong(0);
  private final AtomicInteger totalReadCalls = new AtomicInteger(0);
  private final AtomicInteger totalPolls = new AtomicInteger(0);
  private final AtomicInteger messagesQueuedToSend = new AtomicInteger(0);
  private final AtomicInteger messagesSent = new AtomicInteger(0);
  private final AtomicInteger messagesRcvd = new AtomicInteger(0);

  // kafka stuff
  private final long pollTimeout;
  private final String kafkaTopic;
  private KafkaConsumer<String, String> consumer;
  private KafkaProducer producer;
  private final Properties consumerProperties = new Properties();
  private final Properties producerProperties = new Properties();


  private class KafkaSenderThread extends Thread {
    public void run() {
      try {
        while (isRunning()) {
          sendToKafka(outgoingMessages.take());
        }
      } catch (InterruptedException ex) {
        logger.error("Interrupted while consuming outgoing messages", ex);
      }
    }

    void sendToKafka(DataMessage message) {
      ProducerRecord newRecord = new ProducerRecord(kafkaTopic, message);
      producer.send(newRecord);
      messagesSent.getAndIncrement();
      logger.debug("new message sent:\n {}", message);
    }
  }

  private static final ThreadLocal<BlockingQueue<DataMessage>> threadMessageQueue = new ThreadLocal<BlockingQueue<DataMessage>>() {
    @Override
    protected BlockingQueue<DataMessage> initialValue() {
     return new LinkedBlockingDeque<>();
    }

  };

  @Inject
  public KafkaDataMessageDispatcher(@Named("bootstrap.servers") String bootstrapServers,
                                    @Named("key.deserializer") String keyDeserializer,
                                    @Named("value.deserializer") String valueDeserializer,
                                    @Named("enable.auto.commit") String autoCommit,
                                    @Named("auto.commit.interval.ms") String autoCommitInterval,
                                    @Named("auto.offset.reset") String autoOffsetReset,
                                    @Named("session.timeout.ms") String sessionTimeout,
                                    @Named("key.serializer") String keySerializer,
                                    @Named("value.serializer") String valueSerializer,
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

    producerProperties.put("key.serializer", keySerializer);
    producerProperties.put("value.serializer", valueSerializer);
    producerProperties.put("bootstrap.servers", bootstrapServers);

    if (logger.isInfoEnabled()) {
      StringBuffer propsStr = new StringBuffer(50);
      Properties allProperties = new Properties();
      allProperties.putAll(consumerProperties);
      allProperties.putAll(producerProperties);
      for (String propKey : allProperties.stringPropertyNames()) {
        propsStr.append(propKey).append('=').append(allProperties.getProperty(propKey)).append(", ");
      }
      logger.info("Initialized dispatcher for concentrator with id '{}', topic '{}' and properties: [{}]", concentratorId, kafkaTopic, propsStr.toString());
    }
  }

  protected void openConnections() {
    this.producer = new KafkaProducer<>(producerProperties);

    this.consumer = new KafkaConsumer<>(consumerProperties);
    //manual assignment of partition so we can control offset seek
    final List<TopicPartition> topicPartitionList = Arrays.asList(new TopicPartition(kafkaTopic, 0));
    consumer.assign(topicPartitionList);
    consumer.seekToBeginning(topicPartitionList);

    logger.info("Initialized kafka consumer and producer");
  }

  @Override
  public final void run() {

    Thread senderThread = new KafkaSenderThread();
    senderThread.start();

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

      //print stats every 100 iterations
      if ((++iterations % 100 == 0) && logger.isDebugEnabled()) {
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
        //if threadId not in our threadQueue, then push to new/random thread
        if (!threadBlockingQueueMap.containsKey(threadId)) {
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
          try {
            //push to queue of the Destination thread
            threadBlockingQueueMap.get(threadId).put(dataMessage);
            logger.info("Pushed message with offset {} to thread queue", recordOffset);
          } catch (InterruptedException e) {
            logger.error("Interrupted while putting message in queue", e);
            //TODO: should/can we do something about it?
          }
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
    if (producer != null) {
      synchronized (producer) {
        producer.close();
        logger.info("Closed kafka producer");
      }
    }
    logger.info("Message dispatcher shut down");
  }

  protected void printDebugStats() {
    logger.debug("--------STATS--------");
    logger.debug("# of messages queued to send: {}", messagesQueuedToSend.get());
    logger.debug("# of messages sent to k-log: {}", messagesSent.get());
    logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
    logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
    logger.debug("# polls: {}", totalPolls.get());
    logger.debug("# queue reads: {}", totalReadCalls.get());
    logger.debug("Total waiting time reading from queue in nanoseconds: {}", totalReadBlockingQueueNanos.get());
    logger.debug("-----END OF STATS-----");
  }

  /**
   * @param message
   */
  @Override
  public void send(DataMessage message) {
    //ignore if service not running
    if (!isRunning()) {
      throw new IllegalStateException("Service not running");
    }

    //first check that the thread sending this message has a receiving queue
    createThreadQueueIfNeeded();

    boolean sent = false;
    do {
      try {
        outgoingMessages.put(message);
        messagesQueuedToSend.getAndIncrement();
        logger.debug("new message queued for sending:\n {}", message);
        sent = true;
      } catch (InterruptedException e) {
        logger.error("Interrupted putting message in outgoing queue. Will try again...", e);
      }
    } while (!sent);
  }

  private void createThreadQueueIfNeeded() {
    final long currThreadId = Thread.currentThread().getId();
    if (!threadBlockingQueueMap.containsKey(currThreadId)) {
      threadBlockingQueueMap.put(currThreadId, new LinkedBlockingDeque());
      logger.debug("Added new blocking queue to map, with thread id={}", currThreadId);
    }
  }

  @Override
  public DataMessage receiveMsgForCurrentThread() {
    //ignore if service not running
    if (!isRunning()) {
      throw new IllegalStateException("Service not running");
    }

    final long currThreadId = Thread.currentThread().getId();
    DataMessage rcvdMsg = null;
    do {
      try {
        final long t0 = System.nanoTime();
        rcvdMsg = (DataMessage) threadBlockingQueueMap.get(currThreadId).take();
        totalReadBlockingQueueNanos.getAndAdd(System.nanoTime() - t0);
        logger.debug("Taken new message from blocking queue (thread id={})", currThreadId);
      } catch (InterruptedException e) {
        logger.error("Interrupted while taking from blocking queue", e);
      } finally {
        totalReadCalls.getAndIncrement();
      }
    } while (rcvdMsg == null);

    return rcvdMsg;
  }

  @Override
  public void acceptConnections(boolean acceptConnections) {
    this.acceptingConnections = acceptConnections;
  }
}
