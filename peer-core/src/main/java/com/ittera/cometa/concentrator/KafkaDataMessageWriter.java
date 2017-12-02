package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.cxn.ZkClient;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * TODO A 2nd thread that sends non-urgent messages from a queue.
 */

@Singleton
public class KafkaDataMessageWriter extends AbstractExecutionThreadService implements KafkaMessageWriter {

    protected static final Logger logger = LoggerFactory.getLogger(KafkaDataMessageWriter.class);

    // kafka stuff
    private KafkaProducer producer;
    private String kafkaTopic;
    private final Properties producerProperties = new Properties();

    // zmq stuff
    @Inject
    private ZContext zmqContext;
    private Socket subscriber;
    private Socket offsetPublisher;
    private final String outPubAddress, offsetPubAddress;

    // zookeeper
    @Inject
    private PeerLogDirectory peerLogDirectory;

    private volatile boolean connectionsOpen = false;
    private final AtomicInteger messagesSent = new AtomicInteger(0);

    /**
     * Helper class to relate DataMessage with offset. Needed so that we can write replies' offsets to zookeeper.
     *
     * NOTE 1: assumes all calls to this class are by the same thread (kafka's IO thread).
     * NOTE 2: TODO we shouldn't block kafka's IO thread for too long, or we'll slow
     * down writing of records. Ideally move work to an Executor class.
     *
     * Ensure only 1 thread at the time uses this class **zeromq's sockets aren't thread-safe**
     */
    private final class MessageOffsetInformer implements Callback, Watcher {
        private final DataMessage message;
        private LogReply logReply;
        private boolean done = false;
        private Throwable lastError;

        private final AsyncCallback.StringCallback addReplyCallback = new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int rc, String path, Object ctx, String name) {
                switch (Code.get(rc)) {
                    case OK:
                        logger.debug("reply node created for message w/uuid: {}", message.getMessageUuid());
                        done = true;
                        break;
                    default:
                        logger.warn("reply node NOT created (error code: {}) for message w/uuid: {}", rc, message.getMessageUuid());
                        return;
                }
            }
        };

        private final AsyncCallback.StatCallback statCallback = new AsyncCallback.StatCallback() {
            @Override
            public void processResult(int rc, String path, Object ctx, Stat stat) {
              logger.debug("processResult with rc: {}, path: {}, and stat: {}", rc, path, stat);
            }
        };

        MessageOffsetInformer(DataMessage message) {
            this.message = message;
        }

        boolean isDone() {
            return done;
        }

        Throwable getLastError() {
            return lastError;
        }

        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {

            // publish new record offset
            offsetPublisher.send(String.valueOf(recordMetadata.offset()), 0);
            logger.debug("New offset {} for message w/uuid: {}", recordMetadata.offset(), message.getMessageUuid());

            // if message is reply, save offset to zookeeper
            if (message.hasFollowingUuid()) {
                this.logReply = new LogReply(message.getMessageUuid(), Concentrator.uuid.toString(), message.getFollowingUuid(), recordMetadata.offset());
                try {
                    ((ZkClient)peerLogDirectory).addLogReply(kafkaTopic, logReply, addReplyCallback);
                } catch (IllegalArgumentException iae) {
                  // request node probably doesn't exist, add ourselves as watcher
                  ((ZkClient)peerLogDirectory).requestExists(kafkaTopic, message.getFollowingUuid(), this, statCallback);
                } catch (Exception ex) {
                    logger.error("Unhandled error creating reply message offset for request w/uuid: {}. Giving up.", message.getFollowingUuid(), ex);
                    lastError = ex;
                }
            }
        }

        /**
         * Callback for the times when we want to reply with the offset, but the request node doesn't exist yet
         * @param watchedEvent
         */
        @Override
        public void process(WatchedEvent watchedEvent) {

          if (watchedEvent.getType() == Event.EventType.NodeCreated) {
            logger.debug("node created event: {}, will retry to write add reply node", watchedEvent);
            try {
                ((ZkClient)peerLogDirectory).addLogReply(kafkaTopic, logReply, addReplyCallback);
            } catch (Exception ex) {
                logger.error("Error creating reply message offset for request w/uuid: {}. Giving up.", message.getFollowingUuid(), ex);
                lastError = ex;
            }
          }
        }
    }

    @Inject
    public KafkaDataMessageWriter(@Named("key.serializer") String keySerializer,
                                  @Named("value.serializer") String valueSerializer,
                                  @Named("out.pub") String outPubAddress,
                                  @Named("offset.pub") String offsetPubAddress) {
        this.outPubAddress = outPubAddress;
        this.offsetPubAddress = offsetPubAddress;
        producerProperties.put("key.serializer", keySerializer);
        producerProperties.put("value.serializer", valueSerializer);
        logger.info("Initialized kafka message writer");
    }

    public void openConnections() {

        // start subscriber
        this.subscriber = zmqContext.createSocket(ZMQ.SUB);
        subscriber.bind(outPubAddress);
        subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        logger.info("Subscriber connected");

        // start offsets publisher
        this.offsetPublisher = zmqContext.createSocket(ZMQ.PUB);
        offsetPublisher.bind(offsetPubAddress);
        logger.info("Publisher connected");

        connectionsOpen = true;
        logger.info("All connections open - except kafka producer");
    }

    protected void closeConnections() {
        if (producer != null) {
            producer.close();
        }

        if (subscriber != null) {
            subscriber.close();
        }

        if (offsetPublisher != null) {
            offsetPublisher.close();
        }

        logger.info("All connections closed");
    }

    @Override
    public void writeToLastLog(String logNamePrefix) throws Exception {

        LogInfo lastLog = peerLogDirectory.getLastLog(logNamePrefix);
        writeToLog(lastLog.getName());
    }

    @Override
    public void writeToLog(String logName) throws Exception {

        LogInfo lastLog = peerLogDirectory.getLogInfo(logName);
        this.kafkaTopic = lastLog.getName();
        String bootstrapServers = lastLog.getBootstrapServers();
        producerProperties.put("bootstrap.servers", bootstrapServers);
        // start kafka writer
        this.producer = new KafkaProducer<>(producerProperties);
        logger.info("Will write to log: {} and bootstrapServers: {}", kafkaTopic, bootstrapServers);
    }

    @Override
    public void run() {

        //wait for connections established
        while (!connectionsOpen) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                //what to do
            }
        }

        logger.debug("Starting to dispatch messages to kafka");

        while (isRunning() && !Thread.interrupted()) {

            byte[] msg = null;
            try {
                msg = subscriber.recv();
            } catch (ZMQException ex) {
                int errorCode = ex.getErrorCode();
                if (errorCode == ZError.ETERM) {
                    logger.debug("Caught ETERM during blocking read. Breaking out.");
                    break;
                } else if (errorCode == ZError.EINTR) {
                    logger.debug("Caught EINTR during blocking read. Breaking out.");
                    break;
                } else {
                    throw ex;
                }
            }

            DataMessage dataMessage = null;
            try {
                dataMessage = DataMessage.parseFrom(msg);
            } catch (Exception e) {
                logger.error("Caught exception parsing message", e);
            }

            // got a message
            if (dataMessage != null) {

                // send to kafka immediately
                sendToKafka(dataMessage);
            }
        }

        closeConnections();
    }

    private void sendToKafka(DataMessage message) {
        logger.debug("sending new message with uuid: {}", message.getMessageUuid());
        ProducerRecord newRecord = new ProducerRecord(kafkaTopic, message);
        producer.send(newRecord, new MessageOffsetInformer(message));
        messagesSent.getAndIncrement();
        logger.debug("new message sent with uuid: {} replying to message uuid: {}", message.getMessageUuid(),
                message.getFollowingUuid());
    }

    @Override
    protected void triggerShutdown() {

        logger.info("Data message writer shutting down.");
    }

    @Override
    protected void shutDown() throws Exception {

        logger.info("Data message writer shut down.");
    }

    @Override
    protected void startUp() throws Exception {
        openConnections();
    }
}
