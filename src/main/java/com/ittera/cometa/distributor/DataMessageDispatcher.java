package com.ittera.cometa.distributor;

import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.ittera.cometa.distributor.messages.data.Calls;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.CollectionUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.distributor.messages.data.Wrappers;

public class DataMessageDispatcher extends Thread {

  protected static final Logger logger = LogManager.getLogger(DataMessageDispatcher.class);

  private static long pollTimeout;

  private static ExecutorService executorService;

  private static DataMessageDispatcher ourInstance;

  private KafkaConsumer<String, String> consumer;

  //to be called once initialized
  public static DataMessageDispatcher getInstance() {
    if (ourInstance == null) {
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
    pollTimeout = Long.parseLong((String)props.remove("pollTimeout"));
    props.put("group.id", String.valueOf(Distributor.id));
    consumer = new KafkaConsumer<>(props);
    //consumer.subscribe(Arrays.asList(Distributor.kafkaTopic));

    //manual assignment of partition so we can control offset seek
    TopicPartition topicPartition = new TopicPartition(Distributor.kafkaTopic,0);
    consumer.assign(Arrays.asList(topicPartition));
    consumer.seekToBeginning(Arrays.asList(topicPartition));
    logger.info("DataMessageDispatcher initialized");
    executorService = Executors.newCachedThreadPool();
  }

  public void run() {
    while (true) {
      ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
      if (records.count() > 0) {
        logger.info("Records read:" + records.count());
      }
      for (ConsumerRecord record : records) {
        if (logger.isDebugEnabled()) {
          logger.debug("Processing received record:\n" + record);
        }
        final Wrappers.DataMessage dataMessage = (Wrappers.DataMessage) record.value();
        long threadId = dataMessage.getThreadId();
        //if threadId not in our threadQueue, then push to new/random thread
        if (!Distributor.threadBlockingQueueMap.containsKey(threadId)) {
          logger.debug("Thread queue has thread with ids"+Distributor.threadBlockingQueueMap.keySet());
          logger.debug("No thread for incoming call, creating new one and dispatching...");
          executorService.submit(new Runnable() {
            @Override
            public void run() {
              //TODO call Distributor.incomingCall() which should dispatch as done here based on encapsulated type
              if (dataMessage.hasConstructorCall()) {
                Calls.ConstructorCall constructorCall = dataMessage.getConstructorCall();
                Distributor.incomingConstructor(constructorCall);
              } else if (dataMessage.hasClassMethodCall()) {
                Calls.ClassMethodCall methodCall = dataMessage.getClassMethodCall();
                Distributor.incomingClassMethod(methodCall);
              } else if (dataMessage.hasInstanceMethodCall()) {
                Calls.InstanceMethodCall methodCall = dataMessage.getInstanceMethodCall();
                Distributor.incomingInstanceMethod(methodCall);
              } else {
                //TODO : field op calls
                logger.debug("Incoming message ignored:\n"+dataMessage);
              }

            }
          });
        }

        //else, push to queue of this thread
        else {
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
}
