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

package net.ittera.pal.core.rpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
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
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import net.ittera.pal.core.rpc.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.Params;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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

    // stub incomingCall to return a message which seems valid reply
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
  public void invokeRpcMessage() throws Exception {

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
    // get reply
    rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
    Message replyMsg = new Message();
    replyMsg.unmarshal(rpcDealerSocket.recv(), 0);

    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), anyBoolean());

    // assert reply msg is response to original
    assertThat(replyMsg.getExecMessage().getResponseToId(), is(invokable.getMessageId()));
  }

  @Test
  public void invokeJsonRpcMessage() throws Exception {

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

    // get reply
    OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
        OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
    assert outboundJsonRpcResponseMsg != null;
    final String jsonRpcResponseAsString = outboundJsonRpcResponseMsg.getJsonMessage();
    final JsonRpcResponse jsonRpcResponse =
        gson.fromJson(jsonRpcResponseAsString, JsonRpcResponse.class);

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is(1L));
    assertThat(listenerReceived.get(), is(1));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), any(), anyBoolean());

    // assert reply msg is response to original
    assertThat(jsonRpcResponse.getId(), is(requestUuid.toString()));
  }

  @Test
  public void invokeManyRpcMessages() throws Exception {

    // start invoker thread
    execService.execute(rpcMessageInvoker);

    // add a message dispatch listener
    AtomicInteger listenerReceived = new AtomicInteger(0);
    MessageDispatchListener dispatchListener = message -> listenerReceived.incrementAndGet();
    rpcMessageInvoker.addMessageDispatchListener(dispatchListener);

    // deal messages
    int msgCount = 10;
    List<ExecMessage> messagesToInvoke = new ArrayList<>();
    List<ExecMessage> replyMessages = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      // deal msg
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      Message wrapper = msgBuilder.wrap(invokable);
      rpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
      rpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
      messagesToInvoke.add(invokable);
      // get reply
      rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
      Message msg = new Message();
      msg.unmarshal(rpcDealerSocket.recv(), 0);
      ExecMessage reply = msg.getExecMessage();
      replyMessages.add(reply);
    }

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), anyBoolean());

    // assert reply msg is response to original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          replyMessages.get(i).getResponseToId(), is(messagesToInvoke.get(i).getMessageId()));
    }
  }

  @Test
  public void invokeManyJsonRpcMessages() throws Exception {

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

      // get reply
      OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
          OutboundJsonRpcResponseMsg.receive(jsonRpcDealerSocket, true);
      assert outboundJsonRpcResponseMsg != null;
      gson.fromJson(outboundJsonRpcResponseMsg.getJsonMessage(), JsonRpcResponse.class);
    }

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is((long) msgCount));
    assertThat(listenerReceived.get(), is(msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), any(), anyBoolean());
  }
}
