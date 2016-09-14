package com.ittera.cometa.distributor;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import com.ittera.cometa.distributor.messages.data.Wrappers.DataMessage;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MessageBroker {

  static KafkaProducer producer;
  static String kafkaTopic;

  protected static final Logger logger = LogManager.getLogger(MessageBroker.class);

  MessageBroker(Properties properties, String topic) {
    producer = new KafkaProducer<>(properties);
    this.kafkaTopic = topic;
  }

  static void send(DataMessage message) {
    //first check that the thread sending this message has a receiving queue
    checkCreateThreadQueue();

    producer.send(new ProducerRecord(kafkaTopic, message));
    logger.debug("new message sent!");
  }

  private static void checkCreateThreadQueue() {
    long currThreadId = Thread.currentThread().getId();
    if (!Distributor.threadBlockingQueueMap.containsKey(currThreadId)) {
      Distributor.threadBlockingQueueMap.put(currThreadId, new LinkedBlockingDeque());
      logger.debug("Added new blocking queue to map, with thread id={}", currThreadId);
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

  static void shutdown() {
    logger.info("Shutting down message broker");
    if (producer != null) {
      producer.close();
    }
    logger.info("Message broker shut down");
  }

}
