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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.RPCType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.JSONSerializers;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.jsonrpc.JsonRpcResponseDeserializer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/** This class is not thread-safe. For multi-threaded scenarios, use different instances. */
public class ThinPeer implements AutoCloseable {

  private UUID peerUuid;
  private String peerName;
  private boolean allowP2P = true;
  private boolean isSocketConnected = false;
  private boolean closed;
  private boolean initialized;
  private final MessageBuilder msgBuilder = new MessageBuilder();

  // static
  private static final Logger logger = LoggerFactory.getLogger(ThinPeer.class);

  // kafka stuff
  private String bootstrapServers;
  private boolean logIOEnabled;
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

  // rpc stuff
  private ZContext zmqContext;
  private Socket peerSocket;
  private WSClient wsClient;
  private String rpcAddress;
  private Gson gson;
  private RPCType outboundRpcType = RPCType.RPC;
  private PeerInfo initialPeer;
  private PeerInfo currentPeer;
  private boolean talkingToPeer;
  private boolean zmqContextGiven;

  // PAL directory
  private DirectoryConnectionProvider directoryConnectionProvider;
  private boolean directoryGiven;
  private String palDirectoryUrl;
  private boolean registerSelf = true;

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

  public ThinPeer withRPCAddress(String rpcAddress) {
    this.rpcAddress = rpcAddress;
    return this;
  }

  public ThinPeer withBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
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
    this.initialPeer = initialPeer;
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

  public ThinPeer withSelfRegistration(boolean registerSelf) {
    this.registerSelf = registerSelf;
    return this;
  }

  public ThinPeer withOutboundRPCType(RPCType rpcType) {
    this.outboundRpcType = rpcType;
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
    if (registerSelf && getPalDirectory() != null) {
      try {
        final PeerInfo self = new PeerInfo(peerUuid);
        if (this.peerName != null) {
          self.setName(peerName);
        }
        if (this.rpcAddress != null) {
          self.setRpcAddress(rpcAddress);
        }
        getPalDirectory().registerPeer(self);
      } catch (Exception ex) {
        logger.error("Error registering peer", ex);
      }
    }

    final boolean logless =
        (!producerGiven && producerProperties == null)
            || (!consumerGiven && consumerProperties == null)
            || initialPeer != null;

    if (!logless) {
      // configure log(s) to connect to; fill bootstrap servers if only log names given
      String kafkaTopicPrefix = logPrefix != null ? logPrefix : DEFAULT_TOPIC_PREFIX;
      LogInfo lastLog = null;
      if (this.inLog == null) {
        lastLog = getPalDirectory().getLastLogWithPrefix(kafkaTopicPrefix);
        this.inLog = lastLog;
      } else {
        if (this.inLog.getBootstrapServers() == null && bootstrapServers != null) {
          this.inLog.setBootstrapServers(bootstrapServers);
        }
      }

      if (outLog == null) {
        if (lastLog == null) {
          lastLog = getPalDirectory().getLastLogWithPrefix(kafkaTopicPrefix);
        }
        this.outLog = lastLog;
      } else {
        if (this.outLog.getBootstrapServers() == null && bootstrapServers != null) {
          this.outLog.setBootstrapServers(bootstrapServers);
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

      logIOEnabled = true;
    }

    // configure RPC and connect to initial peer if given
    if (allowP2P) {
      if (outboundRpcType == RPCType.RPC) {
        if (zmqContextGiven) {
          logger.info("Using given ZMQ context");
        } else {
          logger.info("Initializing zmq context");
          this.zmqContext = new ZContext();
        }
        this.peerSocket = zmqContext.createSocket(SocketType.REQ);
      }
      if (initialPeer != null) {
        if (initialPeer.getRpcAddress() != null || initialPeer.getJsonrpcAddress() != null) {
          connectToPeer(initialPeer);
        } else if (initialPeer.getUuid() != null) {
          connectToPeer(initialPeer.getUuid());
        } else {
          throw new RuntimeException(
              format(
                  "Cannot connect to peer without its UUID or listening (i.e. RPC) address. Peer -> %s",
                  initialPeer));
        }
      }
    }

    // initialize gson, registering custom adapters for JSON-RPC Response messages
    this.gson =
        new GsonBuilder()
            .registerTypeAdapter(
                StaticFieldPutDone.class, new JSONSerializers.StaticFieldPutDoneAdapter())
            .registerTypeAdapter(
                InstanceFieldPutDone.class, new JSONSerializers.InstanceFieldPutDoneAdapter())
            .registerTypeAdapter(ReturnValue.class, new JSONSerializers.ReturnValueAdapter())
            .registerTypeAdapter(JsonRpcResponse.class, new JsonRpcResponseDeserializer())
            .create();

    initialized = true;
    logger.info(
        format(
            "Initialized ThinPeer with:%n uuid: %s,%n name: %s,%n rpcAddress: %s,%n directory: %s,%n initialPeer: %s,%n rpcType: %s,%n inLog: %s,%n outLog: %s",
            peerUuid,
            peerName,
            rpcAddress,
            palDirectoryUrl,
            initialPeer,
            outboundRpcType,
            inLog,
            outLog));

    return this;
  }

  private PALDirectory getPalDirectory() {
    if (directoryConnectionProvider != null) {
      return directoryConnectionProvider.get().orElse(null);
    }
    return null;
  }

  private void connectZMQSocket(PeerInfo peer) {
    peerSocket.setIdentity(("ThinPeer-" + peerUuid.toString()).getBytes(ZMQ.CHARSET));
    peerSocket.connect(peer.getRpcAddress());
    isSocketConnected = true;
  }

  private void connectWebSocket(PeerInfo peer) throws URISyntaxException, InterruptedException {
    wsClient = new WSClient(new URI(peer.getJsonrpcAddress()));
    wsClient.connectBlocking();
  }

  private void assertInitialized() {
    if (!initialized) {
      throw new IllegalStateException("ThinPeer is not initialized. Did you call init()?");
    }
  }

  public ExecMessage sendAndReceive(ExecMessage message) throws Exception {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceive: in with message: {}", ColferUtils.format(message));
    }
    if (talkingToPeer) {
      return sendToPeer(message);
    } else {
      return sendToLogAndReceive(message);
    }
  }

  public <T> CompletableFuture<JsonRpcResponse> sendAndReceive(T jsonRpc, Class<T> jsonRpcType) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceive: in with jsonRpc: {}", jsonRpc);
    }

    if (talkingToPeer) {
      return sendToPeer(jsonRpc, jsonRpcType);
    } else {
      throw new IllegalStateException(
          "Not connected to any peer. Cannot send and receive JSON-RPC messages to/from log");
    }
  }

  public Message getMessageAtOffset(Long seek) {
    return getMessageAtOffset(seek, true);
  }

  private Message getMessageAtOffset(Long seek, boolean lookupCached) {
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

    Message message = new Message();
    message.unmarshal(requestedRecord.value(), 0);
    return message;
  }

  private Message getCachedMessageAtOffset(Long offset) {
    ConsumerRecord<String, byte[]> cached = lastRecordsRead.get(offset);
    if (cached != null) {
      Message message = new Message();
      message.unmarshal(cached.value(), 0);
      return message;
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
      logger.trace("sendToLogAndForget: in with message: {}", ColferUtils.format(message));
    }
    // send to kafka
    final byte[] body = ColferUtils.toBytes(msgBuilder.wrap(message));
    producer.send(
        new ProducerRecord<>(outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body));
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Message sent to log:\n{} ({} bytes), and we're done",
          ColferUtils.format(message),
          body.length);
    }
  }

  private ExecMessage sendToLogAndReceive(ExecMessage message) throws Exception {

    if (logger.isTraceEnabled()) {
      logger.trace("sendToLogAndReceive: in with message: {}", ColferUtils.format(message));
    }
    if (!allowP2P) {
      return sendAndReceiveConsumingLog(message);
    }

    return sendToLogConsumeAndSwitchToPeer(message);
  }

  private ExecMessage sendToLogConsumeAndSwitchToPeer(ExecMessage message) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "sendToLogConsumeAndSwitchToPeer: in with message: {}", ColferUtils.format(message));
    }
    ExecMessage replyMsg = sendAndReceiveConsumingLog(message);

    // switch to direct p2p talk
    String msgPeerUuid = replyMsg.getPeerUuid();
    connectToPeer(UUID.fromString(msgPeerUuid));

    return replyMsg;
  }

  private ExecMessage sendAndReceiveConsumingLog(ExecMessage message) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceiveConsumingLog: in with message: {}", ColferUtils.format(message));
    }
    // send to kafka
    long sentRecordOffset;
    final byte[] body = ColferUtils.toBytes(msgBuilder.wrap(message));
    Future<RecordMetadata> recordMetadataFuture =
        producer.send(
            new ProducerRecord<>(
                outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body));
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      if (logger.isDebugEnabled()) {
        logger.debug("Message sent ({} bytes):\n {}", body.length, ColferUtils.format(message));
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

    // wait for reply  (should contain responseToUuid = sentRecordOffset in message)
    while (true) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (records.count() != 0 && logger.isDebugEnabled()) {
        logger.debug("Received {} records", records.count());
      }
      for (ConsumerRecord<String, byte[]> record : records) {
        final Message rcvdMsg = new Message();
        rcvdMsg.unmarshal(record.value(), 0);
        long receivedMsgOffset = record.offset();
        final ExecMessage execMessage = rcvdMsg.getExecMessage();
        final String responseToUuid = execMessage == null ? null : execMessage.getResponseToUuid();
        if (execMessage != null && message.getMessageUuid().equals(responseToUuid)) {
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

  public void connectToPeer(UUID peerUuid) throws Exception {
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

  private void connectToPeer(PeerInfo peer) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace("connectToPeer: in with peerUuid: {}", peerUuid);
    }
    if (!allowP2P) {
      throw new RuntimeException("Cannot connect to peer: p2p is disallowed");
    }

    if (currentPeer != null && isSocketConnected) {
      sendDeleteSessionRequest();
    }

    if (outboundRpcType == RPCType.RPC) {
      connectZMQSocket(peer);
    } else { // is JSON-RPC
      connectWebSocket(peer);
    }
    currentPeer = peer;
    talkingToPeer = true;
    logger.info("Now in direct talk with {}", peer);
  }

  public ExecMessage sendToPeer(ExecMessage message) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToPeer: in with message: {}", ColferUtils.format(message));
    }
    // wrap in Message and send to peer
    peerSocket.send(ColferUtils.toBytes(msgBuilder.wrap(message)));

    final long waitStart = System.currentTimeMillis();
    byte[] reply = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    final Message replyMsgWrapper = new Message();
    replyMsgWrapper.unmarshal(reply, 0);
    final ExecMessage replyMsg = replyMsgWrapper.getExecMessage();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Got reply message: {}, waited {} ms",
          ColferUtils.format(replyMsg),
          (waitEnd - waitStart));
    }

    return replyMsg;
  }

  public <T> CompletableFuture<JsonRpcResponse> sendToPeer(T jsonRpcRequest, Class<T> jsonRpcType) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToPeer: in with jsonRpcRequest: {}", jsonRpcRequest);
    }
    String rpc;
    if (jsonRpcType == JsonRpcRequest.class) {
      rpc = gson.toJson(jsonRpcRequest);
    } else if (jsonRpcType == String.class) {
      rpc = (String) jsonRpcRequest;
    } else {
      throw new IllegalArgumentException("Unsupported type for jsonRpc");
    }
    return wsClient.sendAsync(rpc);
  }

  // TODO: refactor this and above methods
  public ControlMessage sendToPeer(ControlMessage message) {
    assertInitialized();
    if (logger.isTraceEnabled()) {
      logger.trace("sendToPeer: in with message: {}", ColferUtils.format(message));
    }
    // send message request to peer
    peerSocket.send(ColferUtils.toBytes(msgBuilder.wrap(message)));

    final long waitStart = System.currentTimeMillis();
    byte[] reply = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    final Message replyMsgWrapper = new Message();
    replyMsgWrapper.unmarshal(reply, 0);
    final ControlMessage replyMsg = replyMsgWrapper.getControlMessage();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Got reply message: {}, waited {} ms",
          ColferUtils.format(replyMsg),
          (waitEnd - waitStart));
    }

    return replyMsg;
  }

  public void sendDeleteSessionRequest() {
    final String sessionId = peerUuid.toString();
    ControlMessage msg = msgBuilder.buildDeleteSessionControlMessage(peerUuid);
    ControlMessage replyMsg = sendToPeer(msg);
    ControlStatusType statusType = ControlStatusType.fromByte(replyMsg.getStatus());
    if (Objects.requireNonNull(statusType) == ControlStatusType.OK) {
      logger.info("Session w/uuid {} was deleted.", sessionId);
    } else {
      logger.error("Error deleting session w/uuid {} - status {}", sessionId, statusType.name());
    }
  }

  public void sendDeleteObjectRequest(ObjectRef objectRef) throws Exception {
    ControlMessage msg = msgBuilder.buildDeleteObjectControlMessage(peerUuid, objectRef.asString());
    ControlMessage replyMsg = sendToPeer(msg);
    ControlStatusType statusType = ControlStatusType.fromByte(replyMsg.getStatus());
    if (Objects.requireNonNull(statusType) == ControlStatusType.OK) {
      logger.info("Object w/ref {} was deleted.", objectRef);
      return;
    } else {
      throw new Exception(
          String.format(
              "Error deleting object w/ref %s - status %s", objectRef, statusType.name()));
    }
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

  @Override
  public void close() {
    assertInitialized();

    if (currentPeer != null && isSocketConnected) {
      sendDeleteSessionRequest();
    }

    // NOTE: we only close resources that were not passed to us

    // close socket-related resources
    close(peerSocket, "Peer socket closed.");
    isSocketConnected = false;
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

    // unregister self
    if (getPalDirectory() != null) {
      try {
        getPalDirectory().unregisterPeer(this.peerUuid);
      } catch (Exception e) {
        logger.error("Error unregistering self from pal directory.", e);
      }
    }

    // close directory
    if (!directoryGiven && getPalDirectory() != null) {
      getPalDirectory().close();
    }

    closed = true;
  }

  public boolean isLogIOEnabled() {
    return logIOEnabled;
  }

  public boolean isClosed() {
    return closed;
  }

  private final class WSClient extends WebSocketClient {
    private final Map<String, CompletableFuture<JsonRpcResponse>> futureResponses =
        new ConcurrentHashMap<>();

    WSClient(URI uri) {
      super(uri);
    }

    public void send(String message) {
      if (logger.isTraceEnabled()) {
        logger.trace("sending message to ws socket: {}", message);
      }
      super.send(message);
    }

    public CompletableFuture<JsonRpcResponse> sendAsync(String message) {
      if (logger.isTraceEnabled()) {
        logger.trace("sending message to ws socket: {}", message);
      }
      JsonRpcRequest request = gson.fromJson(message, JsonRpcRequest.class);
      CompletableFuture<JsonRpcResponse> futureResponse = new CompletableFuture<>();
      futureResponses.put(request.getId(), futureResponse);
      send(message);
      return futureResponse;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      logger.info("WebSocket connection opened");
    }

    @Override
    public void onMessage(String message) {
      logger.info("Received message: {}", message);
      JsonRpcResponse response = gson.fromJson(message, JsonRpcResponse.class);
      CompletableFuture<JsonRpcResponse> futureResponse = futureResponses.get(response.getId());
      if (futureResponse == null) {
        logger.error("No future response found for message id: {}", response.getId());
      } else {
        futureResponse.complete(response);
        futureResponses.remove(response.getId());
      }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      logger.info("WebSocket connection closed");
    }

    @Override
    public void onError(Exception ex) {
      logger.error("WebSocket error", ex);
    }
  }
}
