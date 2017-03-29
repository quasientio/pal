package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.data.Calls.ConstructorCall;
import com.ittera.cometa.concentrator.messages.protobuf.data.Calls.ClassMethodCall;
import com.ittera.cometa.concentrator.messages.protobuf.data.Calls.InstanceMethodCall;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields.StaticFieldGet;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields.StaticFieldPut;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields.InstanceFieldGet;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields.InstanceFieldPut;

import com.ittera.cometa.concentrator.messages.DataMessageDispatcher;

import java.util.Properties;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

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

  private final long pollTimeout;
  private final ExecutorService executorService;
  private final String kafkaTopic;
  private KafkaConsumer<String, String> consumer;
  private KafkaProducer producer;
  private volatile boolean acceptingConnections = false;
  private final Properties consumerProperties = new Properties();
  private final Properties producerProperties = new Properties();

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
                                    ExecutorService executorService,
                                    @Named("pollTimeout") String pollTimeout,
                                    @Named("kafkaTopic") String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
    this.pollTimeout = Long.parseLong(pollTimeout);
    this.executorService = executorService;
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

      final ConsumerRecords<String, String> records;
      synchronized (consumer) {
        records = consumer.poll(pollTimeout);
      }
      if (records.count() > 0) {
        logger.info("Records read: {}", records.count());
      }
      for (ConsumerRecord record : records) {
        if (logger.isDebugEnabled()) {
          logger.debug("Processing received record:\n {}", record);
        }

        final Wrappers.DataMessage dataMessage = (Wrappers.DataMessage) record.value();
        final long threadId = dataMessage.getThreadId();
        final long recordOffset = record.offset();
        //if threadId not in our threadQueue, then push to new/random thread
        if (!threadBlockingQueueMap.containsKey(threadId)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Thread queue has thread with ids: {}", threadBlockingQueueMap.keySet());
            logger.debug("No thread for incoming call, dispatching to thread pool...");
          }
          executorService.submit(new Runnable() {
            @Override
            public void run() {
              //TODO call Concentrator.incomingCall() which should dispatch as done here based on encapsulated type
              if (dataMessage.hasConstructorCall()) {
                final ConstructorCall constructorCall = dataMessage.getConstructorCall();
                Concentrator.incomingConstructor(constructorCall, recordOffset);
              } else if (dataMessage.hasClassMethodCall()) {
                final ClassMethodCall methodCall = dataMessage.getClassMethodCall();
                Concentrator.incomingClassMethod(methodCall, recordOffset);
              } else if (dataMessage.hasInstanceMethodCall()) {
                final InstanceMethodCall methodCall = dataMessage.getInstanceMethodCall();
                Concentrator.incomingInstanceMethod(methodCall, recordOffset);
              } else if (dataMessage.hasStaticFieldGet()) {
                final StaticFieldGet staticFieldGetCall = dataMessage.getStaticFieldGet();
                Concentrator.incomingGetStatic(staticFieldGetCall, recordOffset);
              } else if (dataMessage.hasInstanceFieldGet()) {
                final InstanceFieldGet instanceFieldGet = dataMessage.getInstanceFieldGet();
                Concentrator.incomingGetObject(instanceFieldGet, recordOffset);
              } else if (dataMessage.hasStaticFieldPut()) {
                final StaticFieldPut staticFieldPut = dataMessage.getStaticFieldPut();
                Concentrator.incomingPutStatic(staticFieldPut, recordOffset);
              } else if (dataMessage.hasInstanceFieldPut()) {
                final InstanceFieldPut instanceFieldPut = dataMessage.getInstanceFieldPut();
                Concentrator.incomingPutField(instanceFieldPut, recordOffset);
              } else {
                logger.warn("Incoming message with offset {} ignored - no handler:\n{}", recordOffset, dataMessage);
              }
            }
          });
        }

        //else, push to queue of this thread
        else {
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

  /**
   * TODO Should not send anything here, just queue it.
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

    ProducerRecord newRecord = new ProducerRecord(kafkaTopic, message);
    synchronized (producer) {
      producer.send(newRecord);
    }
    logger.debug("new message sent:\n {}", message);
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

    long currThreadId = Thread.currentThread().getId();
    DataMessage rcvdMsg = null;
    do {
      try {
        rcvdMsg = (DataMessage) threadBlockingQueueMap.get(currThreadId).take();
        logger.debug("Taken new message from blocking queue (thread id={})", currThreadId);
      } catch (InterruptedException e) {
        logger.error("Interrupted while taking from blocking queue", e);
      }
    } while (rcvdMsg == null);

    return rcvdMsg;
  }

  @Override
  public void acceptConnections(boolean acceptConnections) {
    this.acceptingConnections = acceptConnections;
  }
}
