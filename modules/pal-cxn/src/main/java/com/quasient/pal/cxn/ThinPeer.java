/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.cxn.directory.PeerLease;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.types.ControlCommandType;
import com.quasient.pal.messages.types.ControlStatusType;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * Represents a versatile, lightweight peer (or client) in the Pal runtime, managing communication
 * with remote peers via ZeroMQ and WebSockets, and Kafka. Allows both synchronous and asynchronous
 * operations. This class is not thread-safe; separate instances should be used in multithreaded
 * environments.
 *
 * <p>Use {@link #init()} to initialize the peer before using it, and {@link #close()} to release
 * resources. Configure the peer using the provided builder methods before initialization.
 */
public class ThinPeer implements AutoCloseable {
  /** Logger instance for ThinPeer class. */
  private static final Logger logger = LoggerFactory.getLogger(ThinPeer.class);

  /** Default PING timeout duration. */
  private static final Duration PING_TIMEOUT = Duration.ofSeconds(5);

  /** Default value, in seconds, for this peer's keep-alive. */
  private static final long PEER_KA_SECS = 60;

  /** Universally unique identifier for this peer instance. */
  private UUID peerUuid;

  /** Human-readable name for this peer. */
  private String peerName;

  /** Lease for maintaining this peer's state liveness in the pal Directory. */
  private PeerLease peerLease;

  /** Indicates whether the ZeroMQ socket is currently connected. */
  private boolean isZmqSocketConnected = false;

  /** Indicates whether the WebSocket client is currently connected. */
  private boolean isWsClientConnected = false;

  /** Indicates whether this ThinPeer instance has been closed. */
  private boolean closed;

  /** Indicates whether this ThinPeer instance has been initialized. */
  private boolean initialized;

  /** Builder utility for constructing various message types. */
  private final MessageBuilder msgBuilder = new MessageBuilder();

  /** Kafka bootstrap servers for connecting to the cluster. */
  private String bootstrapServers;

  /** Flag indicating whether log input/output is enabled. */
  private boolean logIOEnabled;

  /** LogInfo for the input Log. */
  private LogInfo inputLog;

  /** LogInfo for the output Log. */
  private LogInfo outputLog;

  /** Kafka topic partition for input Log messages. */
  private TopicPartition inTopicPartition;

  /** Duration for polling Kafka consumer. */
  private Duration pollingDuration;

  /** Number of preceding records to fetch. */
  private static final int PRECEDING_RECS = 50;

  /** Default partition for producer messages. */
  private static final int PRODUCER_PARTITION = 0;

  /** Default polling duration in milliseconds. */
  private static final int DEFAULT_POLLING_DURATION_MILLIS = 10;

  /** Prefix used for Kafka log topics. */
  private String logPrefix;

  /** Default prefix for Kafka topics. */
  public static final String DEFAULT_TOPIC_PREFIX = "app";

  /** Kafka producer for sending log messages. */
  private Producer<String, LogMessage<?>> producer;

  /** Kafka consumer for receiving log messages. */
  private Consumer<String, LogMessage<?>> consumer;

  /** Lock object for synchronizing access to the consumer. */
  private final Object consumerLock = new Object();

  /** Flag indicating whether a producer was provided externally. */
  private boolean producerGiven;

  /** Flag indicating whether producing is enabled. */
  private boolean producing;

  /** Flag indicating whether a consumer was provided externally. */
  private boolean consumerGiven;

  /** Flag indicating whether consuming is enabled. */
  private boolean consuming;

  /** Configuration properties for the Kafka producer. */
  private Properties producerProperties;

  /** Configuration properties for the Kafka consumer. */
  private Properties consumerProperties;

  /** Cache of the last records read from Kafka, mapped by their offsets. */
  private Map<Long, ConsumerRecord<String, LogMessage<?>>> lastRecordsRead = new HashMap<>();

  /** ZeroMQ context for managing ZMQ sockets. */
  private ZContext zmqContext;

  /** ZeroMQ socket for peer communication. */
  private Socket peerSocket;

  /** WebSocket client for JSON-RPC communication. */
  private WsClient wsClient;

  /**
   * Timeout in secs for lost connections. A value lower or equal to 0 results in the check to be
   * deactivated .
   */
  private Integer websocketConnectionLostTimeout;

  /** RPC address for this peer. */
  private String rpcAddress;

  /** Type of outbound RPC mechanism used (binary or JSON). */
  private RpcType outboundRpcType = RpcType.BIN_RPC;

  /** PeerInfo of initial peer to talk to. */
  private PeerInfo initialPeer;

  /** Currently connected peer info. */
  private PeerInfo currentPeer;

  /** Indicates whether this peer is currently communicating with another peer. */
  private boolean talkingToPeer;

  /** Flags if a ZMQ context was provided externally. */
  private boolean zmqContextGiven;

  /** Provider for directory connections to the PAL directory service. */
  private DirectoryConnectionProvider directoryConnectionProvider;

  /** Flag indicating whether a directory connection provider was provided externally. */
  private boolean directoryGiven;

  /** URL of the PAL directory. */
  private String palDirectoryUrl;

  /** Flag indicating whether this peer should register itself with the PAL directory. */
  private boolean registerSelf = false;

  /**
   * Constructs a new ThinPeer instance with default settings.
   *
   * <p>Note: Consider using factory methods for better configuration.
   */
  public ThinPeer() {
    // TODO use factory method instead of empty constructor
  }

  /**
   * Sets the UUID for this peer.
   *
   * @param uuid the universally unique identifier to assign to this peer
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withUuid(UUID uuid) {
    this.peerUuid = uuid;
    return this;
  }

  /**
   * Sets the human-readable name for this peer.
   *
   * @param name the name to assign to this peer
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withName(String name) {
    this.peerName = name;
    return this;
  }

  /**
   * Sets the RPC address for this peer.
   *
   * @param rpcAddress the RPC address to assign to this peer
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withRpcAddress(String rpcAddress) {
    this.rpcAddress = rpcAddress;
    return this;
  }

  /**
   * Configures the Kafka bootstrap servers.
   *
   * @param bootstrapServers the Kafka bootstrap servers to use
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
    return this;
  }

  /**
   * Sets both the input and output log information.
   *
   * @param log the LogInfo instance to assign to both input and output logs
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withLog(LogInfo log) {
    this.inputLog = log;
    this.outputLog = log;
    return this;
  }

  /**
   * Sets the input log information.
   *
   * @param inputLog the LogInfo instance to assign to the input log
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withInputLog(LogInfo inputLog) {
    this.inputLog = inputLog;
    return this;
  }

  /**
   * Sets the output log information.
   *
   * @param outputLog the LogInfo instance to assign to the output log
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withOutputLog(LogInfo outputLog) {
    this.outputLog = outputLog;
    return this;
  }

  /**
   * Sets the prefix used for Kafka log topics.
   *
   * @param logPrefix the prefix to assign to log topics
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
    return this;
  }

  /**
   * Assigns an external Kafka consumer.
   *
   * @param consumer the Kafka Consumer to use for log consumption
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withConsumer(Consumer<String, LogMessage<?>> consumer) {
    this.consumer = consumer;
    this.consumerGiven = true;
    return this;
  }

  /**
   * Sets the configuration properties for the Kafka consumer.
   *
   * @param properties the Properties object containing consumer configurations
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withConsumerProperties(Properties properties) {
    this.consumerProperties = properties;
    return this;
  }

  /**
   * Configures the polling duration for Kafka consumer operations.
   *
   * @param millis the duration in milliseconds for each poll interval
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withPollingDuration(long millis) {
    this.pollingDuration = Duration.of(millis, ChronoUnit.MILLIS);
    return this;
  }

  /**
   * Assigns an external Kafka producer.
   *
   * @param producer the Kafka Producer to use for log production
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withProducer(Producer<String, LogMessage<?>> producer) {
    this.producer = producer;
    this.producerGiven = true;
    return this;
  }

  /**
   * Sets the configuration properties for the Kafka producer.
   *
   * @param properties the Properties object containing producer configurations
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withProducerProperties(Properties properties) {
    this.producerProperties = properties;
    return this;
  }

  /**
   * Sets the ConnectionLostTimeout (in seconds) for the Websocket Client.
   *
   * @param wsConnectionLostTimeout the interval in seconds. A value lower or equal to 0 results in
   *     the check to be deactivated.
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withWebsocketConnectionLostTimeout(Integer wsConnectionLostTimeout) {
    this.websocketConnectionLostTimeout = wsConnectionLostTimeout;
    return this;
  }

  /**
   * Assigns an external ZeroMQ context.
   *
   * @param zmqContext the ZContext to use for ZeroMQ operations
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withZmqContext(ZContext zmqContext) {
    this.zmqContext = zmqContext;
    this.zmqContextGiven = true;
    return this;
  }

  /**
   * Sets the initial peer information for establishing a connection.
   *
   * @param initialPeer the PeerInfo object representing the initial peer to connect to
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withInitialPeer(PeerInfo initialPeer) {
    this.initialPeer = initialPeer;
    return this;
  }

  /**
   * Sets the directory connection provider for interacting with the PAL directory service.
   *
   * @param directoryConnectionProvider the DirectoryConnectionProvider to use
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withDirectoryProvider(DirectoryConnectionProvider directoryConnectionProvider) {
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.directoryGiven = true;
    return this;
  }

  /**
   * Sets the URL for the PAL directory service.
   *
   * @param palDirectoryUrl the URL to assign to the PAL directory
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withDirectoryUrl(String palDirectoryUrl) {
    this.palDirectoryUrl = palDirectoryUrl;
    return this;
  }

  /**
   * Enables or disables self-registration with the PAL directory.
   *
   * @param registerSelf true to register this peer with the PAL directory upon initialization;
   *     false otherwise
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withSelfRegistration(boolean registerSelf) {
    this.registerSelf = registerSelf;
    return this;
  }

  /**
   * Sets the type of outbound RPC mechanism to use.
   *
   * @param rpcType the RpcType to set for outbound RPC
   * @return the current ThinPeer instance for method chaining
   */
  public ThinPeer withOutboundRpcType(RpcType rpcType) {
    this.outboundRpcType = rpcType;
    return this;
  }

  /**
   * Initializes the ThinPeer instance, setting up connections to the PAL directory, configuring
   * Kafka producers and consumers, and establishing RPC connections to the initial peer if
   * provided.
   *
   * <p>This method must be called before using the ThinPeer.
   *
   * @return the initialized ThinPeer instance
   * @throws Exception if initialization fails due to invalid configurations or connection issues
   */
  public ThinPeer init() throws Exception {
    // if not given, create random UUID for this peer
    if (this.peerUuid == null) {
      this.peerUuid = UUID.randomUUID();
    }

    if (palDirectoryUrl != null && directoryConnectionProvider != null) {
      throw new IllegalArgumentException(
          "ThinPeer can use a PAL directory address or a connection provider, but not both");
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
    if (registerSelf) {
      if (getPalDirectory() == null) {
        throw new IllegalArgumentException("Cannot register peer without PAL directory");
      }
      try {
        final PeerInfo self = new PeerInfo(peerUuid);
        if (this.peerName != null) {
          self.setName(peerName);
        }
        if (this.rpcAddress != null) {
          self.setZmqRpcAddress(rpcAddress);
        }
        getPalDirectory().createPeer(self);
        peerLease = getPalDirectory().createPeerLease(self.getUuid(), PEER_KA_SECS);
      } catch (Exception ex) {
        logger.error("Error registering peer", ex);
      }
    }

    final boolean withProducer = producer != null || producerProperties != null;
    final boolean withConsumer = consumer != null || consumerProperties != null;
    final boolean logless = !withProducer && !withConsumer;

    if (!logless) {
      // get last log with prefix from PAL directory
      LogInfo lastLog =
          getPalDirectory() != null
              ? getPalDirectory()
                  .getLatestLogWithPrefix(logPrefix != null ? logPrefix : DEFAULT_TOPIC_PREFIX)
              : null;

      // configure log to read from; fill bootstrap servers if only log name given
      if (withConsumer) {
        if (this.inputLog == null) {
          if (lastLog == null) {
            throw new RuntimeException("Could not get last Log with prefix from PAL directory");
          }
          this.inputLog = lastLog;
        } else {
          if (this.inputLog.getBootstrapServers() == null && bootstrapServers != null) {
            this.inputLog.setBootstrapServers(bootstrapServers);
          }
        }
        // configure kafka consumer
        if (consumer == null) {
          if (consumerProperties == null) {
            throw new RuntimeException("You must supply either Consumer or ConsumerProperties");
          }
          consumerProperties.put("group.id", peerUuid.toString());
          final String bootstrapServers = this.inputLog.getBootstrapServers();
          consumerProperties.put("bootstrap.servers", bootstrapServers);
          this.consumer = new KafkaConsumer<>(consumerProperties);
          logger.info(
              "Kafka consumer initialized. Will connect to bootstrap servers: {}",
              bootstrapServers);
        }

        // set polling duration
        if (pollingDuration == null) {
          pollingDuration = Duration.of(DEFAULT_POLLING_DURATION_MILLIS, ChronoUnit.MILLIS);
        }

        // manual assignment of partition so we can control offset seek
        inTopicPartition = new TopicPartition(this.inputLog.getName(), 0);
        consumer.assign(Collections.singletonList(inTopicPartition));

        consuming = true;
        logger.info("Will read from log: {}", this.inputLog);
      }

      if (withProducer) {
        // configure log to write to; fill bootstrap servers if only log name given
        if (outputLog == null) {
          if (lastLog == null) {
            throw new RuntimeException("Could not get last Log with prefix from PAL directory");
          }
          this.outputLog = lastLog;
        } else {
          if (this.outputLog.getBootstrapServers() == null && bootstrapServers != null) {
            this.outputLog.setBootstrapServers(bootstrapServers);
          }
        }

        // configure kafka producer
        if (producer == null) {
          if (producerProperties == null) {
            throw new RuntimeException("You must supply either Producer or ProducerProperties");
          }
          producerProperties.put("client.id", peerUuid.toString());
          final String bootstrapServers = this.outputLog.getBootstrapServers();
          producerProperties.put("bootstrap.servers", bootstrapServers);
          this.producer = new KafkaProducer<>(producerProperties);
          logger.info(
              "Kafka producer initialized. Will connect to bootstrap servers: {}",
              bootstrapServers);
        }

        producing = true;
        logger.info("Will write to log: {}", this.outputLog);
      }

      logIOEnabled = true;
    }

    // configure RPC and connect to initial peer if given
    if (outboundRpcType == RpcType.BIN_RPC) {
      if (zmqContextGiven) {
        logger.info("Using given ZMQ context");
      } else {
        logger.info("Initializing zmq context");
        this.zmqContext = new ZContext();
      }
      this.peerSocket = zmqContext.createSocket(SocketType.REQ);
    }
    if (initialPeer != null) {
      if (initialPeer.getZmqRpcAddress() != null || initialPeer.getJsonrpcAddress() != null) {
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
        inputLog: {},
        outputLog: {}
        """,
        peerUuid,
        peerName,
        rpcAddress,
        palDirectoryUrl,
        initialPeer,
        outboundRpcType,
        inputLog,
        outputLog);
    return this;
  }

  /**
   * Retrieves the PAL directory connection.
   *
   * @return the PalDirectory instance if available; otherwise, null
   */
  private PalDirectory getPalDirectory() {
    if (directoryConnectionProvider != null) {
      return directoryConnectionProvider.get().orElse(null);
    }
    return null;
  }

  /**
   * Establishes a ZeroMQ socket connection to the specified peer.
   *
   * @param peer the PeerInfo object representing the peer to connect to
   */
  private void connectZmqSocket(PeerInfo peer) {
    byte[] prefixBytes = "ThinPeer-".getBytes(ZMQ.CHARSET);
    byte[] uuidBytes = UuidUtils.toBytes(peerUuid);
    byte[] identityBytes =
        ByteBuffer.allocate(prefixBytes.length + uuidBytes.length)
            .put(prefixBytes)
            .put(uuidBytes)
            .array();

    peerSocket.setIdentity(identityBytes);
    peerSocket.connect(peer.getZmqRpcAddress());
    isZmqSocketConnected = true;
    currentPeer = peer;
    talkingToPeer = true;
    if (logger.isDebugEnabled()) {
      logger.debug("ZMQ socket connected to peer: {}", peer.getUuid());
    }
  }

  /**
   * Establishes a ZeroMQ socket connection to the specified peer, and sends a Ping command, waiting
   * for the reply within the specified timeout
   *
   * @param peer the PeerInfo object representing the peer to connect to
   * @param timeout the timeout duration
   * @return Returns true if the connection succeeded within the specified timeout.
   */
  private boolean connectZmqSocketWithTimeout(PeerInfo peer, Duration timeout) {

    // we connect normally - in ZMQ this is an asynchronous operation and does not
    // guarantee that we are indeed paired
    connectZmqSocket(peer);

    // so we send a ping and wait for the response
    boolean pingResponded = false;
    try {
      pingResponded = sendPing(timeout) != -1;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      // reset socket if we got no response to our ping
      if (!pingResponded) {
        closePeerZMQSocket();
      }
    }

    return pingResponded;
  }

  /**
   * Establishes a WebSocket connection to the specified peer.
   *
   * @param peer the PeerInfo object representing the peer to connect to
   * @throws URISyntaxException if the peer's JSON-RPC address URI is invalid
   * @throws InterruptedException if the connection attempt is interrupted
   */
  private void connectWebSocket(PeerInfo peer) throws URISyntaxException, InterruptedException {
    Map<String, String> headers = Map.of("peer-id", peerUuid.toString());
    wsClient = new WsClient(new URI(peer.getJsonrpcAddress()), headers);
    if (websocketConnectionLostTimeout != null) {
      wsClient.setConnectionLostTimeout(websocketConnectionLostTimeout);
    }
    wsClient.connectBlocking();
    isWsClientConnected = true;
    currentPeer = peer;
    talkingToPeer = true;
  }

  /**
   * Establishes a WebSocket connection to the specified peer, with a connect timeout.
   *
   * @param peer the PeerInfo object representing the peer to connect to
   * @param timeout the time to wait before timing out
   * @return Returns whether the connection succeeded or not
   * @throws URISyntaxException if the peer's JSON-RPC address URI is invalid
   * @throws InterruptedException if the connection attempt is interrupted
   */
  private boolean connectWebSocketWithTimeout(PeerInfo peer, Duration timeout)
      throws URISyntaxException, InterruptedException {
    Map<String, String> headers = Map.of("peer-id", peerUuid.toString());
    wsClient = new WsClient(new URI(peer.getJsonrpcAddress()), headers);
    if (websocketConnectionLostTimeout != null) {
      wsClient.setConnectionLostTimeout(websocketConnectionLostTimeout);
    }
    boolean succeeded = wsClient.connectBlocking(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (succeeded) {
      isWsClientConnected = true;
    }
    currentPeer = peer;
    talkingToPeer = true;
    return succeeded;
  }

  /**
   * Ensures that the ThinPeer instance is initialized and not closed.
   *
   * @throws IllegalStateException if the instance is not initialized or has been closed
   */
  private void assertInitializedAndActive() {
    if (!initialized) {
      throw new IllegalStateException("ThinPeer is not initialized. Did you call init()?");
    }
    if (closed) {
      throw new IllegalStateException("ThinPeer is closed. Cannot perform operations.");
    }
  }

  /**
   * Sends a JSON-RPC request to the connected peer.
   *
   * @param jsonRpc the JSON-RPC request as a String or instance of JsonRpcRequest
   * @return a CompletableFuture that will be completed with the response
   * @throws JsonSerializationException if serialization of the JSON-RPC request fails
   */
  public CompletableFuture<JsonRpcResponse> sendJsonRpcRequestToPeer(Object jsonRpc)
      throws JsonSerializationException {
    return sendJsonRpcRequestToPeer(jsonRpc, null);
  }

  /**
   * Sends a JSON-RPC request to the connected peer with a specified message ID.
   *
   * @param jsonRpc the JSON-RPC request as a String or instance of JsonRpcRequest
   * @param messageId the message ID included in the request
   * @return a CompletableFuture that will be completed with the response
   * @throws JsonSerializationException if serialization of the JSON-RPC request fails
   * @throws IllegalStateException if not connected to any peer or the instance is not initialized
   */
  public CompletableFuture<JsonRpcResponse> sendJsonRpcRequestToPeer(
      Object jsonRpc, String messageId) throws JsonSerializationException {
    if (logger.isDebugEnabled()) {
      logger.debug("sendJsonRpcRequestToPeer: in with jsonRpc: {}", jsonRpc);
    }
    assertInitializedAndActive();

    if (talkingToPeer) {
      String rpcMessage;
      if (jsonRpc instanceof JsonRpcRequest jsonRpcRequest) {
        rpcMessage = JsonRpcSerializer.toJson(jsonRpcRequest);
        return wsClient.sendAsync(rpcMessage, jsonRpcRequest.getId());
      } else if (jsonRpc instanceof String) {
        rpcMessage = (String) jsonRpc;
        return wsClient.sendAsync(rpcMessage, messageId);
      } else {
        throw new IllegalArgumentException("Unsupported type for jsonRpc");
      }
    } else {
      throw new IllegalStateException(
          "Not connected to any peer. Cannot send and receive JSON-RPC messages to/from log");
    }
  }

  /**
   * Sends a Ping request to the connected peer, if any. If connected to a peer via Websocket, then
   * the ping is sent as a ping frame at the transport layer. If connected to a peer via ZMQ socket,
   * then the ping is sent using Pal's PING control message, which is an application-layer message
   * and goes through the RPC dispatch chain.
   *
   * @param timeout the amount of time to wait for the Ping response
   * @return the number of milliseconds waited for the response, or -1 if timed out
   * @throws IllegalStateException if not connected to any peer via Websocket
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ExecutionException if the ping future completed exceptionally
   */
  public double sendPing(Duration timeout)
      throws IllegalStateException, InterruptedException, ExecutionException {
    assertInitializedAndActive();
    if (!talkingToPeer) {
      throw new IllegalStateException("Not connected to any peer");
    }

    if (wsClient != null && isWsClientConnected) {
      long start = System.nanoTime();
      CompletableFuture<Void> voidCompletableFuture = wsClient.sendPingAsync();
      try {
        voidCompletableFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        long elapsedNanos = System.nanoTime() - start;
        return elapsedNanos / 1_000_000.0;
      } catch (TimeoutException e) {
        return -1;
      }
    } else if (peerSocket != null && isZmqSocketConnected) {
      ControlMessage pingMsg =
          msgBuilder.buildControlCommandMessage(peerUuid, ControlCommandType.PING);
      long start = System.nanoTime();
      try {
        sendToPeer(pingMsg, timeout);
      } catch (TimeoutException e) {
        logger.warn("Received TimeoutException sending ping to peer");
        return -1;
      }
      long elapsedNanos = System.nanoTime() - start;
      return elapsedNanos / 1_000_000.0;
    } else {
      throw new IllegalStateException(
          "Something is wrong. Talking to peer but no connection available");
    }
  }

  /**
   * Sends a Ping request to the connected peer, if any. If connected to a peer via Websocket, then
   * the ping is sent as a ping frame at the transport layer. If connected to a peer via ZMQ socket,
   * then the ping is sent using Pal's PING control message, which is at the application-layer and
   * goes through the RPC dispatch chain. This method calls {@link #sendPing(Duration)} with {@link
   * #PING_TIMEOUT} as the timeout parameter.
   *
   * @return the number of milliseconds waited for the response, -1 if timed out
   * @throws IllegalStateException if not connected to any peer via Websocket
   * @throws InterruptedException if the current thread was interrupted while waiting
   * @throws ExecutionException if the ping future completed exceptionally
   */
  public double sendPing() throws IllegalStateException, InterruptedException, ExecutionException {
    return sendPing(PING_TIMEOUT);
  }

  /**
   * Retrieves the log message at the specified offset.
   *
   * @param seek the offset of the message to retrieve
   * @return the LogMessage at the given offset
   */
  @SuppressWarnings("unused")
  public LogMessage<?> getMessageAtOffset(Long seek) {
    return getMessageAtOffset(seek, true);
  }

  /**
   * Retrieves the log message at the specified offset, optionally using the cache.
   *
   * @param seek the offset of the message to retrieve
   * @param lookupCached if true, checks the cached records first
   * @return the LogMessage at the given offset
   * @throws IllegalStateException if log consumption is not enabled or the instance is not
   *     initialized
   */
  private LogMessage<?> getMessageAtOffset(Long seek, boolean lookupCached) {
    assertInitializedAndActive();
    if (!consuming) {
      throw new IllegalStateException("ThinPeer log consumer not configured. Cannot get messages.");
    }
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

    Map<Long, ConsumerRecord<String, LogMessage<?>>> recordsRead = new HashMap<>();

    long actualSeekOffset = (seek - PRECEDING_RECS < 0) ? seek : seek - PRECEDING_RECS;
    if (logger.isDebugEnabled()) {
      logger.debug("Seek to offset #{}", actualSeekOffset);
    }
    synchronized (consumerLock) {
      consumer.seek(inTopicPartition, actualSeekOffset);
    }

    ConsumerRecord<String, LogMessage<?>> requestedRecord = null;
    while (requestedRecord == null) {
      ConsumerRecords<String, LogMessage<?>> records;
      synchronized (consumerLock) {
        records = consumer.poll(pollingDuration);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Read {} records during poll", records.count());
      }
      for (var record : records) {
        if (seek == record.offset()) {
          requestedRecord = record;
        }
        recordsRead.put(record.offset(), record);
      }
    }
    // now swap last batch (map) of records read with the new one
    this.lastRecordsRead = recordsRead;

    // set the offset in the LogMessage before returning
    long offset = requestedRecord.offset();
    LogMessage<?> logMessage = requestedRecord.value();
    logMessage.setOffset(offset);
    return logMessage;
  }

  /**
   * Retrieves a cached log message at the specified offset, if available.
   *
   * @param offset the offset of the cached message to retrieve
   * @return the cached LogMessage if found; otherwise, null
   */
  private LogMessage<?> getCachedMessageAtOffset(Long offset) {
    ConsumerRecord<String, LogMessage<?>> cachedRecord = lastRecordsRead.get(offset);
    if (cachedRecord != null) {
      // set the offset in the LogMessage before returning
      LogMessage<?> logMessage = cachedRecord.value();
      logMessage.setOffset(offset);
      return logMessage;
    }
    return null;
  }

  /**
   * Retrieves a list of log messages starting from the specified offset.
   *
   * @param startOffset the starting offset to retrieve messages from
   * @param numMessages the number of messages to retrieve
   * @return a list of ConsumerRecord objects containing the retrieved messages
   * @throws IllegalStateException if log consumption is not enabled or the instance is not
   *     initialized
   */
  @SuppressWarnings("unused")
  public List<ConsumerRecord<?, ?>> getMessages(long startOffset, long numMessages) {
    assertInitializedAndActive();
    if (!consuming) {
      throw new IllegalStateException("ThinPeer log consumer not configured. Cannot get messages.");
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Getting {} messages starting @ offset #{}", numMessages, startOffset);
    }
    synchronized (consumerLock) {
      consumer.seek(inTopicPartition, startOffset);
    }
    List<ConsumerRecord<?, ?>> messages = new ArrayList<>();
    boolean gotAllMessages = false;

    while (!gotAllMessages) {
      ConsumerRecords<String, LogMessage<?>> records;
      synchronized (consumerLock) {
        records = consumer.poll(pollingDuration);
      }
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

  /**
   * Returns the UUID of this peer.
   *
   * @return the UUID assigned to this peer
   */
  public UUID getPeerUuid() {
    return peerUuid;
  }

  /**
   * Sends an ExecMessage to the configured Kafka log.
   *
   * @param message the ExecMessage to send
   * @return a Future representing the result of the send operation
   * @throws IllegalStateException if producing is not enabled or the instance is not initialized
   */
  public Future<RecordMetadata> sendExecMessageToLog(ExecMessage message) {
    if (logger.isDebugEnabled()) {
      logger.debug("sendExecMessageToLog: in with message: {}", ColferUtils.format(message));
    }
    assertInitializedAndActive();
    if (!producing) {
      throw new IllegalStateException(
          "ThinPeer log producer not configured. Cannot send messages.");
    }

    // wrap in LogMessage
    var headers = Map.of("producer-id", peerUuid.toString());
    LogMessage<Message> logMessage =
        new LogMessage<>(outputLog.getName(), null, headers, msgBuilder.wrap(message));

    // create kafka record
    ProducerRecord<String, LogMessage<?>> record =
        new ProducerRecord<>(
            outputLog.getName(), PRODUCER_PARTITION, message.getMessageId(), logMessage);

    // send and return future
    var sendFuture = producer.send(record);
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{}", logMessage);
    }
    return sendFuture;
  }

  /**
   * Sends a JSON-RPC request to the configured Kafka log.
   *
   * @param jsonRpcRequest the JSON-RPC request as a String or JsonRpcRequest instance
   * @return a Future representing the result of the send operation
   * @throws JsonSerializationException if serialization of the JSON-RPC request fails
   * @throws IllegalStateException if producing is not enabled or the instance is not initialized
   */
  @SuppressWarnings("unused")
  public Future<RecordMetadata> sendJsonRpcRequestToLog(Object jsonRpcRequest)
      throws JsonSerializationException {
    if (logger.isDebugEnabled()) {
      logger.debug("sendJsonRpcRequestToLog: in with jsonRpcRequest: {}", jsonRpcRequest);
    }
    assertInitializedAndActive();
    if (!producing) {
      throw new IllegalStateException(
          "ThinPeer log producer not configured. Cannot send messages.");
    }

    JsonRpcMessage jsonRpcMessage;
    if (jsonRpcRequest instanceof JsonRpcRequest) {
      jsonRpcMessage = (JsonRpcRequest) jsonRpcRequest;
    } else if (jsonRpcRequest instanceof String) {
      jsonRpcMessage = JsonRpcSerializer.fromJson((String) jsonRpcRequest, JsonRpcRequest.class);
    } else {
      throw new IllegalArgumentException("Unsupported type for jsonRpc");
    }

    // create kafka record
    var headers = Map.of("producer-id", peerUuid.toString());
    LogMessage<JsonRpcMessage> logMessage =
        new LogMessage<>(outputLog.getName(), null, headers, jsonRpcMessage);
    final ProducerRecord<String, LogMessage<?>> record =
        new ProducerRecord<>(
            outputLog.getName(), PRODUCER_PARTITION, UUID.randomUUID().toString(), logMessage);

    // send and return future
    var sendFuture = producer.send(record);
    if (logger.isDebugEnabled()) {
      logger.debug("Message sent to log:\n{}", logMessage);
    }
    return sendFuture;
  }

  /**
   * Sends a JSON-RPC request to the configured Kafka log and awaits the corresponding response.
   *
   * @param jsonRpcRequest the JSON-RPC request as a String or JsonRpcRequest instance
   * @return a LogMessage containing the JsonRpcResponse
   * @throws JsonSerializationException if serialization of the JSON-RPC request fails
   * @throws IllegalStateException if producing is not enabled or the instance is not initialized
   */
  public LogMessage<JsonRpcResponse> sendJsonRpcRequestToLogAndReceive(Object jsonRpcRequest)
      throws JsonSerializationException {
    if (logger.isDebugEnabled()) {
      logger.debug("sendJsonRpcRequestToLogAndReceive: in with jsonRpcRequest: {}", jsonRpcRequest);
    }
    assertInitializedAndActive();
    if (!producing) {
      throw new IllegalStateException(
          "ThinPeer log producer not configured. Cannot send messages.");
    }

    JsonRpcMessage jsonRpcMessage;
    if (jsonRpcRequest instanceof JsonRpcRequest) {
      jsonRpcMessage = (JsonRpcRequest) jsonRpcRequest;
    } else if (jsonRpcRequest instanceof String) {
      jsonRpcMessage = JsonRpcSerializer.fromJson((String) jsonRpcRequest, JsonRpcRequest.class);
    } else {
      throw new IllegalArgumentException("Unsupported type for jsonRpc");
    }

    // create kafka record
    var headers = Map.of("producer-id", peerUuid.toString());
    LogMessage<JsonRpcMessage> logMessage =
        new LogMessage<>(outputLog.getName(), null, headers, jsonRpcMessage);
    final ProducerRecord<String, LogMessage<?>> newRecord =
        new ProducerRecord<>(
            outputLog.getName(), PRODUCER_PARTITION, UUID.randomUUID().toString(), logMessage);

    // send and get offset
    long sentRecordOffset;
    Future<RecordMetadata> recordMetadataFuture = producer.send(newRecord);
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      if (logger.isDebugEnabled()) {
        logger.debug("Message sent:\n {}", logMessage);
      }
      sentRecordOffset = recordMetadata.offset();
    } catch (Exception e) {
      logger.error("Error getting sent record metadata", e);
      return null;
    }

    // even if we send the request as a JsonRpc message, the peer's response is written as
    // ExecMessage
    LogMessage<Message> responseMessage =
        pollForResponseToRequestFromOffset(sentRecordOffset + 1, jsonRpcMessage.getId());
    // convert the ExecMessage response into a JsonRpc response
    JsonRpcResponse responseAsJsonRpc =
        msgBuilder.jsonRpcResponseFromExecMessageResponse(
            responseMessage.getContent().getExecMessage());
    return new LogMessage<>(
        responseMessage.getTopic(),
        responseMessage.getOffset(),
        responseMessage.getHeaders(),
        responseAsJsonRpc);
  }

  /**
   * Sends an ExecMessage to the configured Kafka log and awaits the corresponding response.
   *
   * @param message the ExecMessage to send
   * @return a LogMessage containing the response ExecMessage
   * @throws IllegalStateException if producing or consuming is not enabled or the instance is not
   *     initialized
   */
  public LogMessage<Message> sendExecMessageToLogAndReceive(ExecMessage message) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "sendExecMessageToLogAndReceive: in with message: {}", ColferUtils.format(message));
    }
    assertInitializedAndActive();
    if (!producing) {
      throw new IllegalStateException(
          "ThinPeer log producer not configured. Cannot send messages.");
    }
    if (!consuming) {
      throw new IllegalStateException("ThinPeer log consumer not configured. Cannot get messages.");
    }

    // wrap in LogMessage
    var headers = Map.of("producer-id", peerUuid.toString());
    LogMessage<Message> logMessage =
        new LogMessage<>(outputLog.getName(), null, headers, msgBuilder.wrap(message));

    // create kafka record
    ProducerRecord<String, LogMessage<?>> newRecord =
        new ProducerRecord<>(
            outputLog.getName(), PRODUCER_PARTITION, message.getMessageId(), logMessage);

    // send and get offset
    long sentRecordOffset;
    Future<RecordMetadata> recordMetadataFuture = producer.send(newRecord);
    try {
      RecordMetadata recordMetadata = recordMetadataFuture.get();
      if (logger.isDebugEnabled()) {
        logger.debug("Message sent:\n {}", logMessage);
      }
      sentRecordOffset = recordMetadata.offset();
    } catch (Exception e) {
      logger.error("Error getting sent record metadata", e);
      return null;
    }

    return pollForResponseToRequestFromOffset(sentRecordOffset + 1, message.getMessageId());
  }

  /**
   * Polls the Kafka log for a response message corresponding to the specified request ID, starting
   * from the given offset.
   *
   * @param offset the starting offset to poll from
   * @param requestId the ID of the request to match responses
   * @return the LogMessage containing the response
   */
  private LogMessage<Message> pollForResponseToRequestFromOffset(long offset, String requestId) {
    if (logger.isDebugEnabled()) {
      logger.debug("Consumer seeking to offset: {}", offset);
    }

    // poll to consume
    synchronized (consumerLock) {
      consumer.seek(inTopicPartition, offset);
    }

    // wait for response  (should contain responseToId = sentRecordOffset in message)
    while (true) {
      ConsumerRecords<String, LogMessage<?>> records;
      synchronized (consumerLock) {
        records = consumer.poll(pollingDuration);
      }
      if (records.count() != 0 && logger.isDebugEnabled()) {
        logger.debug("Received {} records", records.count());
      }
      for (var record : records) {
        final LogMessage<?> receivedMessage = record.value();
        long receivedMsgOffset = record.offset();
        if (!(receivedMessage.getContent() instanceof Message)) {
          // skip non-binary_rpc messages
          if (logger.isDebugEnabled()) {
            logger.debug("Skipping record with offset {}", receivedMsgOffset);
          }
          continue;
        }
        @SuppressWarnings("unchecked")
        final var responseMessage = (LogMessage<Message>) receivedMessage;
        final ExecMessage execMessage = responseMessage.getContent().getExecMessage();
        final String responseToId = execMessage == null ? null : execMessage.getResponseToId();
        if (execMessage != null && requestId.equals(responseToId)) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Got response with offset {} and id {} ",
                receivedMsgOffset,
                execMessage.getMessageId());
          }
          return responseMessage;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipping record with offset {}", receivedMsgOffset);
          }
        }
      }
    }
  }

  /**
   * Connects to a peer identified by the given UUID using the PAL directory.
   *
   * @param peerUuid the UUID of the peer to connect to
   * @throws Exception if connection fails due to directory access or peer retrieval issues
   */
  public void connectToPeer(UUID peerUuid) throws Exception {
    assertInitializedAndActive();
    PeerInfo newPeer = null;
    if (getPalDirectory() == null) {
      throw new RuntimeException("Cannot connect to peer without PAL directory");
    }
    try {
      newPeer = getPalDirectory().getPeer(peerUuid);
    } catch (Exception ex) {
      logger.error("Couldn't get peer properties", ex);
    }
    if (newPeer != null && !newPeer.equals(currentPeer)) {
      connectToPeer(newPeer);
    }
  }

  /**
   * Establishes a connection to the specified peer. If timeout is not null, wait for the connection
   * to be established up to the given timeout value.
   *
   * @param peer the PeerInfo object representing the peer to connect to
   * @param timeout the amount of time to wait for the connection to be established
   * @return true if the connection was established successfully, false if timed out
   * @throws Exception if connection setup fails
   */
  public boolean connectToPeer(PeerInfo peer, @Nullable Duration timeout) throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("connectToPeer: in with peer: {}", peer.getUuid());
    }

    // inform remote peer to end session, then close connection
    if (currentPeer != null && (isZmqSocketConnected || isWsClientConnected)) {
      sendDeleteSessionCommand();
      closePeerSocket();
    }

    boolean connected = true;
    if (outboundRpcType == RpcType.BIN_RPC) {
      if (timeout != null) {
        connected = connectZmqSocketWithTimeout(peer, timeout);
      } else {
        connectZmqSocket(peer);
      }
    } else { // is JSON-RPC
      if (timeout != null) {
        connected = connectWebSocketWithTimeout(peer, timeout);
      } else {
        connectWebSocket(peer);
      }
    }
    logger.info("Now in direct talk with {}", peer);
    return connected;
  }

  /**
   * Establishes a connection to the specified peer. This method calls {@link
   * #connectToPeer(PeerInfo, Duration)} with a null timeout value.
   *
   * @param peer the PeerInfo object representing the peer to connect to
   * @throws Exception if connection setup fails
   */
  public void connectToPeer(PeerInfo peer) throws Exception {
    connectToPeer(peer, null);
  }

  /**
   * Sends an ExecMessage directly to the connected peer and awaits the response.
   *
   * @param message the ExecMessage to send
   * @return the ExecMessage response from the peer
   * @throws IllegalStateException if not connected to a peer or the instance is not initialized
   */
  public ExecMessage sendToPeer(ExecMessage message) {
    if (logger.isDebugEnabled()) {
      logger.debug("sendToPeer: in with exec message: {}", ColferUtils.format(message));
    }
    assertInitializedAndActive();
    if (!talkingToPeer) {
      throw new IllegalStateException("Not connected to any peer. Cannot send message.");
    }
    // wrap in Message and send to peer
    peerSocket.send(ColferUtils.toBytes(msgBuilder.wrap(message)));

    final long waitStart = System.currentTimeMillis();
    byte[] response = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    final Message responseMessageWrapper = new Message();
    responseMessageWrapper.unmarshal(response, 0);
    final ExecMessage responseMessage = responseMessageWrapper.getExecMessage();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Got response message to exec message: {}, waited {} ms",
          ColferUtils.format(responseMessage),
          (waitEnd - waitStart));
    }

    return responseMessage;
  }

  /**
   * Sends a ControlMessage directly to the connected peer and awaits the response for up to the
   * given timeout, if one is given. Otherwise, it waits indefinitely for the response.
   *
   * @param message the ControlMessage to send
   * @param timeout the maximum time to wait for a response; can be null for no timeout
   * @return the ControlMessage response from the peer
   * @throws IllegalStateException if not connected to a peer or the instance is not initialized
   * @throws TimeoutException if no response is received within the given timeout
   */
  public ControlMessage sendToPeer(ControlMessage message, @Nullable Duration timeout)
      throws TimeoutException {

    if (logger.isDebugEnabled()) {
      logger.debug("in sendToPeer with control message: {}", ColferUtils.format(message));
    }

    // Ensure we're still connected and initialized
    assertInitializedAndActive();
    if (!talkingToPeer) {
      throw new IllegalStateException("Not connected to any peer. Cannot send message.");
    }

    int originalTimeout = peerSocket.getReceiveTimeOut();
    if (timeout != null) {
      // Temporarily set the socket's receive timeout
      // recv() will block up to this duration
      peerSocket.setReceiveTimeOut((int) timeout.toMillis());
    }

    try {
      // Send the message
      peerSocket.send(ColferUtils.toBytes(msgBuilder.wrap(message)));

      final long waitStart = System.currentTimeMillis();
      byte[] response = peerSocket.recv(0);
      final long waitEnd = System.currentTimeMillis();

      if (response == null && timeout != null) {
        // recv() returns null if the timeout was reached
        throw new TimeoutException(
            String.format("No response received within %d ms", timeout.toMillis()));
      }

      // Unmarshal the response into a ControlMessage
      final Message responseMessageWrapper = new Message();
      responseMessageWrapper.unmarshal(response, 0);
      final ControlMessage responseMessage = responseMessageWrapper.getControlMessage();

      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got response to control message: {}, waited {} ms",
            responseMessage.getMessageId(),
            (waitEnd - waitStart));
      }

      return responseMessage;
    } finally {
      if (timeout != null) {
        // Restore the original timeout
        peerSocket.setReceiveTimeOut(originalTimeout);
      }
    }
  }

  /**
   * Sends a ControlMessage directly to the connected peer and awaits the response. This method
   * calls {@link #sendToPeer(ControlMessage, Duration)} with a null timeout.
   *
   * @param message the ControlMessage to send
   * @return the ControlMessage response from the peer
   * @throws IllegalStateException if not connected to a peer or the instance is not initialized
   */
  public ControlMessage sendToPeer(ControlMessage message) {
    try {
      return sendToPeer(message, null);
    } catch (TimeoutException e) {
      // this should not happen
      throw new RuntimeException(
          "This TimeoutException should not have been thrown, but here it is", e);
    }
  }

  /**
   * Sends a MetaMessage directly to the connected peer and awaits the response.
   *
   * @param message the MetaMessage to send
   * @return the MetaMessage response from the peer
   * @throws IllegalStateException if not connected to a peer or the instance is not initialized
   */
  public MetaMessage sendToPeer(MetaMessage message) {
    if (logger.isDebugEnabled()) {
      logger.debug("in sendToPeer with meta message: {}", ColferUtils.format(message));
    }
    assertInitializedAndActive();
    if (!talkingToPeer) {
      throw new IllegalStateException("Not connected to any peer. Cannot send message.");
    }
    // send message request to peer
    if (logger.isDebugEnabled()) {
      logger.debug("sending...");
    }
    peerSocket.send(ColferUtils.toBytes(msgBuilder.wrap(message)));
    final long waitStart = System.currentTimeMillis();
    byte[] response = peerSocket.recv(0);
    final long waitEnd = System.currentTimeMillis();

    final Message responseMessageWrapper = new Message();
    responseMessageWrapper.unmarshal(response, 0);
    final MetaMessage responseMessage = responseMessageWrapper.getMetaMessage();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Got response to meta message w/id: {}, waited {} ms",
          responseMessage.getMessageId(),
          (waitEnd - waitStart));
    }

    return responseMessage;
  }

  /**
   * Sends a command to delete the current session with the connected peer.
   *
   * @throws IllegalStateException if not connected to a peer or the instance is not initialized
   */
  public void sendDeleteSessionCommand() {
    assertInitializedAndActive();

    if (currentPeer == null) {
      throw new IllegalStateException("Not connected to a peer");
    }

    final String sessionId = peerUuid.toString();
    if (isZmqSocketConnected) {
      ControlMessage msg = msgBuilder.buildDeleteSessionCommandMessage(peerUuid);
      ControlMessage responseMessage = sendToPeer(msg);
      ControlStatusType statusType = ControlStatusType.fromId(responseMessage.getStatus());
      if (ControlStatusType.OK.equals(statusType)) {
        logger.debug("Session w/id {} was deleted.", sessionId);
      } else {
        logger.error("Error deleting session w/id: {} - status: {}", sessionId, statusType);
      }
    } else if (isWsClientConnected) {
      JsonRpcRequest request = JsonRpcMessageFactory.buildDeleteSessionCommandMessage();
      try {
        JsonRpcResponse response = sendJsonRpcRequestToPeer(request).get();
        if (response.getError() == null) {
          logger.debug("Session w/id {} was deleted.", sessionId);
        } else {
          logger.error(
              "Error deleting session w/id: {} - error: {}", sessionId, response.getError());
        }
      } catch (Exception e) {
        logger.error("Error sending json-rpc request to delete session", e);
      }
    } else {
      throw new IllegalStateException(
          "Current peer is not null but no active Zmq nor WS socket connection");
    }
  }

  /** Closes the Kafka consumer if it was not provided externally. */
  private void closeConsumer() {
    if (consumer != null) {
      try {
        synchronized (consumerLock) {
          consumer.unsubscribe();
          consumer.close(Duration.of(500, ChronoUnit.MILLIS));
        }
        logger.info("Log consumer closed.");
      } catch (Exception e) {
        logger.warn("Error closing consumer", e);
      }
    }
  }

  /** Closes the Kafka producer if it was not provided externally. */
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

  /** Closes the peer ZeroMQ socket. */
  private void closePeerZMQSocket() {
    if (peerSocket != null) {
      peerSocket.close();
      isZmqSocketConnected = false;
      peerSocket = null;
      currentPeer = null;
      talkingToPeer = false;
    }
  }

  /** Closes the peer WebSocket client. */
  private void closeWSClient() {
    if (wsClient != null) {
      wsClient.close();
      isWsClientConnected = false;
      wsClient = null;
      currentPeer = null;
      talkingToPeer = false;
    }
  }

  /** Closes the peer connection sockets, including ZeroMQ and WebSocket clients. */
  private void closePeerSocket() {
    try {
      closePeerZMQSocket();
      logger.info("Peer zmq socket closed.");
    } catch (Exception e) {
      logger.warn("Error closing peer zmq socket", e);
    }

    try {
      closeWSClient();
      logger.info("WebSocket client closed.");
    } catch (Exception e) {
      logger.warn("Error closing peer websocket", e);
    }
  }

  /**
   * Closes the ThinPeer instance, releasing all associated resources and unregistering from the PAL
   * directory if self-registration was enabled.
   *
   * <p>This method is idempotent and safe to call multiple times.
   */
  @Override
  public void close() {
    assertInitializedAndActive();

    if (currentPeer != null && (isZmqSocketConnected || isWsClientConnected)) {
      sendDeleteSessionCommand();
    }

    // NOTE: we only close resources that were not passed to us

    // close socket-related resources
    closePeerSocket();
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

    // unregister self
    if (getPalDirectory() != null && registerSelf) {
      try {
        peerLease.close(); // revoke + stop keep-alive
        getPalDirectory().deletePeer(this.peerUuid);
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

  /**
   * Checks if log input/output operations are enabled.
   *
   * @return true if log IO is enabled; false otherwise
   */
  public boolean isLogIOEnabled() {
    return logIOEnabled;
  }

  /**
   * Checks if this ThinPeer instance has been closed.
   *
   * @return true if closed; false otherwise
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Checks if this ThinPeer instance has been initialized.
   *
   * @return true if initialized; false otherwise
   */
  public boolean isInitialized() {
    return initialized;
  }

  /**
   * Retrieves the name assigned to this peer.
   *
   * @return the peer's name
   */
  public String getName() {
    return peerName;
  }

  /**
   * Retrieves the Kafka bootstrap servers configured for this peer.
   *
   * @return the Kafka bootstrap servers
   */
  public String getBootstrapServers() {
    return bootstrapServers;
  }

  /**
   * Retrieves the LogInfo for input messages.
   *
   * @return the input LogInfo
   */
  public LogInfo getInputLog() {
    return inputLog;
  }

  /**
   * Retrieves the LogInfo for output messages.
   *
   * @return the output LogInfo
   */
  public LogInfo getOutputLog() {
    return outputLog;
  }

  /**
   * Retrieves the polling duration configured for the Kafka consumer.
   *
   * @return the polling Duration
   */
  public Duration getPollingDuration() {
    return pollingDuration;
  }

  /**
   * Retrieves the Kafka producer instance.
   *
   * @return the Kafka Producer
   */
  public Producer<String, LogMessage<?>> getProducer() {
    return producer;
  }

  /**
   * Retrieves the Kafka consumer instance.
   *
   * @return the Kafka Consumer
   */
  public Consumer<String, LogMessage<?>> getConsumer() {
    return consumer;
  }

  /**
   * Retrieves the Kafka producer configuration properties.
   *
   * @return the producer Properties
   */
  public Properties getProducerProperties() {
    return producerProperties;
  }

  /**
   * Retrieves the Kafka consumer configuration properties.
   *
   * @return the consumer Properties
   */
  public Properties getConsumerProperties() {
    return consumerProperties;
  }

  /**
   * Retrieves the ZeroMQ context.
   *
   * @return the ZContext used for ZeroMQ operations
   */
  public ZContext getZmqContext() {
    return zmqContext;
  }

  /**
   * Retrieves the Binary-RPC address of this peer.
   *
   * @return the Binary-RPC address
   */
  public String getRpcAddress() {
    return rpcAddress;
  }

  /**
   * Retrieves the outbound RPC type used by this peer.
   *
   * @return the outbound RpcType
   */
  public RpcType getOutboundRpcType() {
    return outboundRpcType;
  }

  /**
   * Retrieves the initial peer information used for connection.
   *
   * @return the initial PeerInfo
   */
  public PeerInfo getInitialPeer() {
    return initialPeer;
  }

  /**
   * Retrieves the currently connected peer information.
   *
   * @return the current PeerInfo
   */
  public PeerInfo getCurrentPeer() {
    return currentPeer;
  }

  /**
   * Checks if this peer is currently communicating with another peer.
   *
   * @return true if communicating with a peer; false otherwise
   */
  public boolean isTalkingToPeer() {
    return talkingToPeer;
  }

  /**
   * Retrieves the URL of the PAL directory service.
   *
   * @return the PAL directory URL
   */
  public String getPalDirectoryUrl() {
    return palDirectoryUrl;
  }

  /**
   * Retrieves the prefix used for Kafka log topics.
   *
   * @return the log prefix
   */
  public String getLogPrefix() {
    return logPrefix;
  }

  /**
   * Checks if this peer is configured to self-register with the PAL directory.
   *
   * @return true if self-registration is enabled; false otherwise
   */
  public boolean isSelfRegistering() {
    return registerSelf;
  }

  /**
   * Checks if the ZeroMQ socket is currently connected.
   *
   * @return true if the ZMQ socket is connected; false otherwise
   */
  public boolean isZmqSocketConnected() {
    return isZmqSocketConnected;
  }

  /**
   * Checks if producing is currently enabled.
   *
   * @return true if producing is enabled; false otherwise
   */
  public boolean isProducing() {
    return producing;
  }

  /**
   * Checks if consuming is currently enabled.
   *
   * @return true if consuming is enabled; false otherwise
   */
  public boolean isConsuming() {
    return consuming;
  }

  // </editor-fold>

  /** WebSocket client for handling JSON-RPC communication with a peer. */
  private static final class WsClient extends WebSocketClient {

    /** Maps message IDs to their corresponding CompletableFuture responses. */
    private final Map<String, CompletableFuture<JsonRpcResponse>> futureResponses =
        new ConcurrentHashMap<>();

    /** Future of an async ping, that is completed on receiving pong. */
    private CompletableFuture<Void> pingFuture;

    /**
     * Constructs a new WsClient instance with the specified URI and HTTP headers.
     *
     * @param uri the URI to connect to
     * @param httpHeaders the HTTP headers to include in the WebSocket handshake
     */
    WsClient(URI uri, Map<String, String> httpHeaders) {
      super(uri, httpHeaders);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a message through the WebSocket connection.
     */
    @Override
    public void send(String message) {
      if (logger.isDebugEnabled()) {
        logger.debug("sending message to ws socket: {}", message);
      }
      super.send(message);
    }

    /**
     * Sends a JSON-RPC request asynchronously through the WebSocket.
     *
     * @param message the JSON-RPC request message as a String
     * @param messageId the identifier for the JSON-RPC message
     * @return a CompletableFuture that will be completed with the JsonRpcResponse
     * @throws JsonSerializationException if deserialization of the message fails
     */
    public CompletableFuture<JsonRpcResponse> sendAsync(String message, @Nullable String messageId)
        throws JsonSerializationException {
      if (logger.isDebugEnabled()) {
        logger.debug("in sendAsync - sending message to ws socket: {}", message);
      }
      String id;
      if (messageId == null) {
        JsonRpcRequest request = JsonRpcSerializer.fromJson(message, JsonRpcRequest.class);
        id = request.getId();
      } else {
        id = messageId;
      }
      CompletableFuture<JsonRpcResponse> futureResponse = new CompletableFuture<>();
      futureResponses.put(id, futureResponse);
      send(message);
      return futureResponse;
    }

    /**
     * Sends a protocol-level WebSocket ping frame. Returns a CompletableFuture that completes when
     * (and if) the corresponding pong arrives.
     *
     * @return a CompletableFuture that will be completed when the Pong is received
     */
    public CompletableFuture<Void> sendPingAsync() {
      if (logger.isDebugEnabled()) {
        logger.debug("in sendAsync - sending ping to ws socket");
      }
      if (pingFuture != null && !pingFuture.isDone()) {
        throw new IllegalStateException(
            "A ping is already in flight. Wait for it to complete first.");
      }

      pingFuture = new CompletableFuture<>();
      try {
        super.sendPing();
      } catch (WebsocketNotConnectedException e) {
        pingFuture.completeExceptionally(e);
      }
      return pingFuture;
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
      if (pingFuture != null && !pingFuture.isDone()) {
        pingFuture.complete(null);
      }

      super.onWebsocketPong(conn, f);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Handles initialization tasks upon opening the WebSocket connection.
     */
    @Override
    public void onOpen(ServerHandshake handshakedata) {
      logger.info("WebSocket connection opened");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Processes incoming messages and completes the corresponding CompletableFuture.
     */
    @Override
    public void onMessage(String message) {
      if (message == null || message.isBlank()) {
        if (logger.isDebugEnabled()) {
          logger.debug("Ignoring received null or empty message...");
        }
        return;
      }
      JsonRpcResponse response;
      try {
        response = JsonRpcSerializer.fromJson(message, JsonRpcResponse.class);
      } catch (JsonSerializationException e) {
        logger.error("Error deserializing JSON-RPC response", e);
        throw new RuntimeException(e);
      }
      CompletableFuture<JsonRpcResponse> futureResponse = futureResponses.get(response.getId());
      if (futureResponse == null) {
        logger.error("No response future found for message id: {}", response.getId());
      } else {
        futureResponse.complete(response);
        futureResponses.remove(response.getId());
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Logs the closure of the WebSocket connection.
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
      logger.info("WebSocket connection closed");

      // complete exceptionally any on-flight ping
      if (pingFuture != null && !pingFuture.isDone()) {
        pingFuture.completeExceptionally(
            new RuntimeException("Connection closed before pong received."));
      }

      // complete exceptionally any json-rpc request not yet responded
      if (!futureResponses.isEmpty()) {
        futureResponses
            .values()
            .forEach(
                f ->
                    f.completeExceptionally(
                        new RuntimeException(
                            "Connection closed before JSON-RPC response received.")));
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Logs any errors that occur during WebSocket communication.
     */
    @Override
    public void onError(Exception ex) {
      logger.error("WebSocket error", ex);

      // complete exceptionally any on-flight ping
      if (pingFuture != null && !pingFuture.isDone()) {
        pingFuture.completeExceptionally(
            new RuntimeException("An error was received while waiting for pong", ex));
      }
    }
  }
}
