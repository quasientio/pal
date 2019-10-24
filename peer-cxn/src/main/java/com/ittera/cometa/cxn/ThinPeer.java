package com.ittera.cometa.cxn;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogRequest;
import com.ittera.cometa.PeerInfo;
import com.ittera.cometa.common.util.Strings;
import com.ittera.cometa.messages.LogMessageHeader;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/** This class is not thread-safe. For multi-threaded scenarios, use different instances. */
public class ThinPeer {

  private final UUID defaultPeerUuid = UUID.randomUUID();
  private UUID peerUuid;

  private final boolean allowP2P;

  // static
  private static final Logger logger = LoggerFactory.getLogger(ThinPeer.class);

  // kafka stuff
  private LogInfo inLog, outLog;
  private TopicPartition inTopicPartition;
  private Duration pollingDuration;
  private static final int PRECEDING_RECS = 50;
  private static final int PRODUCER_PARTITION = 0;

  private KafkaProducer<String, byte[]> producer;
  private KafkaConsumer<String, byte[]> consumer;

  private Map<Long, ConsumerRecord> lastRecordsRead = new HashMap<>();
  private ExecutorService singleThreadConsumerExecutor;
  private final Map<String, Header> HEADERS = new HashMap<>();

  // zmq stuff
  private final ZContext zmqContext;
  private final Socket peerSocket;
  private PeerInfo currentPeer;
  private boolean talkingToPeer;

  // PAL directory
  private PALDirectory palDirectory;

  // convenience constructors
  public ThinPeer(Properties properties) throws Exception {
    this(properties, null, null, null);
  }

  public ThinPeer(Properties properties, LogInfo logInfo) throws Exception {
    this(properties, null, logInfo, logInfo);
  }

  public ThinPeer(Properties properties, PeerInfo initialPeer, LogInfo logInfo) throws Exception {
    this(properties, initialPeer, logInfo, logInfo);
  }

  public ThinPeer(Properties properties, PeerInfo initialPeer) throws Exception {
    this(properties, initialPeer, null);
  }

  public ThinPeer(Properties properties, LogInfo inLog, LogInfo outLog) throws Exception {
    this(properties, null, inLog, outLog);
  }

  // THE constructor
  public ThinPeer(
      @Nullable Properties properties,
      @Nullable PeerInfo initialPeer,
      @Nullable LogInfo inLog,
      @Nullable LogInfo outLog)
      throws Exception {
    logger.info(
        "Initializing ThinPeer with props from: {}, initialPeer: {}, inLog: {}, outLog: {}",
        properties,
        initialPeer,
        inLog,
        outLog);

    final boolean logless = initialPeer != null;
    this.inLog = inLog;
    this.outLog = outLog;
    this.currentPeer = initialPeer;

    // set this peer's UUID
    if (properties != null && properties.getProperty("uuid") != null) {
      this.peerUuid = UUID.fromString(properties.getProperty("uuid"));
    } else {
      this.peerUuid = defaultPeerUuid;
    }

    // configure p2p
    this.allowP2P = Boolean.parseBoolean(loadProperty("peer.allowP2P", properties, "true"));
    logger.info("This peer will {}communicate P2P", allowP2P ? "" : "NOT ");

    // configure PAL directory
    String palDirectoryUrl = loadProperty("pal_directory", properties);
    if (palDirectoryUrl == null) {
      throw new RuntimeException(
          "Couldn't connect to PAL Directory. Please set the environment variable 'PAL_DIRECTORY'");
    }
    logger.info("Using PAL_DIRECTORY = {}", palDirectoryUrl);
    this.palDirectory = new PALDirectory(palDirectoryUrl);
    try {
      // register self as new peer TODO fill properties
      final Properties peerProperties = new Properties();
      if (properties != null && properties.getProperty("name") != null) {
        peerProperties.put("name", properties.getProperty("name"));
      }
      palDirectory.registerPeer(peerUuid, peerProperties);
    } catch (Exception ex) {
      logger.error("Error registering peer", ex);
    }

    if (!logless) {
      // configure log(s) to connect to; fill bootstrap servers if only log names given
      String kafkaTopicPrefix = loadProperty("kafkaTopicPrefix", properties);
      LogInfo lastLog = null;
      if (this.inLog == null) {
        // get last log with prefix = kafkaTopicPrefix
        lastLog = palDirectory.getLastLog(kafkaTopicPrefix);
        this.inLog = lastLog;
      } else {
        if (this.inLog.getBootstrapServers() == null) {
          this.inLog.setBrokerInfoSet(palDirectory.getKafkaBrokers());
        }
      }

      if (outLog == null) {
        if (lastLog == null) {
          lastLog = palDirectory.getLastLog(kafkaTopicPrefix);
        }
        this.outLog = lastLog;
      } else {
        if (this.outLog.getBootstrapServers() == null) {
          this.outLog.setBrokerInfoSet(palDirectory.getKafkaBrokers());
        }
      }
      logger.info("Will read from log: {} and write to log: {}", this.inLog, this.outLog);

      // configure kafka producer
      Properties kafkaProducerProps = loadKafkaProducerProps(properties);
      kafkaProducerProps.put("client.id", peerUuid.toString());
      kafkaProducerProps.put("bootstrap.servers", this.outLog.getBootstrapServers());
      this.producer = new KafkaProducer<>(kafkaProducerProps);
      logger.info(
          "Kafka producer initialized. Will connect to bootstrap servers: {}",
          this.outLog.getBootstrapServers());

      // configure kafka consumer
      Properties kafkaConsumerProps = loadKafkaConsumerProps(properties);
      kafkaConsumerProps.put("group.id", peerUuid.toString());
      kafkaConsumerProps.put("bootstrap.servers", this.inLog.getBootstrapServers());
      this.consumer = new KafkaConsumer<>(kafkaConsumerProps);
      logger.info(
          "Kafka consumer initialized. Will connect to bootstrap servers: {}",
          this.inLog.getBootstrapServers());

      // configure kafka misc
      pollingDuration =
          Duration.of(Long.parseLong(loadProperty("pollDuration", properties)), ChronoUnit.MILLIS);

      // manual assignment of partition so we can control offset seek
      inTopicPartition = new TopicPartition(this.inLog.getName(), 0);
      consumer.assign(Collections.singletonList(inTopicPartition));

      // init executor
      singleThreadConsumerExecutor = Executors.newSingleThreadExecutor();

      // create and store immutable headers (instead of creating with every send)
      this.HEADERS.put(
          "EXEC_MSG_TYPE_HEADER",
          new LogMessageHeader("type", Ints.toByteArray(MessageType.ExecMessage.ordinal())));
      this.HEADERS.put(
          "INTERCEPT_MSG_TYPE_HEADER",
          new LogMessageHeader("type", Ints.toByteArray(MessageType.InterceptRequest.ordinal())));
    }

    // configure ZMQ
    logger.info("Initializing zmq context");
    this.zmqContext = new ZContext();
    this.peerSocket = zmqContext.createSocket(SocketType.REQ);
    if (currentPeer != null) {
      if (currentPeer.getReqAddress() != null) {
        connectToPeer(currentPeer);
      } else if (currentPeer.getUuid() != null) {
        connectToPeer(currentPeer.getUuid());
      } else {
        throw new RuntimeException("Cannot connect to peer without UUID or listen address");
      }
    }
  }

  private Properties loadKafkaProducerProps(Properties properties) {
    Properties kafkaProducerProps = new Properties();
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(
            Strings.stringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
      } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.consumer")) {
        kafkaProducerProps.put(
            Strings.stringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }
    return kafkaProducerProps;
  }

  private Properties loadKafkaConsumerProps(Properties properties) {
    Properties kafkaConsumerProps = new Properties();
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.consumer.")) {
        kafkaConsumerProps.put(
            Strings.stringAfter(propKey, "kafka.consumer."), properties.getProperty(propKey));
      } else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.producer")) {
        kafkaConsumerProps.put(
            Strings.stringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }
    return kafkaConsumerProps;
  }

  /**
   * Get a property's value, by performing a search in the following order: 1) given properties
   * object, 2) ENV (uppercase variable) If not found, return defaultValue if given
   */
  private static String loadProperty(
      String propertyName, @Nullable Properties properties, @Nullable String defaultValue) {
    if (properties != null && properties.containsKey(propertyName)) {
      logger.debug("loading value of '{}' from properties object", propertyName);
      return properties.getProperty(propertyName);
    } else if (System.getenv(propertyName.toUpperCase()) != null) {
      logger.debug("loading value of '{}' from ENV", propertyName.toUpperCase());
      return System.getenv(propertyName.toUpperCase());
    } else if (defaultValue != null) {
      logger.debug("loading value of '{}' from default", propertyName);
      return defaultValue;
    }
    return null;
  }

  private static String loadProperty(String propertyName, Properties properties) {
    return loadProperty(propertyName, properties, null);
  }

  private void connectSocket() {
    peerSocket.setIdentity(("Dual-Peer-" + peerUuid.toString()).getBytes(ZMQ.CHARSET));
    peerSocket.connect(currentPeer.getReqAddress());
  }

  public ExecMessage sendAndReceive(ExecMessage message)
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    if (allowP2P && talkingToPeer) {
      return sendToPeer(message);
    } else {
      return sendToLogAndReceive(message);
    }
  }

  public ExecMessage waitFor(Wrappers.Type type, String fieldName)
      throws InvalidProtocolBufferException {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting wait for type: {} and field name: {}", type, fieldName);
    }
    // TODO extra param to seek before
    // consumer.seek(inTopicPartition, sentRecordOffset);

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      for (ConsumerRecord<String, byte[]> record : records) {
        final ExecMessage execMessage = ExecMessage.parseFrom(record.value());
        long receivedMsgOffset = record.offset();
        if (execMessage.hasStaticFieldPutDone()
            && fieldName.equals(execMessage.getStaticFieldPutDone().getField().getName())) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Got matching message with offset {}:\n{}", receivedMsgOffset, execMessage);
          }
          return execMessage;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipping record with offset {}", receivedMsgOffset);
          }
        }
      }
    }
  }

  public ExecMessage getMessageAtOffset(Long seek) throws InvalidProtocolBufferException {
    return getMessageAtOffset(seek, true);
  }

  private ExecMessage getMessageAtOffset(Long seek, boolean lookupCached)
      throws InvalidProtocolBufferException {
    if (logger.isDebugEnabled()) {
      logger.debug("Getting message @ offset #{}, lookupCached = {}", seek, lookupCached);
    }
    if (lookupCached) {
      ExecMessage cachedMsg = getCachedMessageAtOffset(seek);
      if (cachedMsg != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Got cached record at offset {}", seek);
        }
        return cachedMsg;
      }
    }

    Map<Long, ConsumerRecord> recordsRead = new HashMap<>();
    ConsumerRecord<String, byte[]> requestedRecord = null;

    long actualSeekOffset = (seek - PRECEDING_RECS < 0) ? seek : seek - PRECEDING_RECS;
    if (logger.isDebugEnabled()) {
      logger.debug("Seek to offset #{}", actualSeekOffset);
    }
    consumer.seek(inTopicPartition, actualSeekOffset);

    while (requestedRecord == null) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (logger.isDebugEnabled()) {
        logger.debug("Read {} records during poll", records.count());
      }
      for (ConsumerRecord record : records) {
        if (seek == record.offset()) {
          requestedRecord = record;
        }
        recordsRead.put(record.offset(), record);
      }
    }
    // now swap last batch (map) of records read with the new one
    this.lastRecordsRead = recordsRead;
    return ExecMessage.parseFrom(requestedRecord.value());
  }

  private ExecMessage getCachedMessageAtOffset(Long offset) throws InvalidProtocolBufferException {
    ConsumerRecord<String, byte[]> cached = lastRecordsRead.get(offset);
    if (cached != null) {
      return ExecMessage.parseFrom(cached.value());
    }
    return null;
  }

  public List<ConsumerRecord> getMessages(long startOffset, long numMessages) {

    if (logger.isDebugEnabled()) {
      logger.debug("Getting {} messages starting @ offset #{}", numMessages, startOffset);
    }
    consumer.seek(inTopicPartition, startOffset);
    List<ConsumerRecord> messages = new ArrayList<>();
    boolean gotAllMessages = false;

    while (!gotAllMessages) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (logger.isDebugEnabled()) {
        logger.debug("got {} records after poll", records.count());
      }
      for (ConsumerRecord record : records) {
        if (record.offset() < startOffset + numMessages) {
          messages.add(record);
          gotAllMessages = messages.size() == numMessages;
        }
      }
    }

    return messages;
  }

  public UUID getPeerUuid() {
    return peerUuid;
  }

  private Iterable<Header> getHeadersForMessage(MessageType messageType) {
    // set headers
    final List<Header> logHeaders = new ArrayList<>();
    if (messageType.equals(MessageType.ExecMessage)) {
      logHeaders.add(HEADERS.get("EXEC_MSG_TYPE_HEADER"));
    } else if (messageType.equals(MessageType.InterceptRequest)) {
      logHeaders.add(HEADERS.get("INTERCEPT_MSG_TYPE_HEADER"));
    }
    return logHeaders;
  }

  public void sendToLogAndForget(ExecMessage message) {
    // send to kafka
    producer.send(
        new ProducerRecord<>(
            outLog.getName(),
            PRODUCER_PARTITION,
            message.getMessageUuid(),
            message.toByteArray(),
            getHeadersForMessage(MessageType.ExecMessage)));
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log, and we're done:\n{}", message);
    }
  }

  public Future<ExecMessage> sendToLogAndAsyncProcessReqAndRepNodes(ExecMessage message) {

    final UUID requestMsgUuid = UUID.fromString(message.getMessageUuid());
    // send to kafka
    producer.send(
        new ProducerRecord<>(
            outLog.getName(),
            PRODUCER_PARTITION,
            message.getMessageUuid(),
            message.toByteArray(),
            getHeadersForMessage(MessageType.ExecMessage)));
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{} ({} bytes)", message, message.toByteArray().length);
    }

    final ExecMessageFuture messageFuture =
        new ExecMessageFuture(
            this,
            palDirectory,
            singleThreadConsumerExecutor,
            outLog.getName(),
            new LogRequest(requestMsgUuid));

    LogRequest logRequest;
    if (!outLog.equals(inLog)) {
      // if we are reading from a different log, ask for reply to be written to that log (our inLog)
      logRequest = new LogRequest(requestMsgUuid, inLog);
    } else {
      logRequest = new LogRequest(requestMsgUuid);
    }

    // asynchronously create req node
    try {
      palDirectory.addLogRequestAsync(outLog.getName(), logRequest, messageFuture);
    } catch (Exception e) {
      logger.error("Couldn't add request node to directory", e);
      return null;
    }

    return messageFuture;
  }

  private ExecMessage sendToLogAndReceive(ExecMessage message)
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    return sendToLogAndReceive(message, false);
  }

  private ExecMessage sendToLogAndReceive(ExecMessage message, boolean consumeLogUntilReply)
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {

    if (!allowP2P || consumeLogUntilReply) {
      return sendAndReceiveConsumingLog(message);
    }

    // default behavior (consumeLogUntilReply=false) is to wait for Future reply on directory
    return sendAsyncAndSwitchToPeer(message);
  }

  private ExecMessage sendAsyncAndSwitchToPeer(ExecMessage message)
      throws ExecutionException, InterruptedException {
    Future<ExecMessage> replyFuture = sendToLogAndAsyncProcessReqAndRepNodes(message);

    // wait for reply (blocking)
    ExecMessage replyMsg = replyFuture.get();

    // switch to direct p2p talk
    String peerUuid = replyMsg.getPeerUuid();
    connectToPeer(UUID.fromString(peerUuid));

    return replyMsg;
  }

  private ExecMessage sendAndReceiveConsumingLog(ExecMessage message)
      throws InvalidProtocolBufferException {
    // send to kafka
    long sentRecordOffset;
    Future<RecordMetadata> recordMetadataFuture =
        producer.send(
            new ProducerRecord<>(
                outLog.getName(),
                PRODUCER_PARTITION,
                message.getMessageUuid(),
                message.toByteArray(),
                getHeadersForMessage(MessageType.ExecMessage)));
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Message sent with uuid: {} \n{}",
            message.getMessageUuid(),
            getRecordInfo(recordMetadata));
      }
      sentRecordOffset = recordMetadata.offset();
    } catch (Exception e) {
      logger.error("Error getting sent record metadata", e);
      return null;
    }

    // now poll to consume
    if (logger.isDebugEnabled()) {
      logger.debug("Consumer seeking to offset: {}", sentRecordOffset);
    }
    consumer.seek(inTopicPartition, sentRecordOffset);

    // wait for the reply to the sent message (reply should contain following = sentRecordOffset in
    // message)
    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (records.count() != 0 && logger.isDebugEnabled()) {
        logger.debug("Received {} records", records.count());
      }
      for (ConsumerRecord<String, byte[]> record : records) {
        final ExecMessage execMessage = ExecMessage.parseFrom(record.value());
        long receivedMsgOffset = record.offset();
        if (execMessage.hasFollowingUuid()
            && message.getMessageUuid().equals(execMessage.getFollowingUuid())) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Got reply with offset {} and uuid {} ",
                receivedMsgOffset,
                execMessage.getMessageUuid());
          }
          // try switching to direct peer talk (i.e. p2p)
          if (allowP2P) {
            UUID peerUuid = UUID.fromString(execMessage.getPeerUuid());
            PeerInfo newPeer = null;
            try {
              // we getPeerProperties and close after since we assume we'll get here only once
              newPeer = palDirectory.getPeerInfo(peerUuid);
            } catch (Exception ex) {
              logger.error("Couldn't get peer properties", ex);
            }
            if (newPeer != null && !newPeer.equals(currentPeer)) {
              connectToPeer(newPeer);
            }
          }

          return execMessage;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipping record with offset {}", receivedMsgOffset);
          }
        }
      }
    }
  }

  public void connectToPeer(UUID peerUuid) {
    PeerInfo newPeer = null;
    try {
      newPeer = palDirectory.getPeerInfo(peerUuid);
    } catch (Exception ex) {
      logger.error("Couldn't get peer properties", ex);
    }
    if (newPeer != null && !newPeer.equals(currentPeer)) {
      connectToPeer(newPeer);
    } else {
      throw new IllegalArgumentException(
          String.format("peer entry w/uuid: %s not found in directory", peerUuid));
    }
  }

  private void connectToPeer(PeerInfo peerInfo) {
    logger.info("Now in direct talk with {}", peerInfo);
    currentPeer = peerInfo;
    connectSocket();
    talkingToPeer = true;
  }

  private ExecMessage sendToPeer(ExecMessage message) {

    // send message request to peer
    peerSocket.send(message.toByteArray());

    final long waitStart = System.currentTimeMillis();
    byte[] reply = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    ExecMessage replyMsg = null;
    try {
      replyMsg = ExecMessage.parseFrom(reply);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got reply message with uuid: {}, waited {} ms",
            replyMsg.getMessageUuid(),
            (waitEnd - waitStart));
      }
    } catch (InvalidProtocolBufferException ipbe) {
      logger.error("Caught protobuf exception", ipbe);
    }

    return replyMsg;
  }

  private static String getRecordInfo(RecordMetadata recordMetadata) {

    return String.format(
        "{%n timestamp: %d,%n offset: %d,%n #bytes in value: %d%n}",
        recordMetadata.timestamp(), recordMetadata.offset(), recordMetadata.serializedValueSize());
  }

  private void close(Closeable resource, String msg) {
    if (resource != null) {
      try {
        resource.close();
        logger.info(msg);
      } catch (IOException e) {
        logger.warn("Error closing resource", e);
      }
    }
  }

  public void close() {
    // close socket-related resources
    close(peerSocket, "Peer socket closed.");
    try {
      if (zmqContext != null) {
        zmqContext.destroy();
        logger.info("Zmq context closed.");
      }
    } catch (Exception ex) {
      logger.error("Error freeing zmq resources", ex);
    }

    // close log-related resources
    close(producer, "Log producer closed.");
    close(consumer, "Log consumer closed.");
    if (singleThreadConsumerExecutor != null) {
      singleThreadConsumerExecutor.shutdown();
      logger.info("Consumer executor service shut down");
    }
  }
}
