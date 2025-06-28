/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.quasient.pal.core.RunOptions;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.messages.InboundJsonRpcRequestMsg;
import com.quasient.pal.core.messages.OutboundJsonRpcResponseMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
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
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class RpcMessageInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private static final String RPC_DEALER_ADDRESS = "inproc://deal";
  private static final String JSONRPC_DEALER_ADDRESS = "inproc://json.deal";
  private ZContext context;
  private Socket rpcDealerSocket;
  private Socket jsonRpcDealerSocket;
  private static final Gson gson = new Gson();
  private ExecutorService execService;
  private RpcMessageInvoker rpcMessageInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate RPCRequestDispatcher's DEALER socket
    this.rpcDealerSocket = context.createSocket(SocketType.DEALER);
    rpcDealerSocket.bind(RPC_DEALER_ADDRESS);
    // simulate JSONRPCRequestDispatcher's DEALER socket
    this.jsonRpcDealerSocket = context.createSocket(SocketType.DEALER);
    jsonRpcDealerSocket.bind(JSONRPC_DEALER_ADDRESS);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall to return a message which seems valid response
    when(incomingMessageDispatcher.incomingCall(any(), any(), anyBoolean()))
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
                      peerUuid, "", constructor, null, false, incomingMsg.getMessageId());
                });

    this.rpcMessageInvoker =
        new RpcMessageInvoker(
            context,
            msgBuilder,
            new HashSet<>(Arrays.asList(RunOptions.WITH_RPC, RunOptions.WITH_JSONRPC)),
            RPC_DEALER_ADDRESS,
            JSONRPC_DEALER_ADDRESS,
            incomingMessageDispatcher,
            peerUuid);
  }

  @After
  public void cleanup() throws Exception {
    rpcMessageInvoker.closeConnections();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.debug("execService shut down");
  }

  @Test
  public void invokeRpcMessage() {

    // start invoker thread
    execService.execute(rpcMessageInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    rpcMessageInvoker.addMessageDispatchListener(dispatchListener);

    // deal msg
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message wrapper = msgBuilder.wrap(invokable);
    rpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
    rpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
    // get response
    rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
    Message responseMessage = new Message();
    responseMessage.unmarshal(rpcDealerSocket.recv(), 0);

    assertThat(rpcMessageInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(rpcMessageInvoker.getRequestsDispatched(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), anyBoolean());

    // assert response msg is response to original
    assertThat(responseMessage.getExecMessage().getResponseToId(), is(invokable.getMessageId()));
  }

  @Test
  public void invokeJsonRpcMessage() {

    // start invoker thread
    execService.execute(rpcMessageInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    rpcMessageInvoker.addMessageDispatchListener(dispatchListener);

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
    assertThat(rpcMessageInvoker.getExecRequestsDispatched(), is(1L));
    assertThat(rpcMessageInvoker.getRequestsDispatched(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), anyBoolean());

    // assert response msg is response to original
    assertThat(jsonRpcResponse.getId(), is(requestUuid.toString()));
  }

  @Test
  public void invokeManyRpcMessages() {

    // start invoker thread
    execService.execute(rpcMessageInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    rpcMessageInvoker.addMessageDispatchListener(dispatchListener);

    // deal messages
    int msgCount = 10;
    List<ExecMessage> messagesToInvoke = new ArrayList<>();
    List<ExecMessage> responseMessages = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      // deal msg
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      Message wrapper = msgBuilder.wrap(invokable);
      rpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
      rpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
      messagesToInvoke.add(invokable);
      // get response
      rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
      Message msg = new Message();
      msg.unmarshal(rpcDealerSocket.recv(), 0);
      ExecMessage response = msg.getExecMessage();
      responseMessages.add(response);
    }

    // assert number of calls
    assertThat(rpcMessageInvoker.getExecRequestsDispatched(), is((long) msgCount));
    assertThat(rpcMessageInvoker.getRequestsDispatched(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), anyBoolean());

    // assert response msg is response to original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          responseMessages.get(i).getResponseToId(), is(messagesToInvoke.get(i).getMessageId()));
    }
  }

  @Test
  public void invokeManyJsonRpcMessages() {

    // start invoker thread
    execService.execute(rpcMessageInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    rpcMessageInvoker.addMessageDispatchListener(dispatchListener);

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
    assertThat(rpcMessageInvoker.getExecRequestsDispatched(), is((long) msgCount));
    assertThat(rpcMessageInvoker.getRequestsDispatched(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), anyBoolean());
  }
}
