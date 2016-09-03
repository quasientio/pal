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

    protected static final Logger logger = LogManager.getLogger(DataMessageDispatcher.class);

    private static long pollTimeout;

    private static DataMessageDispatcher ourInstance;

    private KafkaConsumer<String, String> consumer;

    //to be called once initialized
    public static DataMessageDispatcher getInstance() {
        if (ourInstance==null) {
            throw new IllegalStateException("DataMessageDispatcher has not been initialized from properties");
        }
        return ourInstance;
    }

    //singleton accessor for initial construction
    public static DataMessageDispatcher getInstance(Properties properties) {
        ourInstance = new DataMessageDispatcher(properties);
        return ourInstance;
    }

    private DataMessageDispatcher(Properties props) {
        pollTimeout = Long.valueOf((String)props.remove("pollTimeout"));
        props.put("group.id", String.valueOf(Distributor.id));
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(Distributor.kafkaTopic));
        logger.info("DataMessageDispatcher initialized");
    }

    public void run() {
      while (true) {
          ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
          if (records.count()>0) {
            logger.info("Records read:" + records.count());
          }
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
