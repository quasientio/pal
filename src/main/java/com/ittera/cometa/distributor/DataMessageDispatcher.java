package com.ittera.cometa.distributor;

import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.ittera.cometa.distributor.messages.data.Wrappers;
import com.ittera.cometa.distributor.messages.data.Calls;
import com.ittera.cometa.distributor.messages.data.Fields;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class DataMessageDispatcher extends Thread {

  protected static final Logger logger = LogManager.getLogger(DataMessageDispatcher.class);

  private static long pollTimeout;

  private static ExecutorService executorService;

  private static DataMessageDispatcher ourInstance;

  private KafkaConsumer<String, String> consumer;

  private boolean mustShutdown;

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
    pollTimeout = Long.parseLong((String) props.remove("pollTimeout"));
    props.put("group.id", String.valueOf(Distributor.id));
    consumer = new KafkaConsumer<>(props);
    //consumer.subscribe(Arrays.asList(Distributor.kafkaTopic));

    //manual assignment of partition so we can control offset seek
    TopicPartition topicPartition = new TopicPartition(MessageBroker.kafkaTopic, 0);
    consumer.assign(Arrays.asList(topicPartition));
    consumer.seekToBeginning(Arrays.asList(topicPartition));
    logger.info("DataMessageDispatcher initialized");
    executorService = Executors.newCachedThreadPool();
  }

  public void run() {
    while (!mustShutdown) {
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
        final long recordOffset = record.offset();
        //if threadId not in our threadQueue, then push to new/random thread
        if (!Distributor.threadBlockingQueueMap.containsKey(threadId)) {
          logger.debug("Thread queue has thread with ids" + Distributor.threadBlockingQueueMap.keySet());
          logger.debug("No thread for incoming call, creating new one and dispatching...");
          executorService.submit(new Runnable() {
            @Override
            public void run() {
              //TODO call Distributor.incomingCall() which should dispatch as done here based on encapsulated type
              if (dataMessage.hasConstructorCall()) {
                Calls.ConstructorCall constructorCall = dataMessage.getConstructorCall();
                Distributor.incomingConstructor(constructorCall, recordOffset);
              } else if (dataMessage.hasClassMethodCall()) {
                Calls.ClassMethodCall methodCall = dataMessage.getClassMethodCall();
                Distributor.incomingClassMethod(methodCall, recordOffset);
              } else if (dataMessage.hasInstanceMethodCall()) {
                Calls.InstanceMethodCall methodCall = dataMessage.getInstanceMethodCall();
                Distributor.incomingInstanceMethod(methodCall, recordOffset);
              } else if (dataMessage.hasStaticFieldGet()) {
                Fields.StaticFieldGet staticFieldGetCall = dataMessage.getStaticFieldGet();
                Distributor.incomingGetStatic(staticFieldGetCall, recordOffset);
              } else if (dataMessage.hasInstanceFieldGet()) {
                Fields.InstanceFieldGet instanceFieldGet = dataMessage.getInstanceFieldGet();
                Distributor.incomingGetObject(instanceFieldGet, recordOffset);
              } else if (dataMessage.hasStaticFieldPut()) {
                Fields.StaticFieldPut staticFieldPut = dataMessage.getStaticFieldPut();
                Distributor.incomingPutStatic(staticFieldPut, recordOffset);
              } else if (dataMessage.hasInstanceFieldPut()) {
                Fields.InstanceFieldPut instanceFieldPut = dataMessage.getInstanceFieldPut();
                Distributor.incomingPutField(instanceFieldPut, recordOffset);
              } else {
                logger.warn("Incoming message ignored - no handler:\n" + dataMessage);
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

    shutdown();
  }

  void requestShutdown() {
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
