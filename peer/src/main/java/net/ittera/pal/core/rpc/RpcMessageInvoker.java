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

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getMessageId;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;

import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import net.ittera.pal.serdes.jsonrpc.JsonRpcRequestException;
import net.ittera.pal.serdes.jsonrpc.JsonRpcSerializer;
import net.ittera.pal.serdes.jsonrpc.JsonSerializationException;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQException;
import zmq.ZError;

class RpcMessageInvoker extends AbstractMessageInvokerThread {

  private final Set<RunOptions> runOptions;
  private final String rpcDealerAddress;
  private ZMQ.Socket rpcSocket;
  private final String jsonrpcDealerAddress;
  private ZMQ.Socket jsonrpcSocket;
  private Poller poller;

  private int rpcSocketIndex = -1;
  private int jsonrpcSocketIndex = -1;

  public RpcMessageInvoker(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      Set<RunOptions> runOptions,
      String rpcDealerAddress,
      String jsonrpcDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(
        group,
        name,
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
    this.runOptions = runOptions;
    this.rpcDealerAddress = rpcDealerAddress;
    this.jsonrpcDealerAddress = jsonrpcDealerAddress;
  }

  // Constructor for unit-testing
  RpcMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      Set<RunOptions> runOptions,
      String rpcDealerAddress,
      String jsonrpcDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    this.runOptions = runOptions;
    this.rpcDealerAddress = rpcDealerAddress;
    this.jsonrpcDealerAddress = jsonrpcDealerAddress;
  }

  @Override
  public void run() {

    Poller poller = setupPoller();
    boolean socketError = false;
    logger.debug("Start getting requests from sockets");

    while (!interrupted() && !socketError) {
      int signaled = pollSockets(poller);

      if (signaled < 1) {
        if (logger.isInfoEnabled()) {
          logger.info("Poller returned from poll with {} sockets ready. Breaking out.", signaled);
        }
        break;
      }

      try {
        handleRpcRequest();
        handleJsonRpcRequest();
      } catch (ZMQException ex) {
        socketError = handleSocketException(ex);
      }
    }

    poller.close();
    closeConnections();
  }

  private Poller setupPoller() {
    poller = zmqContext.createPoller(2);
    setupRpcSocket(poller);
    setupJsonRpcSocket(poller);
    return poller;
  }

  private int pollSockets(Poller poller) {
    try {
      return poller.poll();
    } catch (ZError.IOException e) {
      if (e.getCause() instanceof ClosedChannelException) {
        if (logger.isDebugEnabled()) {
          logger.debug("Caught ClosedChannelException during poll. Breaking out.");
        }
        return -1;
      } else {
        logger.error("Caught unexpected ZError exception during poll", e);
        throw e;
      }
    }
  }

  private void setupRpcSocket(Poller poller) {
    if (runOptions.contains(RunOptions.WITH_RPC)) {
      rpcSocket = zmqContext.createSocket(SocketType.REP);
      boolean rpcSocketConnected = rpcSocket.connect(rpcDealerAddress);
      if (rpcSocketConnected) {
        if (logger.isDebugEnabled()) {
          logger.debug("Connected to RPC dealer at {}", rpcDealerAddress);
        }
        rpcSocketIndex = poller.register(rpcSocket, Poller.POLLIN);
      } else {
        logger.error("Failed to connect to RPC dealer at {}", rpcDealerAddress);
      }
    }
  }

  private void setupJsonRpcSocket(Poller poller) {
    if (runOptions.contains(RunOptions.WITH_JSONRPC)) {
      jsonrpcSocket = zmqContext.createSocket(SocketType.REP);
      boolean jsonrpcSocketConnected = jsonrpcSocket.connect(jsonrpcDealerAddress);
      if (jsonrpcSocketConnected) {
        if (logger.isDebugEnabled()) {
          logger.debug("Connected to JSON-RPC dealer at {}", jsonrpcDealerAddress);
        }
        jsonrpcSocketIndex = poller.register(jsonrpcSocket, Poller.POLLIN);
      } else {
        logger.error("Failed to connect to JSON-RPC dealer at {}", jsonrpcDealerAddress);
      }
    }
  }

  private void handleRpcRequest() {
    if (rpcSocketIndex != -1 && poller.pollin(rpcSocketIndex)) {
      byte[] rpcReq = rpcSocket.recv(0);
      if (rpcReq != null) {
        dispatchRpcRequest(rpcReq);
      }
    }
  }

  private void dispatchRpcRequest(byte[] rpcReq) {

    final long started = System.currentTimeMillis();
    final Message requestMsg = new Message();
    boolean unmarshalError = false;

    try {
      requestMsg.unmarshal(rpcReq, 0);
      if (logger.isDebugEnabled()) {
        logger.debug("Received RPC message with id: {}", getMessageId(requestMsg));
      }
    } catch (Exception e) {
      logger.error("Caught exception parsing message", e);
      unmarshalError = true;
    }

    // dispatch
    if (!unmarshalError) {
      try {
        final Message replyMsg = dispatch(requestMsg);

        // send reply
        rpcSocket.send(ColferUtils.toBytes(replyMsg));
        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched and sent message w/id: {} in reply to RPC request"
                    + " w/id: {} in {} ms",
                getMessageId(replyMsg),
                getMessageId(requestMsg),
                took);
          }
        }
      } catch (Exception e) {
        logger.error("Error dispatching message w/id {}", getMessageId(requestMsg), e);
      }
    }
  }

  private void handleJsonRpcRequest() {
    if (jsonrpcSocketIndex != -1 && poller.pollin(jsonrpcSocketIndex)) {
      InboundJsonRpcRequestMsg jsonrpcMsg = InboundJsonRpcRequestMsg.receive(jsonrpcSocket, true);
      if (jsonrpcMsg != null) {
        dispatchJsonRpcRequest(jsonrpcMsg);
      }
    }
  }

  private void dispatchJsonRpcRequest(InboundJsonRpcRequestMsg jsonrpcMsg) {

    final long started = System.currentTimeMillis();
    JsonRpcRequest jsonRpcRequest = null;
    JsonRpcResponse jsonRpcResponse = null;
    String requestId = null;
    Exception parseException = null;
    Message requestMsg = null;

    // parse and validate JSON-RPC message
    try {
      jsonRpcRequest = parseAndValidateJsonRpcMessage(jsonrpcMsg.getJsonMessage());
      requestId = jsonRpcRequest.getId();
      if (logger.isDebugEnabled()) {
        logger.debug("Received JSON-RPC message from peer w/id: {}", jsonrpcMsg.getPeerId());
      }
    } catch (JsonRpcRequestException e) {
      requestId = e.getRequestId();
      parseException = e;
      logger.error("Caught exception parsing message with id: {}", requestId, e);
    } catch (Exception e) {
      parseException = e;
      logger.error("Caught unexpected exception parsing message", e);
    }

    // parsing+validating failed, log and send error response
    if (parseException != null) {
      jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(parseException, requestId);
      try {
        new OutboundJsonRpcResponseMsg(
                jsonrpcMsg.getPeerId(), JsonRpcSerializer.toJson(jsonRpcResponse))
            .send(jsonrpcSocket);
      } catch (JsonSerializationException ex) {
        logger.error("Error sending JSON-RPC response", ex);
      }
      logMessageDispatch(requestId, null, started);
      return;
    }

    Exception invalidRequestException = null;
    // create ExecMessage from JSON-RPC request message
    MessageType requestMessageType = null;
    try {
      requestMessageType = JsonRpcMessageUtils.getMessageType(jsonRpcRequest);
      switch (requestMessageType.getFamily()) {
        case EXEC:
          requestMsg =
              messageBuilder.jsonRpcRequestToExecMessage(jsonRpcRequest, jsonrpcMsg.getPeerId());
          break;
        case META:
          requestMsg =
              messageBuilder.jsonRpcRequestToMetaMessage(jsonRpcRequest, jsonrpcMsg.getPeerId());
          break;
        case CONTROL:
          requestMsg =
              messageBuilder.jsonRpcRequestToControlMessage(jsonRpcRequest, jsonrpcMsg.getPeerId());
          break;
        case INTERCEPT:
        default:
          invalidRequestException = new InvalidJsonRpcRequestException("Unsupported request type");
      }
    } catch (Exception e) {
      invalidRequestException = new InvalidJsonRpcRequestException(e.getMessage());
    }

    // request type is unsupported, log and send error response
    if (invalidRequestException != null) {
      jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(invalidRequestException, requestId);
      try {
        new OutboundJsonRpcResponseMsg(
                jsonrpcMsg.getPeerId(), JsonRpcSerializer.toJson(jsonRpcResponse))
            .send(jsonrpcSocket);
      } catch (JsonSerializationException ex) {
        logger.error("Error sending JSON-RPC response", ex);
      }
      logMessageDispatch(requestId, null, started);
      return;
    }

    // dispatch
    Message replyMsg;
    try {
      replyMsg = dispatch(requestMsg);
    } catch (Exception dispatchException) {

      // dispatching failed, log and send error response
      logger.error(
          "Error dispatching message w/id {}", getMessageId(requestMsg), dispatchException);
      jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(dispatchException, requestId);
      try {
        new OutboundJsonRpcResponseMsg(
                jsonrpcMsg.getPeerId(), JsonRpcSerializer.toJson(jsonRpcResponse))
            .send(jsonrpcSocket);
      } catch (JsonSerializationException ex) {
        logger.error("Error sending JSON-RPC response", ex);
      }
      logMessageDispatch(requestMsg, jsonRpcResponse.getId(), started);
      return;
    }

    // create JSON-RPC response from MetaMessage / ExecMessage reply
    switch (requestMessageType.getFamily()) {
      case EXEC:
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromExecMessageResponse(replyMsg.getExecMessage());
        break;
      case META:
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromMetaMessageResponse(replyMsg.getMetaMessage());
        break;
      case CONTROL:
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromControlMessageResponse(replyMsg.getControlMessage());
        break;
      default:
        // we cannot get here: other branches ruled out in pre-dispatch switch
    }

    // send response
    try {
      new OutboundJsonRpcResponseMsg(
              jsonrpcMsg.getPeerId(), JsonRpcSerializer.toJson(jsonRpcResponse))
          .send(jsonrpcSocket);
    } catch (JsonSerializationException ex) {
      logger.error("Error sending JSON-RPC response", ex);
    }
    logMessageDispatch(requestMsg, replyMsg, started);
  }

  private boolean handleSocketException(ZMQException ex) {
    int errorCode = ex.getErrorCode();
    if (errorCode == ZError.ETERM || errorCode == ZError.EINTR) {
      if (logger.isDebugEnabled()) {
        logger.debug("Caught ETERM or EINTR during blocking read. Breaking out.");
      }
      return true;
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Re-throwing unexpected exception", ex);
      }
      throw ex;
    }
  }

  @Override
  protected void closeConnections() {
    Arrays.stream(new ZMQ.Socket[] {rpcSocket, jsonrpcSocket})
        .forEach(
            s -> {
              if (s != null) {
                try {
                  s.close();
                } catch (Exception e) {
                  logger.debug("Error closing socket", e);
                }
              }
            });
    super.closeConnections();
  }
}
