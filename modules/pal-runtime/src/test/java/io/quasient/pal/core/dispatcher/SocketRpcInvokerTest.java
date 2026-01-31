/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import io.quasient.pal.core.internal.messages.OutboundJsonRpcResponseMsg;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class SocketRpcInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private static final String ZMQRPC_DEALER_ADDRESS = "inproc://zmq.deal";
  private static final String JSONRPC_DEALER_ADDRESS = "inproc://json.deal";
  private ZContext context;
  private Socket zmqRpcDealerSocket;
  private Socket jsonRpcDealerSocket;
  private static final Gson gson = new Gson();
  private ExecutorService execService;
  private SocketRpcInvoker socketRpcInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder(peerUuid);

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate RPCRequestDispatcher's DEALER socket
    this.zmqRpcDealerSocket = context.createSocket(SocketType.DEALER);
    zmqRpcDealerSocket.bind(ZMQRPC_DEALER_ADDRESS);
    // simulate JSONRPCRequestDispatcher's DEALER socket
    this.jsonRpcDealerSocket = context.createSocket(SocketType.DEALER);
    jsonRpcDealerSocket.bind(JSONRPC_DEALER_ADDRESS);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall to return a message which seems valid response
    when(incomingMessageDispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            (Answer<?>)
                invocation -> {
                  Object[] args = invocation.getArguments();
                  ExecMessage incomingMsg = (ExecMessage) args[0];
                  Constructor<?> constructor = null;
                  try {
                    constructor = String.class.getConstructor();
                  } catch (NoSuchMethodException e) {
                    logger.error("Error getting constructor", e);
                  }
                  return msgBuilder.buildReturnValue(
                      "", constructor, null, false, incomingMsg.getMessageId());
                });

    this.socketRpcInvoker =
        new SocketRpcInvoker(
            context,
            msgBuilder,
            new HashSet<>(Arrays.asList(RunOptions.WITH_ZMQ_RPC, RunOptions.WITH_JSON_RPC)),
            ZMQRPC_DEALER_ADDRESS,
            JSONRPC_DEALER_ADDRESS,
            incomingMessageDispatcher,
            peerUuid);
  }

  @After
  public void cleanup() throws Exception {
    socketRpcInvoker.closeConnections();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.debug("execService shut down");
  }

  @Test
  public void invokeRpcMessage() {

    // start invoker thread
    execService.execute(socketRpcInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    socketRpcInvoker.addMessageDispatchListener(dispatchListener);

    // deal msg
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message wrapper = msgBuilder.wrap(invokable);
    zmqRpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
    zmqRpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
    // get response
    zmqRpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
    Message responseMessage = new Message();
    responseMessage.unmarshal(zmqRpcDealerSocket.recv(), 0);

    assertThat(socketRpcInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(socketRpcInvoker.getRequestsDispatched(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());

    // assert response msg is response to original
    assertThat(responseMessage.getExecMessage().getResponseToId(), is(invokable.getMessageId()));
  }

  @Test
  public void invokeJsonRpcMessage() {

    // start invoker thread
    execService.execute(socketRpcInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    socketRpcInvoker.addMessageDispatchListener(dispatchListener);

    // create new JSON-RPC request
    final UUID requestUuid = UUID.randomUUID();
    JsonRpcRequest request =
        new JsonRpcRequest.Builder()
            .withMethod("new")
            .withId(requestUuid.toString())
            .withParams(new Params.Builder().withType("java.lang.String").build())
            .build();
    final UUID clientId = UUID.randomUUID();

    // deal msg
    String jsonRpcRequestAsString = gson.toJson(request);
    InboundJsonRpcRequestMsg inboundJsonRpcRequestMsg =
        new InboundJsonRpcRequestMsg(clientId, jsonRpcRequestAsString);
    boolean sentOk = inboundJsonRpcRequestMsg.send(jsonRpcDealerSocket);
    if (!sentOk) {
      throw new RuntimeException("Error sending JSON-RPC message");
    }

    // get response
    OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
        OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
    assert outboundJsonRpcResponseMsg != null;
    final String jsonRpcResponseAsString = outboundJsonRpcResponseMsg.getJsonMessage();
    final JsonRpcResponse jsonRpcResponse =
        gson.fromJson(jsonRpcResponseAsString, JsonRpcResponse.class);

    // assert number of calls
    assertThat(socketRpcInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(socketRpcInvoker.getRequestsDispatched(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), any());

    // assert response msg is response to original
    assertThat(jsonRpcResponse.getId(), is(requestUuid.toString()));
  }

  @Test
  public void invokeManyColferRpcMessages() {

    // start invoker thread
    execService.execute(socketRpcInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    socketRpcInvoker.addMessageDispatchListener(dispatchListener);

    // deal messages
    int msgCount = 10;
    List<ExecMessage> messagesToInvoke = new ArrayList<>();
    List<ExecMessage> responseMessages = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      // deal msg
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      Message wrapper = msgBuilder.wrap(invokable);
      zmqRpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
      zmqRpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
      messagesToInvoke.add(invokable);
      // get response
      zmqRpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
      Message msg = new Message();
      msg.unmarshal(zmqRpcDealerSocket.recv(), 0);
      ExecMessage response = msg.getExecMessage();
      responseMessages.add(response);
    }

    // assert number of calls
    assertThat(socketRpcInvoker.getExecRequestsDispatched(), is((long) msgCount));
    assertThat(socketRpcInvoker.getRequestsDispatched(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), any());

    // assert response msg is response to original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          responseMessages.get(i).getResponseToId(), is(messagesToInvoke.get(i).getMessageId()));
    }
  }

  @Test
  public void invokeManyJsonRpcMessages() {

    // start invoker thread
    execService.execute(socketRpcInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    socketRpcInvoker.addMessageDispatchListener(dispatchListener);

    // deal messages
    int msgCount = 10;
    for (int i = 0; i < msgCount; i++) {
      // create JSON-RPC request
      final UUID requestUuid = UUID.randomUUID();
      JsonRpcRequest request =
          new JsonRpcRequest.Builder()
              .withId(requestUuid.toString())
              .withMethod("new")
              .withParams(new Params.Builder().withType("java.lang.String").build())
              .build();
      final UUID clientId = UUID.randomUUID();
      // deal msg
      String jsonRpcRequestAsString = gson.toJson(request);
      InboundJsonRpcRequestMsg inboundJsonRpcRequestMsg =
          new InboundJsonRpcRequestMsg(clientId, jsonRpcRequestAsString);
      boolean sentOk = inboundJsonRpcRequestMsg.send(jsonRpcDealerSocket);
      if (!sentOk) {
        throw new RuntimeException("Error sending JSON-RPC message");
      }

      // get response
      OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
          OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
      assert outboundJsonRpcResponseMsg != null;
      gson.fromJson(outboundJsonRpcResponseMsg.getJsonMessage(), JsonRpcResponse.class);
    }

    // assert number of calls
    assertThat(socketRpcInvoker.getExecRequestsDispatched(), is((long) msgCount));
    assertThat(socketRpcInvoker.getRequestsDispatched(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), any());
  }

  @Test
  public void jsonRpc_invalidJson_returnsError_noDispatch() {
    execService.execute(socketRpcInvoker);
    // malformed json (id present to test id extraction path)
    String badJson =
        "{\"jsonrpc\":\"2.0\",\"id\":\"123\",\"method\":\"new\""; // missing closing brace
    InboundJsonRpcRequestMsg inbound = new InboundJsonRpcRequestMsg(UUID.randomUUID(), badJson);
    inbound.send(jsonRpcDealerSocket);

    OutboundJsonRpcResponseMsg resp = OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
    assert resp != null;
    JsonRpcResponse json = gson.fromJson(resp.getJsonMessage(), JsonRpcResponse.class);
    // error present, result null, and dispatcher not called
    assertThat(json.getError() != null, is(true));
    verify(incomingMessageDispatcher, times(0)).incomingCall(any(), any(), any());
  }

  @Test
  public void jsonRpc_unsupportedMethod_returnsError_noDispatch() {
    execService.execute(socketRpcInvoker);
    // send raw JSON with unsupported method (bypass builder validation)
    String raw =
        String.format(
            "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"unknown\",\"params\":{\"type\":\"java.lang.String\"}}",
            UUID.randomUUID());
    InboundJsonRpcRequestMsg inbound = new InboundJsonRpcRequestMsg(UUID.randomUUID(), raw);
    inbound.send(jsonRpcDealerSocket);

    OutboundJsonRpcResponseMsg resp = OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
    assert resp != null;
    JsonRpcResponse json = gson.fromJson(resp.getJsonMessage(), JsonRpcResponse.class);
    assertThat(json.getError() != null, is(true));
    verify(incomingMessageDispatcher, times(0)).incomingCall(any(), any(), any());
  }

  // ==========================================================================
  // Test specifications for Issue #476 - Awaiting implementation in #477
  // ==========================================================================

  /**
   * Test specification: META message handling via JSON-RPC.
   *
   * <p>Given: JSON-RPC request for META service (e.g., "describe" method)
   *
   * <p>When: Request is dispatched through the JSON-RPC socket
   *
   * <p>Then: MetaMessageDispatcher should be invoked and return class metadata response
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void dispatchJsonRpcRequest_metaMessage_handledCorrectly() {
    // Given: JSON-RPC request for META service
    // When: Request dispatched
    // Then: MetaMessageDispatcher invoked; response returned

    // TODO(#477): Implement test logic
    // - Create a JSON-RPC request with method "describe" and params containing type name
    // - Configure mock incomingMessageDispatcher to return valid MetaMessage response
    // - Send request via jsonRpcDealerSocket
    // - Verify response contains class metadata
    // - Verify MetaMessageDispatcher was invoked (via incomingCall)
    fail("Not yet implemented");
  }

  /**
   * Test specification: CONTROL message handling via JSON-RPC.
   *
   * <p>Given: JSON-RPC request for CONTROL message (e.g., session control)
   *
   * <p>When: Request is dispatched through the JSON-RPC socket
   *
   * <p>Then: ControlMessageDispatcher should be invoked and return session response
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void dispatchJsonRpcRequest_controlMessage_handledCorrectly() {
    // Given: JSON-RPC request for CONTROL message
    // When: Request dispatched
    // Then: ControlMessageDispatcher invoked; response returned

    // TODO(#477): Implement test logic
    // - Create a JSON-RPC request with CONTROL method (e.g., session management)
    // - Configure mock incomingMessageDispatcher to return valid ControlMessage response
    // - Send request via jsonRpcDealerSocket
    // - Verify response contains control message result
    // - Verify ControlMessageDispatcher was invoked
    fail("Not yet implemented");
  }

  /**
   * Test specification: Dispatch exception returns error response.
   *
   * <p>Given: IncomingMessageDispatcher that throws exception during dispatch
   *
   * <p>When: JSON-RPC request is dispatched
   *
   * <p>Then: JSON-RPC error response should be returned with appropriate error details
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void dispatchJsonRpcRequest_dispatchException_returnsErrorResponse() {
    // Given: IncomingMessageDispatcher that throws exception
    // When: Request dispatched
    // Then: JSON-RPC error response returned

    // TODO(#477): Implement test logic
    // - Configure incomingMessageDispatcher.incomingCall() to throw RuntimeException
    // - Create valid JSON-RPC EXEC request
    // - Send request via jsonRpcDealerSocket
    // - Verify response contains error object with exception details
    // - Verify result is null
    fail("Not yet implemented");
  }

  /**
   * Test specification: AROUND intercept callback with proceed() call.
   *
   * <p>Given: AROUND BEFORE intercept callback request
   *
   * <p>When: Callback handler calls ctx.proceed() to continue execution
   *
   * <p>Then: Original method should execute, AFTER phase should complete, final response returned
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void handleAroundInterceptCallback_proceedCalled_executesChain() {
    // Given: AROUND BEFORE intercept request
    // When: Callback calls proceed()
    // Then: Original method executed; AFTER phase completes

    // TODO(#477): Implement test logic
    // - Create InterceptCallbackRequestMessage with AROUND type and BEFORE phase
    // - Configure mock incomingMessageDispatcher.incomingAroundInterceptCallback() to:
    //   - Invoke the AroundSocketAccessor.sendAndReceiveAfterPhase()
    //   - Return final InterceptCallbackResponseMessage
    // - Send the AROUND BEFORE request via zmqRpcDealerSocket
    // - Send AFTER phase request in response to BEFORE response
    // - Verify final response has AFTER phase data
    fail("Not yet implemented");
  }

  /**
   * Test specification: AROUND intercept callback timeout.
   *
   * <p>Given: AROUND callback that doesn't respond within timeout
   *
   * <p>When: Timeout is exceeded while waiting for AFTER phase
   *
   * <p>Then: AroundTimeoutException should be raised and error response returned
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void handleAroundInterceptCallback_proceedTimeout_returnsError() {
    // Given: AROUND callback that doesn't respond
    // When: Timeout exceeded
    // Then: Timeout error returned

    // TODO(#477): Implement test logic
    // - Create InterceptCallbackRequestMessage with AROUND type and BEFORE phase
    // - Configure mock incomingMessageDispatcher.incomingAroundInterceptCallback() to:
    //   - Invoke AroundSocketAccessor.sendAndReceiveAfterPhase() which times out
    //   - Throw AroundTimeoutException
    // - Send the AROUND BEFORE request via zmqRpcDealerSocket
    // - Do NOT send AFTER phase request (simulate timeout)
    // - Verify error response contains timeout information
    fail("Not yet implemented");
  }

  /**
   * Test specification: Socket exception with non-terminal error is rethrown.
   *
   * <p>Given: ZMQException with non-terminal error code (not ETERM or EINTR)
   *
   * <p>When: handleSocketException is called with this exception
   *
   * <p>Then: Exception should be rethrown rather than handled gracefully
   *
   * <p>Note: This test verifies the rethrow path in handleSocketException. Existing test
   * SocketRpcInvokerHandleExceptionTest covers ETERM/EINTR cases, this covers the rethrow case.
   */
  @Test
  @Ignore("Awaiting implementation in #477")
  public void handleSocketException_otherError_rethrows() {
    // Given: ZMQException with non-terminal error
    // When: handleSocketException called
    // Then: Exception rethrown

    // TODO(#477): Implement test logic
    // - Use reflection to access private handleSocketException method
    // - Call with ZMQException containing EFAULT or other non-terminal error code
    // - Verify ZMQException is thrown (not caught/handled)
    // Note: SocketRpcInvokerHandleExceptionTest already tests this case but
    // this spec documents the requirement explicitly for coverage
    fail("Not yet implemented");
  }
}
