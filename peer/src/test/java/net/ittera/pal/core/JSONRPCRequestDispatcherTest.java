package net.ittera.pal.core;

import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResult;
import net.ittera.pal.serdes.colfer.JSONSerializers;
import net.ittera.pal.serdes.jsonrpc.JsonRpcResponseDeserializer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

public class JSONRPCRequestDispatcherTest extends ZmqEnabledTest {
  static class Worker implements Runnable {
    private final ZMQ.Socket socket;
    private final ZContext context;
    private final String dealerAddress;
    private final Set<String> receivedMsgIds = new TreeSet<>();
    private Gson gson;

    Worker(ZContext context, String dealerAddress) {
      this.context = context;
      this.dealerAddress = dealerAddress;
      this.socket = this.context.createSocket(SocketType.REP);
      initGson();
    }

    private void initGson() {
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
          rpcRequestMsg = InboundJsonRpcRequestMsg.recvMsg(socket, true);
          UUID clientId = rpcRequestMsg.getClientId();

          // parse and validate request
          JsonRpcRequest jsonRpcRequest =
              parseAndValidateJsonRpcMessage(rpcRequestMsg.getJsonMessage());

          // store received message id
          receivedMsgIds.add(jsonRpcRequest.getId());

          // send back response
          final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();
          jsonRpcResponse.setId(jsonRpcRequest.getId());
          jsonRpcResponse.setJsonrpc("2.0");
          ReturnValue returnValue = new ReturnValue();
          returnValue.setIsVoid(true);
          jsonRpcResponse.setResult(new JsonRpcResult(returnValue));
          String responseAsJson = this.gson.toJson(jsonRpcResponse);
          new OutboundJsonRpcResponseMsg(clientId, responseAsJson).send(socket);
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            logger.warn("context terminated");
            break;
          } else if (errorCode == ZError.EINTR) {
            logger.warn("interrupted during recv()");
            break;
          } else {
            logger.error("unexpected error during recv()", ex);
            throw (ex);
          }
        } catch (Exception e) {
          logger.error("error processing received message", e);
        }
      }

      this.socket.close();
      this.context.close();
    }

    Set<String> getReceivedMessageIds() {
      return receivedMsgIds;
    }
  }

  private static final class WSClient extends WebSocketClient {
    private final Map<String, CompletableFuture<JsonRpcResponse>> futureResponses =
        new ConcurrentHashMap<>();
    private Gson gson;

    WSClient(URI serverUri) {
      super(serverUri);
      initGson();
    }

    private void initGson() {
      // initialize gson, registering custom adapters for JSON-RPC Response messages
      gson =
          new GsonBuilder()
              .registerTypeAdapter(
                  StaticFieldPutDone.class, new JSONSerializers.StaticFieldPutDoneAdapter())
              .registerTypeAdapter(
                  InstanceFieldPutDone.class, new JSONSerializers.InstanceFieldPutDoneAdapter())
              .registerTypeAdapter(ReturnValue.class, new JSONSerializers.ReturnValueAdapter())
              .registerTypeAdapter(JsonRpcResponse.class, new JsonRpcResponseDeserializer())
              .create();
    }

    public CompletableFuture<JsonRpcResponse> sendAsync(JsonRpcRequest jsonRpcRequest) {
      if (logger.isTraceEnabled()) {
        logger.trace("sending message to ws socket: {}", jsonRpcRequest);
      }
      while (this.getReadyState() != ReadyState.OPEN) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          logger.error("error waiting for ws connection to open", e);
        }
      }
      CompletableFuture<JsonRpcResponse> futureResponse = new CompletableFuture<>();
      futureResponses.put(jsonRpcRequest.getId(), futureResponse);
      send(gson.toJson(jsonRpcRequest));
      return futureResponse;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
      logger.info("WS connection opened");
    }

    @Override
    public void onMessage(String message) {
      logger.info("WS received message: {}", message);
      JsonRpcResponse jsonRpcResponse = gson.fromJson(message, JsonRpcResponse.class);
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

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private ServiceManager manager;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private ExecutorService execService;
  private ZContext zmqContext;
  private WSClient webSocketClient;
  private Worker rpcMessageInvoker;
  private JSONRPCRequestDispatcher dispatcher;

  @Before
  public void setUp() throws URISyntaxException, InterruptedException {
    zmqContext = createContext();
    final String DEALER_ADDR = "inproc://jsonrpc.dealer";
    final String WEBSOCKET_ADDR = String.format("ws://localhost:%d", findAvailableServerPort());
    dispatcher =
        new JSONRPCRequestDispatcher(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "JSONRPCRequestDispatcher.service",
            WEBSOCKET_ADDR,
            DEALER_ADDR);
    Set<Service> services = new HashSet<>(Collections.singletonList(dispatcher));
    manager = new ServiceManager(services);
    execService = Executors.newSingleThreadExecutor();

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);

    // start ws client
    webSocketClient = new WSClient(new URI(WEBSOCKET_ADDR));
    webSocketClient.connectBlocking();

    // start worker
    rpcMessageInvoker = new Worker(zmqContext, DEALER_ADDR);
    execService.submit(rpcMessageInvoker);
  }

  @After
  public void cleanup() throws Exception {
    webSocketClient.close();
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
    execService.shutdownNow();
    execService.awaitTermination(2, TimeUnit.SECONDS);
    closeContext(zmqContext);
  }

  private JsonRpcRequest createJsonRpcRequest(String method, UUID id) {
    JsonRpcRequest request = new JsonRpcRequest();
    request.setJsonrpc("2.0");
    request.setMethod(method);
    request.setId(id.toString());
    request.processMethodParts();
    return request;
  }

  @Test
  public void sendJsonRpcRequest() throws InterruptedException, ExecutionException {

    // create and send request
    UUID requestId = UUID.randomUUID();
    JsonRpcRequest jsonRpcRequest = createJsonRpcRequest("new:java.lang.String", requestId);
    CompletableFuture<JsonRpcResponse> jsonRpcResponseFuture =
        webSocketClient.sendAsync(jsonRpcRequest);

    // wait for response
    jsonRpcResponseFuture.get();
    assertEquals("RETURN_VALUE", jsonRpcResponseFuture.get().getResult().getResultType().name());
    assertTrue(((ReturnValue) jsonRpcResponseFuture.get().getResult().getObject()).getIsVoid());

    // check received message ids
    assertEquals(rpcMessageInvoker.getReceivedMessageIds().size(), 1);
    assertTrue(rpcMessageInvoker.getReceivedMessageIds().contains(requestId.toString()));
  }

  @Test
  public void sendManyJsonRpcRequest() throws InterruptedException, ExecutionException {

    int requestsCount = 100;
    List<CompletableFuture<JsonRpcResponse>> jsonRpcResponseFutures = new ArrayList<>();
    List<UUID> sentRequestIds = new ArrayList<>();

    // create and send requests
    for (int i = 0; i < requestsCount; i++) {
      UUID requestId = UUID.randomUUID();
      sentRequestIds.add(requestId);
      JsonRpcRequest jsonRpcRequest = createJsonRpcRequest("new:java.lang.String", requestId);
      jsonRpcResponseFutures.add(webSocketClient.sendAsync(jsonRpcRequest));
    }

    // wait for responses
    for (CompletableFuture<JsonRpcResponse> jsonRpcResponseFuture : jsonRpcResponseFutures) {
      jsonRpcResponseFuture.get();
      assertEquals("RETURN_VALUE", jsonRpcResponseFuture.get().getResult().getResultType().name());
      assertTrue(((ReturnValue) jsonRpcResponseFuture.get().getResult().getObject()).getIsVoid());
    }

    // check message ids received by worker
    assertEquals(rpcMessageInvoker.getReceivedMessageIds().size(), requestsCount);
    assertThat(
        rpcMessageInvoker.getReceivedMessageIds(),
        containsInAnyOrder(sentRequestIds.stream().map(UUID::toString).toArray()));
  }
}
