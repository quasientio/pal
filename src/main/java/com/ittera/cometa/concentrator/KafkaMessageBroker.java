package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * TODO: This class should NOT depend on protobuf
 */
@Singleton
public final class KafkaMessageBroker implements MessageBroker {

  private final Properties properties = new Properties();
  private final KafkaProducer producer;
  private final String kafkaTopic;
  private final Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap;

  protected static final Logger logger = LogManager.getLogger(KafkaMessageBroker.class);

  @Inject
  public KafkaMessageBroker(@Named("bootstrap.servers") String bootstrapServers,
                            @Named("key.serializer") String keySerializer,
                            @Named("value.serializer") String valueSerializer,
                            Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap,
                            @Named("kafkaTopic") String topic) {
    this.kafkaTopic = topic;
    this.threadBlockingQueueMap = threadBlockingQueueMap;
    //create Kafka producer
    properties.put("bootstrap.servers", bootstrapServers);
    properties.put("key.serializer", keySerializer);
    properties.put("value.serializer", valueSerializer);
    this.producer = new KafkaProducer<>(properties);
    if (logger.isInfoEnabled()) {
      StringBuffer propsStr = new StringBuffer(50);
      for (String propKey : properties.stringPropertyNames()) {
        propsStr.append(propKey).append('=').append(properties.getProperty(propKey)).append(", ");
      }
      logger.info("Initialized message broker, with topic '{}' and properties: [{}]", kafkaTopic, propsStr.toString());
    }
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


  public void shutdown() {
    logger.info("Shutting down message broker");
    if (producer != null) {
      producer.close();
    }
    logger.info("Message broker shut down");
  }

}
