package com.ittera.cometa.distributor;

import java.util.Properties;
import java.util.Arrays;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.distributor.messages.data.Wrappers;

public class DataMessageDispatcher extends Thread {

    protected static final Logger logger = LogManager.getLogger("distributor");

    private static DataMessageDispatcher ourInstance = new DataMessageDispatcher();

    private KafkaConsumer<String, String> consumer;

    public static DataMessageDispatcher getInstance() {
        return ourInstance;
    }

    private DataMessageDispatcher() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", String.valueOf(Distributor.id));
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "500");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        //props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "com.ittera.cometa.distributor.messages.ProtobufDeserializer");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(Distributor.kafkaTopic));
        setDaemon(true);
        logger.info("DataMessageDispatcher initialized");
    }

    public void run() {
      while (true) {
          ConsumerRecords<String, String> records = consumer.poll(10);
          logger.info("Records read:"+records.count());
          for (ConsumerRecord record: records) {
              if (logger.isDebugEnabled()) {
                  logger.debug("Processing received record:\n"+record);
              }
              Wrappers.DataMessage dataMessage = (Wrappers.DataMessage) record.value();
              long threadId=dataMessage.getThreadId();
              try {
                  //push to queue of the Destination thread
                Distributor.threadBlockingQueueMap.get(threadId).put(dataMessage);
                logger.info("Pushed message to thread queue");
               } catch (InterruptedException e) {
                logger.error("Interrupted while putting message in queue");
                //TODO: should we do something about it?
              }
          }
      }
    }
}
