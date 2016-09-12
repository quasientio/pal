package com.ittera.cometa.distributor;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.Future;

import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;

import com.ittera.cometa.distributor.messages.data.Wrappers.DataMessage;

import org.apache.kafka.clients.producer.RecordMetadata;

public abstract class AbstractDistributorTest {

  protected final static Logger logger = LogManager.getLogger("tests");

  protected final String clientId;
  private final String kafkaTopic;
  private final Properties kafkaProducerProps = new Properties();
  final KafkaProducer producer;

  AbstractDistributorTest() throws IOException {
    //load properties
    final Properties properties = new Properties();
    try (final InputStream stream = this.getClass().getResourceAsStream("/tests.properties")) {
      properties.load(stream);
    }

    clientId = properties.getProperty("id");
    kafkaTopic = properties.getProperty("kafkaTopic");

    /** Configure and Initialize Kafka Producer **/
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
      } else if (propKey.startsWith("kafka.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }

    kafkaProducerProps.put("client.id", String.valueOf(clientId));
    producer = new KafkaProducer<>(kafkaProducerProps);
    logger.debug("Kafka producer initialized: {}", producer);
  }


  protected Long send(DataMessage message) {
    Future<RecordMetadata> recordMetadataFuture = producer.send(new ProducerRecord(kafkaTopic, message));
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      logger.debug("Message sent:{}", recordMetadata.toString());
      return recordMetadata.offset();
    } catch (Exception e) {
      logger.error("Error getting sent record metadata");
      return null;
    }
  }
}
