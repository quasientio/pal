package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.data.Wrappers;
import com.ittera.cometa.concentrator.messages.data.Calls.ConstructorCall;
import com.ittera.cometa.concentrator.messages.data.Calls.ClassMethodCall;
import com.ittera.cometa.concentrator.messages.data.Calls.InstanceMethodCall;
import com.ittera.cometa.concentrator.messages.data.Fields.StaticFieldGet;
import com.ittera.cometa.concentrator.messages.data.Fields.StaticFieldPut;
import com.ittera.cometa.concentrator.messages.data.Fields.InstanceFieldGet;
import com.ittera.cometa.concentrator.messages.data.Fields.InstanceFieldPut;

import java.util.Properties;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

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
  private static DataMessageDispatcher INSTANCE;
  private KafkaConsumer<String, String> consumer;
  private volatile boolean mustShutdown;
  private final static Object initLock = new Object();

  //singleton accessor to be called once initialized
  public static DataMessageDispatcher getInstance() {
    if (INSTANCE == null) {
      throw new IllegalStateException("DataMessageDispatcher has not been initialized from properties");
    }
    return INSTANCE;
  }

  //singleton accessor for initial construction
  public static DataMessageDispatcher getInstance(Properties properties) {
    synchronized (initLock) {
      if (INSTANCE == null) {
        INSTANCE = new DataMessageDispatcher(properties);
      }
    }
    return INSTANCE;
  }

  private DataMessageDispatcher(Properties props) {
    super();
    pollTimeout = Long.parseLong((String) props.remove("pollTimeout"));
    props.put("group.id", String.valueOf(Concentrator.id));
    consumer = new KafkaConsumer<>(props);
    //consumer.subscribe(Arrays.asList(Concentrator.kafkaTopic));

    //manual assignment of partition so we can control offset seek
    final TopicPartition topicPartition = new TopicPartition(MessageBroker.kafkaTopic, 0);
    consumer.assign(Arrays.asList(topicPartition));
    consumer.seekToBeginning(Arrays.asList(topicPartition));
    logger.info("DataMessageDispatcher initialized");

    //get Executor service
    executorService = Executor.getInstance();
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
        if (!Concentrator.threadBlockingQueueMap.containsKey(threadId)) {
          logger.debug("Thread queue has thread with ids" + Concentrator.threadBlockingQueueMap.keySet());
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
            Concentrator.threadBlockingQueueMap.get(threadId).put(dataMessage);
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

  final void requestShutdown() {
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
