package net.ittera.pal.core;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JsonRpcWebSocketServer extends WebSocketServer {

  private static final Logger logger = LoggerFactory.getLogger(JsonRpcWebSocketServer.class);
  private static final int STOP_TIMEOUT_MS = 2000;
  private static final int WS_THREAD_POOL_SIZE = 2;
  private static final String CLOSE_MSG = "Closing WebSocket server. Bye!";

  private final BlockingQueue<InboundJsonRpcRequestMsg> requestQueue;
  private final Map<WebSocket, UUID> webSocketConnectionMapping = new ConcurrentHashMap<>();
  private final Map<UUID, ConnectionStats> peerStatsMap = new ConcurrentHashMap<>();

  public JsonRpcWebSocketServer(
      InetSocketAddress address, BlockingQueue<InboundJsonRpcRequestMsg> requestQueue) {
    super(address, WS_THREAD_POOL_SIZE);
    setReuseAddr(true);
    this.requestQueue = requestQueue;
  }

  private WebSocket getConnectionSocketFromId(UUID connId) {
    return webSocketConnectionMapping.entrySet().stream()
        .filter(e -> e.getValue().equals(connId))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  public void sendResponseToWebSocketClient(UUID peerId, String response) {
    WebSocket connSocket = getConnectionSocketFromId(peerId);
    if (connSocket == null) {
      logger.error("Error sending back response: no WebSocket peer found for id: {}", peerId);
      return;
    }
    try {
      connSocket.send(response);
      peerStatsMap.get(peerId).incrementTotalMessagesSent();
      logger.debug("Sent back response to peer w/id: {}", peerId);
    } catch (Exception e) {
      logger.error("Error sending back response to peer w/id: {}", peerId, e);
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
