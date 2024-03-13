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

package net.ittera.pal.core.exec;

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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
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

public class RPCMessageInvokerTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private Socket rpcDealerSocket;
  private Socket jsonRpcDealerSocket;
  private static final Gson gson = new Gson();
  private ExecutorService execService;
  private RPCMessageInvoker rpcMessageInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder msgBuilder = new MessageBuilder();

  @Before
  public void setup() throws Exception {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    // simulate RPCRequestDispatcher's DEALER socket
    this.rpcDealerSocket = context.createSocket(SocketType.DEALER);
    String RPC_DEALER_ADDR = "inproc://deal";
    rpcDealerSocket.bind(RPC_DEALER_ADDR);
    // simulate JSONRPCRequestDispatcher's DEALER socket
    this.jsonRpcDealerSocket = context.createSocket(SocketType.DEALER);
    String JSONRPC_DEALER_ADDR = "inproc://json.deal";
    jsonRpcDealerSocket.bind(JSONRPC_DEALER_ADDR);

    /* mock incomingMessageDispatcher */
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    // stub incomingCall to return a message which seems valid reply
    when(incomingMessageDispatcher.incomingCall(any(), anyBoolean()))
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
                      peerUuid, "", constructor, null, false, incomingMsg.getMessageUuid());
                });

    this.rpcMessageInvoker =
        new RPCMessageInvoker(
            context,
            msgBuilder,
            new HashSet<>(Arrays.asList(RunOptions.WITH_RPC, RunOptions.WITH_JSONRPC)),
            RPC_DEALER_ADDR,
            JSONRPC_DEALER_ADDR,
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
  public void invokeRPCMessage() throws Exception {

    // start invoker thread
    execService.submit(rpcMessageInvoker);

    // deal msg
    ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message wrapper = msgBuilder.wrap(invokable);
    rpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
    rpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
    // get reply
    rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
    Message msg = new Message();
    msg.unmarshal(rpcDealerSocket.recv(), 0);
    ExecMessage reply = msg.getExecMessage();

    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is(1L));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

    // assert reply msg followsUuid of original
    assertThat(reply.getResponseToUuid(), is(invokable.getMessageUuid()));
  }

  @Test
  public void invokeJSONRPCMessage() throws Exception {

    // start invoker thread
    execService.submit(rpcMessageInvoker);

    // create new JSON-RPC request
    JsonRpcRequest request = new JsonRpcRequest();
    request.setJsonrpc("2.0");
    request.setMethod("new:java.lang.String");
    final UUID requestUuid = UUID.randomUUID();
    final UUID clientId = UUID.randomUUID();
    request.setId(requestUuid.toString());
    request.processMethodParts();

    // deal msg
    String jsonRpcRequestAsString = gson.toJson(request);
    InboundJsonRpcRequestMsg inboundJSONRPCRequestMsg =
        new InboundJsonRpcRequestMsg(clientId, jsonRpcRequestAsString);
    boolean sentOk = inboundJSONRPCRequestMsg.send(jsonRpcDealerSocket);
    if (!sentOk) {
      throw new RuntimeException("Error sending JSON-RPC message");
    }

    // get reply
    OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
        OutboundJsonRpcResponseMsg.recvMsg(jsonRpcDealerSocket, true);
    final String jsonRpcResponseAsString = outboundJsonRpcResponseMsg.getJsonMessage();
    final JsonRpcResponse jsonRpcResponse =
        gson.fromJson(jsonRpcResponseAsString, JsonRpcResponse.class);

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is(1L));
    verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

    // assert reply msg followsUuid of original
    assertThat(jsonRpcResponse.getId(), is(requestUuid.toString()));
  }

  @Test
  public void invokeManyRPCMessages() throws Exception {

    // start invoker thread
    execService.submit(rpcMessageInvoker);

    // deal msgs
    int msgCount = 10;
    List<ExecMessage> msgsToInvoke = new ArrayList<>();
    List<ExecMessage> replyMessages = new ArrayList<>();
    for (int i = 0; i < msgCount; i++) {
      // deal msg
      ExecMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      Message wrapper = msgBuilder.wrap(invokable);
      rpcDealerSocket.send("", ZMQ.SNDMORE); // 1st frame empty to emulate REQ envelope
      rpcDealerSocket.send(ColferUtils.toBytes(wrapper), 0);
      msgsToInvoke.add(invokable);
      // get reply
      rpcDealerSocket.recv(); // 1st frame empty to emulate REP envelope
      Message msg = new Message();
      msg.unmarshal(rpcDealerSocket.recv(), 0);
      ExecMessage reply = msg.getExecMessage();
      replyMessages.add(reply);
    }

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is((long) msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());

    // assert reply msg followsUuid of original
    for (int i = 0; i < msgCount; i++) {
      assertThat(
          replyMessages.get(i).getResponseToUuid(), is(msgsToInvoke.get(i).getMessageUuid()));
    }
  }

  @Test
  public void invokeManyJSONRPCMessages() throws Exception {

    // start invoker thread
    execService.submit(rpcMessageInvoker);

    // deal msgs
    int msgCount = 10;
    for (int i = 0; i < msgCount; i++) {
      // create JSON-RPC request
      JsonRpcRequest request = new JsonRpcRequest();
      request.setJsonrpc("2.0");
      request.setMethod("new:java.lang.String");
      final UUID requestUuid = UUID.randomUUID();
      final UUID clientId = UUID.randomUUID();
      request.setId(requestUuid.toString());
      request.processMethodParts();
      // deal msg
      String jsonRpcRequestAsString = gson.toJson(request);
      InboundJsonRpcRequestMsg inboundJSONRPCRequestMsg =
          new InboundJsonRpcRequestMsg(clientId, jsonRpcRequestAsString);
      boolean sentOk = inboundJSONRPCRequestMsg.send(jsonRpcDealerSocket);
      if (!sentOk) {
        throw new RuntimeException("Error sending JSON-RPC message");
      }

      // get reply
      OutboundJsonRpcResponseMsg outboundJsonRpcResponseMsg =
          OutboundJsonRpcResponseMsg.recvMsg(jsonRpcDealerSocket, true);
      gson.fromJson(outboundJsonRpcResponseMsg.getJsonMessage(), JsonRpcResponse.class);
    }

    // assert number of calls
    assertThat(rpcMessageInvoker.getRequestsDispatched().get(), is((long) msgCount));
    verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());
  }
}
