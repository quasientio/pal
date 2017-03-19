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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.BlockingQueue;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.inject.name.Named;
import com.google.inject.Inject;

public class KafkaDataMessageDispatcher extends Thread implements DataMessageDispatcher {

  protected static final Logger logger = LogManager.getLogger(KafkaDataMessageDispatcher.class);

  private long pollTimeout;
  private static ExecutorService executorService;
  private KafkaConsumer<String, String> consumer;
  private volatile boolean mustShutdown;

  private Properties properties = new Properties();
  private volatile Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap;
  private String kafkaTopic;

  @Inject
  public KafkaDataMessageDispatcher(@Named("bootstrap.servers") String bootstrapServers,
                                    @Named("key.deserializer") String keyDeserializer,
                                    @Named("value.deserializer") String valueDeserializer,
                                    @Named("enable.auto.commit") String autoCommit,
                                    @Named("auto.commit.interval.ms") String autoCommitInterval,
                                    @Named("auto.offset.reset") String autoOffsetReset,
                                    @Named("session.timeout.ms") String sessionTimeout,
                                    @Named("id") String concentratorId,
                                    Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap,
                                    ExecutorService executorService,
                                    @Named("pollTimeout") String pollTimeout,
                                    @Named("kafkaTopic") String kafkaTopic) {
    this.threadBlockingQueueMap = threadBlockingQueueMap;
    this.kafkaTopic = kafkaTopic;
    this.pollTimeout = Long.parseLong(pollTimeout);
    this.executorService = executorService;
    //create Kafka consumer
    properties.put("group.id", concentratorId);
    properties.put("bootstrap.servers", bootstrapServers);
    properties.put("key.deserializer", keyDeserializer);
    properties.put("value.deserializer", valueDeserializer);
    properties.put("enable.auto.commit", autoCommit);
    properties.put("auto.commit.interval.ms", autoCommitInterval);
    properties.put("auto.offset.reset", autoOffsetReset);
    properties.put("session.timeout.ms", sessionTimeout);
    consumer = new KafkaConsumer<>(properties);

    //manual assignment of partition so we can control offset seek
    final TopicPartition topicPartition = new TopicPartition(kafkaTopic, 0);
    consumer.assign(Arrays.asList(topicPartition));
    consumer.seekToBeginning(Arrays.asList(topicPartition));
    logger.info("Initialized dispatcher, with topic '{}' and properties: {}", kafkaTopic, properties.stringPropertyNames());
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
    logger.info("Message dispatcher shut down");
  }

}
