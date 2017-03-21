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

@Singleton
public class KafkaDataMessageDispatcher implements DataMessageDispatcher {

  protected static final Logger logger = LogManager.getLogger(KafkaDataMessageDispatcher.class);

  private static final Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap = new ConcurrentHashMap<Long, BlockingQueue<DataMessage>>();

  private final long pollTimeout;
  private final ExecutorService executorService;
  private final String kafkaTopic;
  private final KafkaConsumer<String, String> consumer;
  private final KafkaProducer producer;
  private volatile boolean mustShutdown = false;
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
    this.consumer = new KafkaConsumer<>(consumerProperties);

    producerProperties.put("key.serializer", keySerializer);
    producerProperties.put("value.serializer", valueSerializer);
    producerProperties.put("bootstrap.servers", bootstrapServers);
    this.producer = new KafkaProducer<>(producerProperties);

    //manual assignment of partition so we can control offset seek
    final List<TopicPartition> topicPartitionList = Arrays.asList(new TopicPartition(kafkaTopic, 0));
    consumer.assign(topicPartitionList);
    consumer.seekToBeginning(topicPartitionList);
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

  public final void run() {
    while (!mustShutdown) {
      final ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
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
          logger.debug("Thread queue has thread with ids" + threadBlockingQueueMap.keySet());
          logger.debug("No thread for incoming call, dispatching to thread pool...");
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

    shutdown();
  }

  public void requestShutdown() {
    mustShutdown = true;
  }

  private void shutdown() {
    //TODO: clean up, send uncommitted offset, etc.

    logger.info("Shutting down message dispatcher");
    if (consumer != null) {
      consumer.close();
    }
    if (producer != null) {
      producer.close();
    }
    logger.info("Message dispatcher shut down");
  }

  public void send(DataMessage message) {
    //first check that the thread sending this message has a receiving queue
    createThreadQueueIfNeeded();

    producer.send(new ProducerRecord(kafkaTopic, message));
    logger.debug("new message sent:\n {}", message);
  }

  private void createThreadQueueIfNeeded() {
    final long currThreadId = Thread.currentThread().getId();
    if (!threadBlockingQueueMap.containsKey(currThreadId)) {
      threadBlockingQueueMap.put(currThreadId, new LinkedBlockingDeque());
      logger.debug("Added new blocking queue to map, with thread id={}", currThreadId);
    }
  }

  public DataMessage receiveMsgForCurrentThread() {
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
}
