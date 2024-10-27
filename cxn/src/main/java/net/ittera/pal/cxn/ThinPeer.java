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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.LogMessage;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.JsonRpcType;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.messages.types.RpcType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.JsonSerializers;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/** This class is not thread-safe. For multithreaded scenarios, use different instances. */
public class ThinPeer implements AutoCloseable {

  private UUID peerUuid;
  private String peerName;
  private boolean allowP2P = true;
  private boolean isZmqSocketConnected = false;
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

  private Map<Long, ConsumerRecord<String, byte[]>> lastRecordsRead = new HashMap<>();
  private ExecutorService asyncConsumerExecutor;

  // rpc stuff
  private ZContext zmqContext;
  private Socket peerSocket;
  private WsClient wsClient;
  private String rpcAddress;
  private Gson gson;
  private RpcType outboundRpcType = RpcType.RPC;
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

  public ThinPeer withUuid(UUID uuid) {
    this.peerUuid = uuid;
    return this;
  }

  public ThinPeer withName(String name) {
    this.peerName = name;
    return this;
  }

  public ThinPeer withRpcAddress(String rpcAddress) {
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

  public ThinPeer withZmqContext(ZContext zmqContext) {
    this.zmqContext = zmqContext;
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

  public ThinPeer withDirectoryUrl(String palDirectoryUrl) {
    this.palDirectoryUrl = palDirectoryUrl;
    return this;
  }

  public ThinPeer withSelfRegistration(boolean registerSelf) {
    this.registerSelf = registerSelf;
    return this;
  }

  public ThinPeer withOutboundRpcType(RpcType rpcType) {
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
        this.palDirectoryUrl = PalDirectory.NO_URL;
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
      // get last log with prefix from PAL directory
      String kafkaTopicPrefix = logPrefix != null ? logPrefix : DEFAULT_TOPIC_PREFIX;
      LogInfo lastLog =
          getPalDirectory() != null
              ? getPalDirectory().getLastLogWithPrefix(kafkaTopicPrefix)
              : null;

      // configure log(s) to connect to; fill bootstrap servers if only log names given
      if (this.inLog == null) {
        if (lastLog == null) {
          throw new RuntimeException("Could not get last Log with prefix from PAL directory");
        }
        this.inLog = lastLog;
      } else {
        if (this.inLog.getBootstrapServers() == null && bootstrapServers != null) {
          this.inLog.setBootstrapServers(bootstrapServers);
        }
      }

      if (outLog == null) {
        if (lastLog == null) {
          throw new RuntimeException("Could not get last Log with prefix from PAL directory");
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
      if (outboundRpcType == RpcType.RPC) {
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
              "Cannot connect to peer without its UUID or "
                  + "listening (i.e. RPC) address. Peer -> "
                  + initialPeer);
        }
      }
    }

    // initialize gson, registering custom adapters for JSON-RPC Response messages
    this.gson =
        new GsonBuilder()
            .registerTypeAdapter(
                StaticFieldPutDone.class, new JsonSerializers.StaticFieldPutDoneAdapter())
            .registerTypeAdapter(
                InstanceFieldPutDone.class, new JsonSerializers.InstanceFieldPutDoneAdapter())
            .registerTypeAdapter(ReturnValue.class, new JsonSerializers.ReturnValueAdapter())
            .registerTypeAdapter(JsonRpcResponse.class, new JsonRpcResponseDeserializer())
            .create();

    initialized = true;
    logger.info(
        """
        Initialized ThinPeer with:
        uuid: {},
        name: {},
        rpcAddress: {},
        directory: {},
        initialPeer: {},
        rpcType: {},
        inLog: {},
        outLog: {}
        """,
        peerUuid,
        peerName,
        rpcAddress,
        palDirectoryUrl,
        initialPeer,
        outboundRpcType,
        inLog,
        outLog);
    return this;
  }

  private PalDirectory getPalDirectory() {
    if (directoryConnectionProvider != null) {
      return directoryConnectionProvider.get().orElse(null);
    }
    return null;
  }

  private void connectZmqSocket(PeerInfo peer) {
    peerSocket.setIdentity(("ThinPeer-" + peerUuid.toString()).getBytes(ZMQ.CHARSET));
    peerSocket.connect(peer.getRpcAddress());
    isZmqSocketConnected = true;
  }

  private void connectWebSocket(PeerInfo peer) throws URISyntaxException, InterruptedException {
    wsClient = new WsClient(new URI(peer.getJsonrpcAddress()));
    wsClient.connectBlocking();
  }

  private void assertInitializedAndActive() {
    if (!initialized) {
      throw new IllegalStateException("ThinPeer is not initialized. Did you call init()?");
    }
    if (closed) {
      throw new IllegalStateException("ThinPeer is closed. Cannot perform operations.");
    }
  }

  public ExecMessage sendAndReceive(ExecMessage message) throws Exception {
    assertInitializedAndActive();
    if (logger.isTraceEnabled()) {
      logger.trace(
          "sendAndReceiveJsonRpcRequest: in with message: {}", ColferUtils.format(message));
    }
    if (talkingToPeer) {
      return sendToPeer(message);
    } else {
      return sendToLogAndReceive(message);
    }
  }

  /**
   * Send a message to the peer via websocket.
   *
   * @param jsonRpc the JSON-RPC request as a String or instance of JsonRpcRequest.
   * @return a CompletableFuture that will be completed with the response
   */
  public CompletableFuture<JsonRpcResponse> sendJsonRpcRequestToPeer(Object jsonRpc) {
    assertInitializedAndActive();
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceiveJsonRpcRequest: in with jsonRpc: {}", jsonRpc);
    }

    if (talkingToPeer) {
      String rpcMessage;
      if (jsonRpc instanceof JsonRpcRequest) {
        rpcMessage = gson.toJson(jsonRpc);
      } else if (jsonRpc instanceof String) {
        rpcMessage = (String) jsonRpc;
      } else {
        throw new IllegalArgumentException("Unsupported type for jsonRpc");
      }
      return wsClient.sendAsync(rpcMessage);
    } else {
      throw new IllegalStateException(
          "Not connected to any peer. Cannot send and receive JSON-RPC messages to/from log");
    }
  }

  @SuppressWarnings("unused")
  public LogMessage<?> getMessageAtOffset(Long seek) {
    return getMessageAtOffset(seek, true);
  }

  private LogMessage<?> getMessageAtOffset(Long seek, boolean lookupCached) {
    assertInitializedAndActive();
    if (logger.isDebugEnabled()) {
      logger.debug("Getting message @ offset #{}, lookupCached = {}", seek, lookupCached);
    }
    if (lookupCached) {
      LogMessage<?> cachedMsg = getCachedMessageAtOffset(seek);
      if (cachedMsg != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Got cached record at offset {}", seek);
        }
        return cachedMsg;
      }
    }

    Map<Long, ConsumerRecord<String, byte[]>> recordsRead = new HashMap<>();
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
      for (ConsumerRecord<String, byte[]> record : records) {
        if (seek == record.offset()) {
          requestedRecord = record;
        }
        recordsRead.put(record.offset(), record);
      }
    }
    // now swap last batch (map) of records read with the new one
    this.lastRecordsRead = recordsRead;

    return createLogMessage(requestedRecord);
  }

  private LogMessage<?> createLogMessage(ConsumerRecord<String, byte[]> record) {
    byte[] value = record.value();
    MessageFormatType messageFormat = getMessageFormatFromHeader(record.headers());
    if (messageFormat == null) {
      throw new IllegalArgumentException("Message format not found in record headers");
    }
    LogMessage<?> logMessage;
    Map<String, String> headers = new HashMap<>();
    String messageType = getMessageTypeFromHeader(record.headers());
    if (messageType != null) {
      headers.put("message-type", messageType);
    }

    switch (messageFormat) {
      case COLFER -> {
        Message message = new Message();
        message.unmarshal(value, 0);
        logMessage = new LogMessage<>(record.offset(), headers, message);
      }
      case JSONRPC -> {
        String json = new String(value, StandardCharsets.UTF_8);
        if (JsonRpcType.REQUEST.name().equals(messageType)) {
          JsonRpcRequest jsonRpcRequest = gson.fromJson(json, JsonRpcRequest.class);
          logMessage = new LogMessage<>(record.offset(), headers, jsonRpcRequest);
        } else if (JsonRpcType.RESPONSE.name().equals(messageType)) {
          JsonRpcResponse jsonRpcResponse = gson.fromJson(json, JsonRpcResponse.class);
          logMessage = new LogMessage<>(record.offset(), headers, jsonRpcResponse);
        } else {
          throw new IllegalArgumentException("Unsupported JSON-RPC message type: " + messageType);
        }
      }
      default -> throw new IllegalArgumentException("Unsupported message format: " + messageFormat);
    }

    return logMessage;
  }

  private MessageFormatType getMessageFormatFromHeader(Headers headers) {
    for (Header header : headers.headers("message-format")) {
      byte formatByte = header.value()[0];
      return MessageFormatType.fromByte(formatByte);
    }
    return null;
  }

  private String getMessageTypeFromHeader(Headers headers) {
    for (Header header : headers.headers("message-type")) {
      return new String(header.value(), StandardCharsets.UTF_8);
    }
    return null;
  }

  private LogMessage<?> getCachedMessageAtOffset(Long offset) {
    ConsumerRecord<String, byte[]> cached = lastRecordsRead.get(offset);
    if (cached != null) {
      return createLogMessage(cached);
    }
    return null;
  }

  @SuppressWarnings("unused")
  public List<ConsumerRecord<?, ?>> getMessages(long startOffset, long numMessages) {
    assertInitializedAndActive();
    if (logger.isDebugEnabled()) {
      logger.debug("Getting {} messages starting @ offset #{}", numMessages, startOffset);
    }
    consumer.seek(inTopicPartition, startOffset);
    List<ConsumerRecord<?, ?>> messages = new ArrayList<>();
    boolean gotAllMessages = false;

    while (!gotAllMessages) {
      ConsumerRecords<String, byte[]> records = consumer.poll(pollingDuration);
      if (logger.isDebugEnabled()) {
        logger.debug("got {} records after poll", records.count());
      }
      for (var record : records) {
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

  public Future<RecordMetadata> sendExecMessageToLog(ExecMessage message) {
    assertInitializedAndActive();
    if (logger.isTraceEnabled()) {
      logger.trace("sendExecMessageToLog: in with message: {}", ColferUtils.format(message));
    }

    // create kafka record
    final byte[] body = ColferUtils.toBytes(msgBuilder.wrap(message));
    final ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body);
    record.headers().add("message-format", new byte[] {MessageFormatType.COLFER.toByte()});
    record
        .headers()
        .add("message-type", MessageType.EXEC_MESSAGE.name().getBytes(StandardCharsets.UTF_8));
    record.headers().add("producer", peerUuid.toString().getBytes(StandardCharsets.UTF_8));

    // send and return future
    var sendFuture = producer.send(record);
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{} ({} bytes)", ColferUtils.format(message), body.length);
    }
    return sendFuture;
  }

  @SuppressWarnings("unused")
  public Future<RecordMetadata> sendJsonRpcRequestToLog(Object jsonRpcRequest) {
    assertInitializedAndActive();
    if (logger.isTraceEnabled()) {
      logger.trace("sendJsonRpcRequestToLog: in with jsonRpcRequest: {}", jsonRpcRequest);
    }
    String rpcMessage;
    if (jsonRpcRequest instanceof JsonRpcRequest) {
      rpcMessage = gson.toJson(jsonRpcRequest);
    } else if (jsonRpcRequest instanceof String) {
      rpcMessage = (String) jsonRpcRequest;
    } else {
      throw new IllegalArgumentException("Unsupported type for jsonRpc");
    }

    // create kafka record
    final byte[] body = rpcMessage.getBytes(StandardCharsets.UTF_8);
    final ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(
            outLog.getName(), PRODUCER_PARTITION, UUID.randomUUID().toString(), body);
    record.headers().add("message-format", new byte[] {MessageFormatType.JSONRPC.toByte()});
    record
        .headers()
        .add("message-type", JsonRpcType.REQUEST.name().getBytes(StandardCharsets.UTF_8));
    record.headers().add("producer", peerUuid.toString().getBytes(StandardCharsets.UTF_8));

    // send and return future
    var sendFuture = producer.send(record);
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{} ({} bytes)", rpcMessage, body.length);
    }
    return sendFuture;
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

    if (replyMsg != null) {
      // switch to direct p2p talk
      String msgPeerUuid = replyMsg.getPeerUuid();
      connectToPeer(UUID.fromString(msgPeerUuid));
    }

    return replyMsg;
  }

  private ExecMessage sendAndReceiveConsumingLog(ExecMessage message) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace("sendAndReceiveConsumingLog: in with message: {}", ColferUtils.format(message));
    }
    // send to kafka
    long sentRecordOffset;
    final byte[] body = ColferUtils.toBytes(msgBuilder.wrap(message));
    final ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(outLog.getName(), PRODUCER_PARTITION, message.getMessageUuid(), body);
    newRecord.headers().add("message-format", new byte[] {MessageFormatType.COLFER.toByte()});
    newRecord
        .headers()
        .add("message-type", MessageType.EXEC_MESSAGE.name().getBytes(StandardCharsets.UTF_8));
    newRecord.headers().add("producer", peerUuid.toString().getBytes(StandardCharsets.UTF_8));
    Future<RecordMetadata> recordMetadataFuture = producer.send(newRecord);
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
        final Message receivedMessage = new Message();
        receivedMessage.unmarshal(record.value(), 0);
        long receivedMsgOffset = record.offset();
        final ExecMessage execMessage = receivedMessage.getExecMessage();
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
              if (getPalDirectory() != null) {
                newPeer = getPalDirectory().getPeerInfo(msgPeerUuid);
              }
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
    if (getPalDirectory() == null) {
      throw new RuntimeException("Cannot connect to peer without PAL directory");
    }
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

    if (currentPeer != null && isZmqSocketConnected) {
      sendDeleteSessionRequest();
    }

    if (outboundRpcType == RpcType.RPC) {
      connectZmqSocket(peer);
    } else { // is JSON-RPC
      connectWebSocket(peer);
    }
    currentPeer = peer;
    talkingToPeer = true;
    logger.info("Now in direct talk with {}", peer);
  }

  public ExecMessage sendToPeer(ExecMessage message) {
    assertInitializedAndActive();
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
          "Got reply message to Exec message: {}, waited {} ms",
          ColferUtils.format(replyMsg),
          (waitEnd - waitStart));
    }

    return replyMsg;
  }

  public ControlMessage sendToPeer(ControlMessage message) {
    assertInitializedAndActive();
    if (logger.isTraceEnabled()) {
      logger.trace("in sendToPeer with Control message: {}", ColferUtils.format(message));
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
          "Got reply to Control message: {}, waited {} ms",
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
    } else {
      throw new Exception(
          String.format(
              "Error deleting object w/ref %s - status %s", objectRef, statusType.name()));
    }
  }

  private void closeConsumer() {
    if (consumer != null) {
      try {
        consumer.close(Duration.of(500, ChronoUnit.MILLIS));
        logger.info("Log consumer closed.");
      } catch (Exception e) {
        logger.warn("Error closing consumer", e);
      }
    }
  }

  private void closeProducer() {
    if (producer != null) {
      try {
        producer.close(Duration.of(500, ChronoUnit.MILLIS));
        logger.info("Log producer closed.");
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
  }

  private void closePeerSocket() {
    try {
      if (peerSocket != null) {
        peerSocket.close();
        logger.info("Peer socket closed.");
      }
    } catch (Exception e) {
      logger.warn("Error closing peer zmq socket", e);
    }

    try {
      if (wsClient != null) {
        wsClient.close();
        logger.info("WebSocket client closed.");
      }
    } catch (Exception e) {
      logger.warn("Error closing peer websocket", e);
    }
  }

  @Override
  public void close() {
    assertInitializedAndActive();

    if (currentPeer != null && isZmqSocketConnected) {
      sendDeleteSessionRequest();
    }

    // NOTE: we only close resources that were not passed to us

    // close socket-related resources
    closePeerSocket();
    isZmqSocketConnected = false;
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
      closeProducer();
    }
    if (!consumerGiven) {
      closeConsumer();
    }
    if (asyncConsumerExecutor != null) {
      asyncConsumerExecutor.shutdown();
      logger.info("Consumer executor service shut down");
    }

    // unregister self
    if (getPalDirectory() != null && registerSelf) {
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

  // <editor-fold desc="Getters">
  public boolean isLogIOEnabled() {
    return logIOEnabled;
  }

  public boolean isClosed() {
    return closed;
  }

  public boolean isInitialized() {
    return initialized;
  }

  public String getName() {
    return peerName;
  }

  public boolean isP2PEnabled() {
    return allowP2P;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public LogInfo getInLog() {
    return inLog;
  }

  public LogInfo getOutLog() {
    return outLog;
  }

  public Duration getPollingDuration() {
    return pollingDuration;
  }

  public Producer<String, byte[]> getProducer() {
    return producer;
  }

  public Consumer<String, byte[]> getConsumer() {
    return consumer;
  }

  public Properties getProducerProperties() {
    return producerProperties;
  }

  public Properties getConsumerProperties() {
    return consumerProperties;
  }

  public ZContext getZmqContext() {
    return zmqContext;
  }

  public String getRpcAddress() {
    return rpcAddress;
  }

  public RpcType getOutboundRpcType() {
    return outboundRpcType;
  }

  public PeerInfo getInitialPeer() {
    return initialPeer;
  }

  public PeerInfo getCurrentPeer() {
    return currentPeer;
  }

  public boolean isTalkingToPeer() {
    return talkingToPeer;
  }

  public String getPalDirectoryUrl() {
    return palDirectoryUrl;
  }

  public String getLogPrefix() {
    return logPrefix;
  }

  public boolean isSelfRegistering() {
    return registerSelf;
  }

  public boolean isZmqSocketConnected() {
    return isZmqSocketConnected;
  }

  // </editor-fold>

  private final class WsClient extends WebSocketClient {
    private final Map<String, CompletableFuture<JsonRpcResponse>> futureResponses =
        new ConcurrentHashMap<>();

    WsClient(URI uri) {
      super(uri);
    }

    @Override
    public void send(String message) {
      if (logger.isTraceEnabled()) {
        logger.trace("sending message to ws socket: {}", message);
      }
      super.send(message);
    }

    public CompletableFuture<JsonRpcResponse> sendAsync(String message) {
      if (logger.isTraceEnabled()) {
        logger.trace("in sendAsync - sending message to ws socket: {}", message);
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
