/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.cxn;

import static java.lang.String.format;

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
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
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.LogRequest;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/** This class is not thread-safe. For multi-threaded scenarios, use different instances. */
public class ThinPeer {

  private UUID peerUuid;
  private String peerName;
  private boolean allowP2P = true;
  private boolean closed;
  private boolean initialized;
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();

  // static
  private static final Logger logger = LoggerFactory.getLogger(ThinPeer.class);

  // kafka stuff
  private LogInfo inLog;
  private LogInfo outLog;
  private TopicPartition inTopicPartition;
  private Duration pollingDuration;
  private static final int PRECEDING_RECS = 50;
  private static final int PRODUCER_PARTITION = 0;
  private static final int DEFAULT_POLLING_DURATION_MILLIS = 10;
  private String logPrefix;
  public static final String DEFAULT_TOPIC_PREFIX = "app";

  private Producer<String, byte[]> producer;
  private Consumer<String, byte[]> consumer;
  private boolean producerGiven;
  private boolean consumerGiven;
  private Properties producerProperties;
  private Properties consumerProperties;

  private Map<Long, ConsumerRecord> lastRecordsRead = new HashMap<>();
  private ExecutorService asyncConsumerExecutor;

  // zmq stuff
  private ZContext zmqContext;
  private Socket peerSocket;
  private PeerInfo currentPeer;
  private boolean talkingToPeer;
  private boolean zmqContextGiven;

  // PAL directory
  private DirectoryConnectionProvider directoryConnectionProvider;
  private boolean directoryGiven;
  private String palDirectoryUrl;

  public ThinPeer() {
    // TODO use factory method instead of empty constructor
  }

  public ThinPeer withUUID(UUID uuid) {
    this.peerUuid = uuid;
    return this;
  }

  public ThinPeer withName(String name) {
    this.peerName = name;
    return this;
  }

  public ThinPeer withLog(LogInfo log) {
    this.inLog = log;
    this.outLog = log;
    return this;
  }

  public ThinPeer withInLog(LogInfo inLog) {
    this.inLog = inLog;
    return this;
  }

  public ThinPeer withOutLog(LogInfo outLog) {
    this.outLog = outLog;
    return this;
  }

  public ThinPeer withLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
    return this;
  }

  public ThinPeer withConsumer(Consumer<String, byte[]> consumer) {
    this.consumer = consumer;
    this.consumerGiven = true;
    return this;
  }

  public ThinPeer withConsumerProperties(Properties properties) {
    this.consumerProperties = properties;
    return this;
  }

  public ThinPeer withPollingDuration(long millis) {
    this.pollingDuration = Duration.of(millis, ChronoUnit.MILLIS);
    return this;
  }

  public ThinPeer withProducer(Producer<String, byte[]> producer) {
    this.producer = producer;
    this.producerGiven = true;
    return this;
  }

  public ThinPeer withProducerProperties(Properties properties) {
    this.producerProperties = properties;
    return this;
  }

  public ThinPeer withZContext(ZContext zContext) {
    this.zmqContext = zContext;
    this.zmqContextGiven = true;
    return this;
  }

  public ThinPeer withInitialPeer(PeerInfo initialPeer) {
    this.currentPeer = initialPeer;
    return this;
  }

  public ThinPeer withNoP2P() {
    this.allowP2P = false;
    return this;
  }

  public ThinPeer withDirectoryProvider(DirectoryConnectionProvider directoryConnectionProvider) {
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.directoryGiven = true;
    return this;
  }

  public ThinPeer withDirectoryURL(String palDirectoryUrl) {
    this.palDirectoryUrl = palDirectoryUrl;
    return this;
  }

  public ThinPeer init() throws Exception {
    // if not given, create random UUID for this peer
    if (this.peerUuid == null) {
      this.peerUuid = UUID.randomUUID();
    }

    if (palDirectoryUrl != null && directoryConnectionProvider != null) {
      throw new IllegalArgumentException(
          "ThinPeer needs a PAL directory address or a connection provider, but not both");
    }

    // configure PAL directory
    if (directoryConnectionProvider != null) {
      this.palDirectoryUrl = directoryConnectionProvider.getConnectionString();
    } else {
      if (palDirectoryUrl == null) {
        this.palDirectoryUrl = PALDirectory.NO_URL;
      }
      directoryConnectionProvider = new DirectoryConnectionProvider(palDirectoryUrl);
    }

    // register self as peer
    if (getPalDirectory() != null) {
      try {
        final Properties peerProperties = new Properties();
        if (this.peerName != null) {
          peerProperties.put("name", peerName);
        }
        getPalDirectory().registerPeer(peerUuid, peerProperties);
      } catch (Exception ex) {
        logger.error("Error registering peer", ex);
      }
    }

    final boolean logless = currentPeer != null;
    if (!logless) {
      // configure log(s) to connect to; fill bootstrap servers if only log names given
      String kafkaTopicPrefix = logPrefix != null ? logPrefix : DEFAULT_TOPIC_PREFIX;
      LogInfo lastLog = null;
      if (this.inLog == null) {
        lastLog = getPalDirectory().getLastLog(kafkaTopicPrefix);
        this.inLog = lastLog;
      } else {
        if (this.inLog.getBootstrapServers() == null) {
          this.inLog.setBrokerInfoSet(getPalDirectory().getKafkaBrokers());
        }
      }

      if (outLog == null) {
        if (lastLog == null) {
          lastLog = getPalDirectory().getLastLog(kafkaTopicPrefix);
        }
        this.outLog = lastLog;
      } else {
        if (this.outLog.getBootstrapServers() == null) {
          this.outLog.setBrokerInfoSet(getPalDirectory().getKafkaBrokers());
        }
      }

      logger.info("Will read from log: {}, and write to log: {}", this.inLog, this.outLog);

      // configure kafka producer
      if (producer == null) {
        if (producerProperties == null) {
          throw new RuntimeException("You must supply either Producer or ProducerProperties");
        }
        producerProperties.put("client.id", peerUuid.toString());
        final String bootstrapServers = this.outLog.getBootstrapServers();
        producerProperties.put("bootstrap.servers", bootstrapServers);
        this.producer = new KafkaProducer<>(producerProperties);
        logger.info(
            "Kafka producer initialized. Will connect to bootstrap servers: {}", bootstrapServers);
      }

      // configure kafka consumer
      if (consumer == null) {
        if (consumerProperties == null) {
          throw new RuntimeException("You must supply either Consumer or ConsumerProperties");
        }
        consumerProperties.put("group.id", peerUuid.toString());
        final String bootstrapServers = this.inLog.getBootstrapServers();
        consumerProperties.put("bootstrap.servers", bootstrapServers);
        this.consumer = new KafkaConsumer<>(consumerProperties);
        logger.info(
            "Kafka consumer initialized. Will connect to bootstrap servers: {}", bootstrapServers);
      }

      // set polling duration
      if (pollingDuration == null) {
        pollingDuration = Duration.of(DEFAULT_POLLING_DURATION_MILLIS, ChronoUnit.MILLIS);
      }

      // manual assignment of partition so we can control offset seek
      inTopicPartition = new TopicPartition(this.inLog.getName(), 0);
      consumer.assign(Collections.singletonList(inTopicPartition));

      // init executor
      asyncConsumerExecutor = Executors.newSingleThreadExecutor();
    }

    // configure ZMQ
    if (allowP2P) {
      if (zmqContext == null) {
        logger.info("Initializing zmq context");
        this.zmqContext = new ZContext();
      }
      this.peerSocket = zmqContext.createSocket(SocketType.REQ);
      if (currentPeer != null) {
        if (currentPeer.getReqAddress() != null) {
          connectToPeer(currentPeer);
        } else if (currentPeer.getUuid() != null) {
          connectToPeer(currentPeer.getUuid());
        } else {
          throw new RuntimeException(
              format(
                  "Cannot connect to peer without its UUID or listening (i.e. REQ) address. Peer -> %s",
                  currentPeer));
        }
      }
    }

    initialized = true;
    logger.info(
        format(
            "Initialized ThinPeer with:%n uuid: %s,%n name: %s,%n directory: %s,%n initialPeer: %s,%n inLog: %s,%n outLog: %s",
            peerUuid, peerName, palDirectoryUrl, currentPeer, inLog, outLog));

    return this;
  }

  private PALDirectory getPalDirectory() {
    if (directoryConnectionProvider != null) {
      return directoryConnectionProvider.get().orElse(null);
    }
    return null;
  }

  private void connectSocket() {
    peerSocket.setIdentity(("Dual-Peer-" + peerUuid.toString()).getBytes(ZMQ.CHARSET));
    peerSocket.connect(currentPeer.getReqAddress());
  }

  private void assertInitialized() {
    if (!initialized) {
      throw new IllegalStateException("ThinPeer is not initialized. Did you call init()?");
    }
  }

  public ExecMessage sendAndReceive(ExecMessage message, boolean consumeLogUntilReply)
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceive: in with message: {}", message);
    }
    if (talkingToPeer) {
      return sendToPeer(message);
    } else {
      return sendToLogAndReceive(message, consumeLogUntilReply);
    }
  }

  /** <b>INCOMPLETE</b>: only checks for matches of staticFieldPutDone's ONLY USED BY SWING TESTS */
  public ExecMessage waitFor(ExecMessageType type, String fieldName)
      throws InvalidProtocolBufferException {
    assertInitialized();
    if (logger.isDebugEnabled()) {
      logger.debug("Starting wait for type: {} and field name: {}", type, fieldName);
    }
    // TODO extra param to seek before -> consumer.seek(inTopicPartition, sentRecordOffset);

    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      for (ConsumerRecord<String, byte[]> record : records) {
        final Message message = Message.parseFrom(record.value());
        long receivedMsgOffset = record.offset();
        if (message.hasExecMessage()) {
          final ExecMessage execMessage = message.getExecMessage();
          if (execMessage.hasStaticFieldPutDone()
              && fieldName.equals(execMessage.getStaticFieldPutDone().getField().getName())) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Got matching message with offset {}:\n{}", receivedMsgOffset, execMessage);
            }
            return execMessage;
          }
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipping record with offset {}", receivedMsgOffset);
          }
        }
      }
    }
  }

  public Message getMessageAtOffset(Long seek) throws InvalidProtocolBufferException {
    return getMessageAtOffset(seek, true);
  }

  private Message getMessageAtOffset(Long seek, boolean lookupCached)
      throws InvalidProtocolBufferException {
    assertInitialized();
    if (logger.isDebugEnabled()) {
      logger.debug("Getting message @ offset #{}, lookupCached = {}", seek, lookupCached);
    }
    if (lookupCached) {
      Message cachedMsg = getCachedMessageAtOffset(seek);
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
    return Message.parseFrom(requestedRecord.value());
  }

  private Message getCachedMessageAtOffset(Long offset) throws InvalidProtocolBufferException {
    ConsumerRecord<String, byte[]> cached = lastRecordsRead.get(offset);
    if (cached != null) {
      return Message.parseFrom(cached.value());
    }
    return null;
  }

  public List<ConsumerRecord> getMessages(long startOffset, long numMessages) {
    assertInitialized();
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

  public void sendToLogAndForget(ExecMessage message) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToLogAndForget: in with message: {}", message);
    }
    // send to kafka
    final byte[] body = msgBuilder.wrap(message).toByteArray();
    producer.send(
        new ProducerRecord<>(outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body));
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{} ({} bytes), and we're done", message, body.length);
    }
  }

  public Future<ExecMessage> sendToLogAndAsyncProcessReqAndRepNodes(ExecMessage message) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToLogAndAsyncProcessReqAndRepNodes: in with message: {}", message);
    }
    final UUID requestMsgUuid = UUID.fromString(message.getMessageUuid());
    // send to kafka
    final byte[] body = msgBuilder.wrap(message).toByteArray();
    producer.send(
        new ProducerRecord<>(outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body));
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{} ({} bytes)", message, body.length);
    }

    final ExecMessageFuture messageFuture =
        new ExecMessageFuture(
            this,
            getPalDirectory(),
            asyncConsumerExecutor,
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
      getPalDirectory().addLogRequestAsync(outLog.getName(), logRequest, messageFuture);
    } catch (Exception e) {
      logger.error("Couldn't add request node to directory", e);
      return null;
    }

    return messageFuture;
  }

  private ExecMessage sendToLogAndReceive(ExecMessage message, boolean consumeLogUntilReply)
      throws ExecutionException, InterruptedException, InvalidProtocolBufferException {

    if (logger.isTraceEnabled()) {
      logger.trace("sendToLogAndReceive: in with message: {}", message);
    }
    if (!allowP2P) {
      return sendAndReceiveConsumingLog(message);
    }

    if (consumeLogUntilReply) {
      return sendToLogConsumeAndSwitchToPeer(message);
    } else {
      // wait for Future reply on directory
      return sendAsyncAndSwitchToPeer(message);
    }
  }

  private ExecMessage sendToLogConsumeAndSwitchToPeer(ExecMessage message)
      throws InvalidProtocolBufferException {
    if (logger.isTraceEnabled()) {
      logger.trace("sendToLogConsumeAndSwitchToPeer: in with message: {}", message);
    }
    ExecMessage replyMsg = sendAndReceiveConsumingLog(message);

    // switch to direct p2p talk
    String msgPeerUuid = replyMsg.getPeerUuid();
    connectToPeer(UUID.fromString(msgPeerUuid));

    return replyMsg;
  }

  private ExecMessage sendAsyncAndSwitchToPeer(ExecMessage message)
      throws ExecutionException, InterruptedException {
    if (logger.isTraceEnabled()) {
      logger.trace("sendAsyncAndSwitchToPeer: in with message: {}", message);
    }
    Future<ExecMessage> replyFuture = sendToLogAndAsyncProcessReqAndRepNodes(message);

    // wait for reply (blocking)
    ExecMessage replyMsg = replyFuture.get();

    // switch to direct p2p talk
    String msgPeerUuid = replyMsg.getPeerUuid();
    connectToPeer(UUID.fromString(msgPeerUuid));

    return replyMsg;
  }

  private ExecMessage sendAndReceiveConsumingLog(ExecMessage message)
      throws InvalidProtocolBufferException {
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceiveConsumingLog: in with message: {}", message);
    }
    // send to kafka
    long sentRecordOffset;
    final byte[] body = msgBuilder.wrap(message).toByteArray();
    Future<RecordMetadata> recordMetadataFuture =
        producer.send(
            new ProducerRecord<>(
                outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body));
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      if (logger.isDebugEnabled()) {
        logger.debug("Message sent ({} bytes):\n {}", body.length, message);
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

    // wait for reply  (should contain following = sentRecordOffset in message)
    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (records.count() != 0 && logger.isDebugEnabled()) {
        logger.debug("Received {} records", records.count());
      }
      for (ConsumerRecord<String, byte[]> record : records) {
        final Message rcvdMsg = Message.parseFrom(record.value());
        long receivedMsgOffset = record.offset();
        final ExecMessage execMessage = rcvdMsg.getExecMessage();
        if (execMessage != null
            && execMessage.hasFollowingUuid()
            && message.getMessageUuid().equals(execMessage.getFollowingUuid())) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Got reply with offset {} and uuid {} ",
                receivedMsgOffset,
                execMessage.getMessageUuid());
          }
          // try switching to direct peer talk (i.e. p2p)
          if (allowP2P) {
            UUID msgPeerUuid = UUID.fromString(execMessage.getPeerUuid());
            PeerInfo newPeer = null;
            try {
              // we getPeerProperties and close after since we assume we'll get here only once
              newPeer = getPalDirectory().getPeerInfo(msgPeerUuid);
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
      newPeer = getPalDirectory().getPeerInfo(peerUuid);
    } catch (Exception ex) {
      logger.error("Couldn't get peer properties", ex);
    }
    if (newPeer != null && !newPeer.equals(currentPeer)) {
      connectToPeer(newPeer);
    }
  }

  private void connectToPeer(PeerInfo peerInfo) {
    if (logger.isTraceEnabled()) {
      logger.trace("connectToPeer: in with peerUuid: {}", peerUuid);
    }
    if (!allowP2P) {
      throw new RuntimeException("Cannot connect to peer: p2p is disallowed");
    }
    logger.info("Now in direct talk with {}", peerInfo);
    currentPeer = peerInfo;
    connectSocket();
    talkingToPeer = true;
  }

  public ExecMessage sendToPeer(ExecMessage message) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToPeer: in with message: {}", message);
    }
    // send message request to peer
    peerSocket.send(msgBuilder.wrap(message).toByteArray());

    final long waitStart = System.currentTimeMillis();
    byte[] reply = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    ExecMessage replyMsg = null;
    try {
      replyMsg = Message.parseFrom(reply).getExecMessage();
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

  private void close(Consumer consumer, long timeout, TemporalUnit timeUnit, String msg) {
    if (consumer != null) {
      try {
        consumer.close(Duration.of(timeout, timeUnit));
        logger.info(msg);
      } catch (Exception e) {
        logger.warn("Error closing consumer", e);
      }
    }
  }

  private void close(Producer producer, long timeout, TemporalUnit timeUnit, String msg) {
    if (producer != null) {
      try {
        producer.close(Duration.of(timeout, timeUnit));
        logger.info(msg);
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
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
    assertInitialized();

    // NOTE: we only resources that were not passed to us

    // close socket-related resources
    close(peerSocket, "Peer socket closed.");
    if (!zmqContextGiven) {
      try {
        if (zmqContext != null) {
          zmqContext.destroy();
          logger.info("Zmq context closed.");
        }
      } catch (Exception ex) {
        logger.error("Error freeing zmq resources", ex);
      }
    }

    // close log-related resources
    if (!producerGiven) {
      close(producer, 500, ChronoUnit.MILLIS, "Log producer closed.");
    }
    if (!consumerGiven) {
      close(consumer, 500, ChronoUnit.MILLIS, "Log consumer closed.");
    }
    if (asyncConsumerExecutor != null) {
      asyncConsumerExecutor.shutdown();
      logger.info("Consumer executor service shut down");
    }

    // close directory
    if (!directoryGiven && getPalDirectory() != null) {
      getPalDirectory().close();
    }

    closed = true;
  }

  public boolean isClosed() {
    return closed;
  }
}
