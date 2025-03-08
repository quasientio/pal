package net.ittera.pal.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
import java.util.concurrent.atomic.AtomicInteger;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.rpc.WebSocketOutputStream;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.ResponseObject;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.messages.types.MetaServiceType;
import net.ittera.pal.serdes.jsonrpc.JsonRpcSerializer;
import net.ittera.pal.serdes.jsonrpc.ResponseObjectSerializer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JsonRpcWebSocketServer extends WebSocketServer {

  private static final Logger logger = LoggerFactory.getLogger(JsonRpcWebSocketServer.class);
  private static final int STOP_TIMEOUT_MS = 2000;
  private static final int WS_THREAD_POOL_SIZE = 3;
  private static final String CLOSE_MSG = "Closing WebSocket server. Bye!";

  private final ObjectMapper responseObjectMapper;
  private final BlockingQueue<InboundJsonRpcRequestMsg> requestQueue;
  private final Map<WebSocket, UUID> webSocketConnectionMapping = new ConcurrentHashMap<>();
  private final Map<UUID, ConnectionStats> peerStatsMap = new ConcurrentHashMap<>();

  public JsonRpcWebSocketServer(
      InetSocketAddress address, BlockingQueue<InboundJsonRpcRequestMsg> requestQueue) {
    super(address, WS_THREAD_POOL_SIZE);
    setReuseAddr(true);
    this.requestQueue = requestQueue;
    this.responseObjectMapper = createResponseObjectMapper();
  }

  private ObjectMapper createResponseObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(ResponseObject.class, new ResponseObjectSerializer());
    mapper.registerModule(module);
    return mapper;
  }

  private WebSocket getConnectionSocketFromId(UUID connId) {
    return webSocketConnectionMapping.entrySet().stream()
        .filter(e -> e.getValue().equals(connId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

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

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    UUID peerId;
    if (logger.isDebugEnabled()) {
      logger.debug(
          "New connection from: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }
    if (handshake.hasFieldValue("peer-id")) {
      peerId = UUID.fromString(handshake.getFieldValue("peer-id"));
    } else {
      peerId = UUID.randomUUID();
      logger.debug("No peer-id header found in handshake. Assigned new id: {}", peerId);
    }

    webSocketConnectionMapping.put(conn, peerId);
    peerStatsMap.put(peerId, new ConnectionStats());
  }

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

  @Override
  public void onError(WebSocket conn, Exception ex) {
    logger.error("Error on WebSocket connection", ex);
  }

  @Override
  public void onStart() {
    logger.info("WebSocket server started on: {}", getAddress());
  }

  public void close() {
    try {
      stop(STOP_TIMEOUT_MS, CLOSE_MSG);
    } catch (InterruptedException e) {
      logger.error("Error closing WebSocket server", e);
    }
  }

  private static class ConnectionStats {
    private final AtomicInteger totalMessagesSent = new AtomicInteger(0);
    private final AtomicInteger totalMessagesReceived = new AtomicInteger(0);

    public void incrementTotalMessagesSent() {
      totalMessagesSent.incrementAndGet();
    }

    public void incrementTotalMessagesReceived() {
      totalMessagesReceived.incrementAndGet();
    }

    public long getTotalMessagesSent() {
      return totalMessagesSent.get();
    }

    public long getTotalMessagesReceived() {
      return totalMessagesReceived.get();
    }
  }
}
