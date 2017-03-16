package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingDeque;

public final class MessageBroker {

  static KafkaProducer producer;
  static String kafkaTopic;

  protected static final Logger logger = LogManager.getLogger(MessageBroker.class);

  static void init(Properties properties, String topic) {
    producer = new KafkaProducer<>(properties);
    kafkaTopic = topic;
  }

  static void send(DataMessage message) {
    if (!isInitialized()) {
      throw new IllegalStateException("MessageBroker has not been initialized. Please call init() first.");
    }
    //first check that the thread sending this message has a receiving queue
    createThreadQueueIfNeeded();

    producer.send(new ProducerRecord(kafkaTopic, message));
    logger.debug("new message sent:\n {}", message);
  }

  private static void createThreadQueueIfNeeded() {
    final long currThreadId = Thread.currentThread().getId();
    if (!Concentrator.threadBlockingQueueMap.containsKey(currThreadId)) {
      Concentrator.threadBlockingQueueMap.put(currThreadId, new LinkedBlockingDeque());
      logger.debug("Added new blocking queue to map, with thread id={}", currThreadId);
    }
  }

  static void shutdown() {
    if (!isInitialized()) {
      return;
    }
    logger.info("Shutting down message broker");
    if (producer != null) {
      producer.close();
    }
    logger.info("Message broker shut down");
  }

  private static boolean isInitialized() {
    return (producer != null) && (kafkaTopic != null);
  }
}
