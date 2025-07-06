/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.websocket;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import com.quasient.pal.messages.jsonrpc.ResponseObject;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.messages.types.MetaServiceType;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.ResponseObjectSerializer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A WebSocket server implementation for handling JSON-RPC requests and responses.
 *
 * <p>This server listens for incoming WebSocket connections, processes JSON-RPC messages, and
 * dispatches responses to clients. For certain response types, such as metadata responses, file
 * contents are streamed back to clients using a custom output stream.
 */
class JsonRpcWebSocketServer extends WebSocketServer {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(JsonRpcWebSocketServer.class);

  /** Timeout in milliseconds to wait for the server to stop gracefully. */
  private static final int STOP_TIMEOUT_MS = 10000;

  /** Size of the thread pool for processing WebSocket connections. */
  private static final int WS_THREAD_POOL_SIZE = 3;

  /** Latch that allows signalling when the server socket open/ready. */
  private final CountDownLatch wsSocketReadyLatch;

  /** Message sent to clients when closing the WebSocket server. */
  private static final String CLOSE_MSG = "Closing WebSocket server. Bye!";

  /** ObjectMapper configured with a custom module for serializing ResponseObject instances. */
  private final ObjectMapper responseObjectMapper;

  /** Queue used to enqueue inbound JSON-RPC request messages for subsequent processing. */
  private final BlockingQueue<InboundJsonRpcRequestMsg> requestQueue;

  /**
   * Mapping between active WebSocket connections and their corresponding unique peer identifiers.
   */
  private final Map<WebSocket, UUID> webSocketConnectionMapping = new ConcurrentHashMap<>();

  /** Mapping of peer IDs to connection statistics used for tracking message counts. */
  private final Map<UUID, ConnectionStats> peerStatsMap = new ConcurrentHashMap<>();

  /**
   * Constructs a JsonRpcWebSocketServer with the specified binding address and request queue.
   *
   * @param address the InetSocketAddress on which the server listens for connections
   * @param requestQueue the BlockingQueue where inbound JSON-RPC requests are enqueued
   * @param wsSocketReadyLatch the CountdownLatch used to signal that the server connection is open
   */
  public JsonRpcWebSocketServer(
      InetSocketAddress address,
      BlockingQueue<InboundJsonRpcRequestMsg> requestQueue,
      CountDownLatch wsSocketReadyLatch) {
    super(address, WS_THREAD_POOL_SIZE);
    setReuseAddr(true);
    this.requestQueue = requestQueue;
    this.wsSocketReadyLatch = wsSocketReadyLatch;
    this.responseObjectMapper = createResponseObjectMapper();
    logger.info("Initialized WebSocketServer. Will listen on: {}", address);
  }

  /**
   * Creates and configures an ObjectMapper with a custom module for serializing ResponseObject
   * instances.
   *
   * @return a configured ObjectMapper used for serializing JSON-RPC responses
   */
  private ObjectMapper createResponseObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(ResponseObject.class, new ResponseObjectSerializer());
    mapper.registerModule(module);
    return mapper;
  }

  /**
   * Retrieves the WebSocket connection associated with the specified peer identifier.
   *
   * @param connId the unique identifier for the peer connection
   * @return the corresponding WebSocket connection, or null if no matching connection is found
   */
  private WebSocket getConnectionSocketFromId(UUID connId) {
    return webSocketConnectionMapping.entrySet().stream()
        .filter(e -> e.getValue().equals(connId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  /**
   * Sends the given JSON-RPC response to the client corresponding to the specified peer identifier.
   *
   * <p>For responses of type {@code META_MESSAGE_RESPONSE} that indicate a metadata response, the
   * method streams file contents back to the client; otherwise, the response is sent directly.
   *
   * @param peerId the unique identifier of the target peer
   * @param response the JSON serialized response to be sent
   * @param messageType the type of message that dictates the response handling behavior
   */
  public void sendResponseToWebSocketClient(UUID peerId, String response, MessageType messageType) {
    WebSocket connSocket = getConnectionSocketFromId(peerId);
    if (connSocket == null) {
      logger.error("Error sending back response: no WebSocket peer found for id: {}", peerId);
      return;
    }

    // if it's a non-error response to FETCH_CLASSES_INFO, stream file contents back to client
    if (messageType.equals(MessageType.META_MESSAGE_RESPONSE)) {
      try {
        JsonRpcResponse jsonRpcResponse =
            JsonRpcSerializer.fromJson(response, JsonRpcResponse.class);
        if (isClassMetadataResponse(jsonRpcResponse)) {
          Objects.requireNonNull(jsonRpcResponse.getResult());
          ResponseObject responseObj = jsonRpcResponse.getResult().getValue();
          @SuppressWarnings("unchecked")
          Map<String, String> responseMap =
              responseObjectMapper.readValue(responseObj.getValue(), Map.class);
          Path metadataFilePath = Path.of(responseMap.get("response"));
          sendStreamingResponse(connSocket, jsonRpcResponse.getId(), metadataFilePath);
          // delete metadata file
          Files.deleteIfExists(metadataFilePath);
          peerStatsMap.get(peerId).incrementTotalMessagesSent();
          logger.debug("Sent back response to peer w/id: {}", peerId);
          return;
        }
      } catch (Exception e) {
        logger.error("Error sending back response to peer w/id: {}", peerId, e);
        return;
      }
    }

    // other responses are sent to the client directly as a simple message
    try {
      connSocket.send(response);
      peerStatsMap.get(peerId).incrementTotalMessagesSent();
      logger.debug("Sent back response to peer w/id: {}", peerId);
    } catch (Exception e) {
      logger.error("Error sending back response to peer w/id: {}", peerId, e);
    }
  }

  /**
   * Checks whether the given JSON-RPC response should be treated as a class metadata response.
   *
   * <p>This method deserializes the embedded response map and verifies if it corresponds to a
   * metadata request for class information.
   *
   * @param jsonRpcResponse the JSON-RPC response to examine
   * @return true if the response is recognized as a class metadata response; false otherwise
   */
  private boolean isClassMetadataResponse(JsonRpcResponse jsonRpcResponse) {
    if (jsonRpcResponse.getResult() != null) {
      ResponseObject responseObj = jsonRpcResponse.getResult().getValue();
      if (responseObj != null) {
        try {
          @SuppressWarnings("unchecked")
          Map<String, String> responseMap =
              responseObjectMapper.readValue(responseObj.getValue(), Map.class);
          return MetaServiceType.FETCH_CLASSES_INFO
              .getJsonName()
              .equals(responseMap.get("service"));
        } catch (JsonProcessingException e) {
          logger.error("Error deserializing response map", e);
        }
      }
    }
    return false;
  }

  /**
   * Sends a streaming JSON-RPC response to a client by transmitting the contents of the specified
   * file over the WebSocket connection.
   *
   * <p>The method builds a placeholder JSON-RPC response, then opens an input stream for the file
   * and serializes the response using a custom serializer that streams the file content.
   *
   * @param conn the WebSocket connection to which the streaming data is sent
   * @param messageId the identifier associated with the JSON-RPC response message
   * @param filePath the file path from which data is to be streamed
   * @throws IOException if an I/O error occurs while reading the file or transmitting data
   */
  public void sendStreamingResponse(WebSocket conn, String messageId, Path filePath)
      throws IOException {
    // 1. Build the JsonRpcResponse (value field is a placeholder)
    JsonRpcResponse response =
        JsonRpcResponse.builder()
            .withId(messageId)
            .withResult(
                JsonRpcResponseReturnValue.builder()
                    .withIsVoid(false)
                    .withValue(
                        ResponseObject.builder()
                            .withIsNull(false)
                            .withValue("[STREAMING_DATA]") // Placeholder
                            .build())
                    .build())
            .build();

    // 2. Open the file input stream
    try (InputStream dataStream = Files.newInputStream(filePath)) {
      // 3. Serialize with the custom serializer and stream to WebSocket
      try (WebSocketOutputStream wsStream = new WebSocketOutputStream(conn);
          JsonGenerator generator = responseObjectMapper.getFactory().createGenerator(wsStream)) {

        // Pass the InputStream as a context attribute
        ObjectWriter writer =
            responseObjectMapper.writer().withAttribute("valueInputStream", dataStream);
        writer.writeValue(generator, response);
      }
    }
  }

  /**
   * Invoked when a new WebSocket connection is opened.
   *
   * <p>The method assigns a unique peer UUID based on the handshake header if present and valid;
   * otherwise, a new UUID is generated. It also initializes connection statistics for the peer.
   *
   * @param conn the newly established WebSocket connection
   * @param handshake the handshake data provided by the connecting client
   */
  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    UUID peerId = null;
    if (logger.isDebugEnabled()) {
      logger.debug(
          "New connection from: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }
    if (handshake.hasFieldValue("peer-id")) {
      try {
        peerId = UUID.fromString(handshake.getFieldValue("peer-id"));
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid peer-id value found in header", e);
      }
    }
    if (peerId == null) {
      peerId = UUID.randomUUID();
      logger.debug("No valid peer-id found in handshake. Assigned new id: {}", peerId);
    }

    webSocketConnectionMapping.put(conn, peerId);
    peerStatsMap.put(peerId, new ConnectionStats());
  }

  /**
   * Invoked when a WebSocket connection is closed.
   *
   * <p>This method logs the closure event, removes the mapping for the closed connection, and
   * cleans up associated connection statistics.
   *
   * @param conn the WebSocket connection that was closed
   * @param code the status code indicating the reason for closure
   * @param reason a descriptive reason for connection closure
   * @param remote true if the connection was closed by the remote host, false otherwise
   */
  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Closed connection from: {}",
          conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }
    UUID peerId = webSocketConnectionMapping.remove(conn);
    if (logger.isDebugEnabled()) {
      // log connection status
      ConnectionStats stats = peerStatsMap.get(peerId);
      logger.debug(
          "Stats for peer w/id: {} -> Messages sent: {}, Messages received: {}",
          peerId,
          stats.getTotalMessagesSent(),
          stats.getTotalMessagesReceived());
    }
    peerStatsMap.remove(peerId);
  }

  /**
   * Invoked when a message is received from a client.
   *
   * <p>This method increments the received message count for the peer, logs the event, constructs
   * an inbound JSON-RPC request message, and enqueues it for processing.
   *
   * @param conn the WebSocket connection from which the message was received
   * @param message the received message content
   */
  @Override
  public void onMessage(WebSocket conn, String message) {
    UUID peerId = webSocketConnectionMapping.get(conn);
    peerStatsMap.get(peerId).incrementTotalMessagesReceived();
    logger.debug("New message from peer w/id: {}", peerId);
    if (logger.isTraceEnabled()) {
      logger.trace("Message received: {}", message);
    }
    InboundJsonRpcRequestMsg requestMsg = new InboundJsonRpcRequestMsg(peerId, message);
    // Add the message to the queue
    requestQueue.offer(requestMsg);
    if (logger.isDebugEnabled()) {
      logger.debug("Pushed message from peer w/id: {} for dispatch", peerId);
    }
  }

  /**
   * Invoked when an error occurs on a WebSocket connection.
   *
   * @param conn the WebSocket connection where the error occurred (it may be null)
   * @param ex the exception representing the error encountered
   */
  @Override
  public void onError(WebSocket conn, Exception ex) {
    logger.error("Error on WebSocket connection", ex);
  }

  /**
   * Invoked when the WebSocket server has successfully started.
   *
   * <p>Logs the server's binding address upon startup.
   */
  @Override
  public void onStart() {
    logger.info("WebSocket server started on: {}", getAddress());
    wsSocketReadyLatch.countDown();
  }

  /**
   * Closes the WebSocket server by attempting a graceful shutdown.
   *
   * <p>Uses a predefined timeout and close message. Any interruption during the stop process is
   * logged.
   */
  public void close() {
    try {
      stop(STOP_TIMEOUT_MS, CLOSE_MSG);
    } catch (InterruptedException e) {
      logger.error("Error closing WebSocket server", e);
    }
  }

  /**
   * Tracks statistics for a WebSocket connection, including the counts of messages sent and
   * received.
   */
  private static class ConnectionStats {

    /** Atomic counter for the total number of messages sent by the connection. */
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);

    /** Atomic counter for the total number of messages received by the connection. */
    private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);

    /** Increments the counter for total messages sent. */
    public void incrementTotalMessagesSent() {
      totalMessagesSent.incrementAndGet();
    }

    /** Increments the counter for total messages received. */
    public void incrementTotalMessagesReceived() {
      totalMessagesReceived.incrementAndGet();
    }

    /**
     * Returns the total number of messages sent by the connection.
     *
     * @return the message send count
     */
    public long getTotalMessagesSent() {
      return totalMessagesSent.get();
    }

    /**
     * Returns the total number of messages received by the connection.
     *
     * @return the message receive count
     */
    public long getTotalMessagesReceived() {
      return totalMessagesReceived.get();
    }
  }
}
