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

import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import com.quasient.pal.core.internal.messages.OutboundJsonRpcResponseMsg;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

public class JsonRpcRequestServerTest extends ZmqEnabledTest {

  private static final String DEALER_ADDRESS = "inproc://jsonrpc.dealer";
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private ServiceManager manager;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private ExecutorService execService;
  private ZContext zmqContext;
  private WsClient webSocketClient;
  private Worker rpcMessageInvoker;

  @Before
  public void setUp() throws URISyntaxException, InterruptedException {
    final int port;
    try {
      port = findAvailableServerPort();
    } catch (Exception e) {
      // Sandbox may forbid creating ServerSocket; skip these websocket tests if so
      Assume.assumeNoException("Skipping WebSocket tests due to sandbox", e);
      return;
    }
    String websocketAddress = String.format("ws://localhost:%d", port);
    logger.debug("New WS address: {}", websocketAddress);
    zmqContext = createContext();
    JsonRpcRequestServer dispatcher =
        new JsonRpcRequestServer(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "JsonRpcRequestServer.service",
            websocketAddress,
            DEALER_ADDRESS);
    logger.debug("Created JsonRpcRequestServer service");
    Set<Service> services = new HashSet<>(Collections.singletonList(dispatcher));
    manager = new ServiceManager(services);
    execService = Executors.newSingleThreadExecutor();

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);

    // start ws client
    logger.debug("Starting WsClient");
    webSocketClient = new WsClient(new URI(websocketAddress));
    boolean ok = webSocketClient.connectBlocking(3, TimeUnit.SECONDS);
    assertTrue("Websocket connection failed", ok);
    logger.debug("Ws client connected");

    // start worker
    rpcMessageInvoker = new Worker(zmqContext, DEALER_ADDRESS);
    execService.execute(rpcMessageInvoker);
  }

  @After
  public void cleanup() throws Exception {
    if (webSocketClient != null) {
      webSocketClient.close();
    }
    if (manager != null) {
      manager.stopAsync().awaitStopped(10, TimeUnit.SECONDS);
    }
    if (execService != null) {
      execService.shutdownNow();
      execService.awaitTermination(10, TimeUnit.SECONDS);
    }
    if (zmqContext != null) {
      closeContext(zmqContext);
    }
  }

  private JsonRpcRequest createJsonRpcRequest(String method, String type, UUID id) {
    return new JsonRpcRequest.Builder()
        .withId(id.toString())
        .withMethod(method)
        .withParams(new Params.Builder().withType(type).build())
        .build();
  }

  @Test
  public void sendJsonRpcRequest()
      throws InterruptedException, ExecutionException, JsonSerializationException {

    // create and send request
    UUID requestId = UUID.randomUUID();
    JsonRpcRequest jsonRpcRequest =
        createJsonRpcRequest("new", "java.lang.StringBuilder", requestId);
    CompletableFuture<JsonRpcResponse> jsonRpcResponseFuture =
        webSocketClient.sendAsync(jsonRpcRequest);

    // wait for response
    jsonRpcResponseFuture.get();
    assertNotNull(jsonRpcResponseFuture.get().getResult());
    assertTrue(jsonRpcResponseFuture.get().getResult().getIsVoid());

    // check received message ids
    assertThat(rpcMessageInvoker.getReceivedMessageIds().size(), is(1));
    assertTrue(rpcMessageInvoker.getReceivedMessageIds().contains(requestId.toString()));
  }

  @Test
  public void sendManyJsonRpcRequest()
      throws InterruptedException, ExecutionException, JsonSerializationException {

    int requestsCount = 100;
    List<CompletableFuture<JsonRpcResponse>> jsonRpcResponseFutures = new ArrayList<>();
    List<UUID> sentRequestIds = new ArrayList<>();

    // create and send requests
    for (int i = 0; i < requestsCount; i++) {
      UUID requestId = UUID.randomUUID();
      sentRequestIds.add(requestId);
      JsonRpcRequest jsonRpcRequest = createJsonRpcRequest("new", "java.lang.String", requestId);
      jsonRpcResponseFutures.add(webSocketClient.sendAsync(jsonRpcRequest));
    }

    // wait for responses
    for (CompletableFuture<JsonRpcResponse> jsonRpcResponseFuture : jsonRpcResponseFutures) {
      jsonRpcResponseFuture.get();
      assertNotNull(jsonRpcResponseFuture.get().getResult());
      assertTrue(jsonRpcResponseFuture.get().getResult().getIsVoid());
    }

    // check message ids received by worker
    assertThat(rpcMessageInvoker.getReceivedMessageIds().size(), is(requestsCount));
    assertThat(
        rpcMessageInvoker.getReceivedMessageIds(),
        containsInAnyOrder(sentRequestIds.stream().map(UUID::toString).toArray()));
  }

  private static class Worker implements Runnable {
    private final ZMQ.Socket socket;
    private final String dealerAddress;
    private final Set<String> receivedMsgIds = new TreeSet<>();

    Worker(ZContext context, String dealerAddress) {
      this.dealerAddress = dealerAddress;
      this.socket = context.createSocket(SocketType.REP);
    }

    @Override
    public void run() {
      // connect to dealer
      this.socket.connect(this.dealerAddress);

      // process requests
      while (!Thread.interrupted()) {
        InboundJsonRpcRequestMsg rpcRequestMsg;
        try {
          // receive request
          rpcRequestMsg = InboundJsonRpcRequestMsg.receive(socket, true);
          assert rpcRequestMsg != null;

          // parse and validate request
          JsonRpcRequest jsonRpcRequest =
              parseAndValidateJsonRpcMessage(rpcRequestMsg.getJsonMessage());

          // store received message id
          receivedMsgIds.add(jsonRpcRequest.getId());

          // send back response
          final JsonRpcResponse jsonRpcResponse =
              new JsonRpcResponse.Builder()
                  .withId(jsonRpcRequest.getId())
                  .withResult(new JsonRpcResponseReturnValue.Builder().withIsVoid(true).build())
                  .build();
          String responseAsJson = JsonRpcSerializer.toJson(jsonRpcResponse);
          new OutboundJsonRpcResponseMsg(
                  rpcRequestMsg.getPeerId(), responseAsJson, MessageType.EXEC_RETURN_VALUE)
              .send(socket);
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            logger.warn("context terminated");
            break;
          } else if (errorCode == ZError.EINTR) {
            logger.warn("interrupted during receive()");
            break;
          } else {
            logger.error("unexpected error during receive()", ex);
            throw ex;
          }
        } catch (Exception e) {
          logger.error("error processing received message", e);
        }
      }

      this.socket.close();
    }

    Set<String> getReceivedMessageIds() {
      return receivedMsgIds;
    }
  }

  private static final class WsClient extends WebSocketClient {
    private final Map<String, CompletableFuture<JsonRpcResponse>> futureResponses =
        new ConcurrentHashMap<>();

    WsClient(URI serverUri) {
      super(serverUri);
    }

    public CompletableFuture<JsonRpcResponse> sendAsync(JsonRpcRequest jsonRpcRequest)
        throws JsonSerializationException {
      if (logger.isDebugEnabled()) {
        logger.debug("sending message to ws socket: {}", jsonRpcRequest);
      }
      while (this.getReadyState() != ReadyState.OPEN) {
        try {
          //noinspection BusyWait
          Thread.sleep(50);
        } catch (InterruptedException e) {
          logger.error("error waiting for ws connection to open", e);
        }
      }
      CompletableFuture<JsonRpcResponse> futureResponse = new CompletableFuture<>();
      futureResponses.put(jsonRpcRequest.getId(), futureResponse);
      send(JsonRpcSerializer.toJson(jsonRpcRequest));
      return futureResponse;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
      logger.info("WS connection opened");
    }

    @Override
    public void onMessage(String message) {
      logger.info("WS received message: {}", message);
      JsonRpcResponse jsonRpcResponse;
      try {
        jsonRpcResponse = JsonRpcSerializer.fromJson(message, JsonRpcResponse.class);
      } catch (JsonSerializationException e) {
        logger.error("error deserializing json message", e);
        throw new RuntimeException(e);
      }
      CompletableFuture<JsonRpcResponse> futureResponse =
          futureResponses.remove(jsonRpcResponse.getId());
      if (futureResponse != null) {
        futureResponse.complete(jsonRpcResponse);
      } else {
        logger.error("no future response found for message id: {}", jsonRpcResponse.getId());
      }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
      logger.info("WS connection closed");
    }

    @Override
    public void onError(Exception ex) {
      logger.error("WS error", ex);
    }
  }
}
