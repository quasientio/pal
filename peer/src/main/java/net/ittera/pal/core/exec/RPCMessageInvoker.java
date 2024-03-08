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

import com.google.gson.Gson;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.core.messages.InboundJsonRpcRequestMsg;
import net.ittera.pal.core.messages.OutboundJsonRpcResponseMsg;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.colfer.MessageUtils;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQException;
import zmq.ZError;

class RPCMessageInvoker extends AbstractMessageInvokerThread {

  private final Set<RunOptions> runOptions;
  private final String rpcDealerAddress;
  private ZMQ.Socket rpcSocket;
  private final String jsonrpcDealerAddress;
  private ZMQ.Socket jsonrpcSocket;
  private static Gson gson = new Gson();

  public RPCMessageInvoker(
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
  RPCMessageInvoker(
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

    Poller poller = zmqContext.createPoller(2);
    int rpcSocketIndex = -1;
    int jsonrpcSocketIndex = -1;

    // create and connect REP socket for RPC
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

    // create and connect REP socket for JSON-RPC
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

    Message requestMsg;
    Message replyMsg;
    byte[] rpcReq;

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from sockets");
    }

    boolean socketError = false;
    while (!interrupted() && !socketError) {
      rpcReq = null;
      InboundJsonRpcRequestMsg jsonrpcMsg = null;

      if (logger.isDebugEnabled()) {
        logger.debug("Ready and polling from sockets...");
      }

      int signaled;
      try {
        signaled = poller.poll();
      } catch (ZError.IOException e) {
        if (e.getCause() instanceof ClosedChannelException) {
          // we are probably shutting down
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedChannelException during poll. Breaking out.");
          }
          break;
        } else {
          logger.error("Caught unexpected ZError exception during poll", e);
          throw e;
        }
      }

      if (signaled < 1) {
        if (logger.isInfoEnabled()) {
          logger.info("Poller returned from poll with {} sockets ready. Breaking out.", signaled);
        }
        break;
      }

      try {
        // got a RPC request
        if (rpcSocketIndex != -1 && poller.pollin(rpcSocketIndex)) {
          rpcReq = rpcSocket.recv(0);
        }

        // got a JSON-RPC request
        if (jsonrpcSocketIndex != -1 && poller.pollin(jsonrpcSocketIndex)) {
          jsonrpcMsg = InboundJsonRpcRequestMsg.recvMsg(jsonrpcSocket, true);
        }
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          socketError = true;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          socketError = true;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Re-throwing unexpected exception", ex);
          }
          throw ex;
        }
      }

      // parse and dispatch new RPC req
      if (rpcReq != null) {

        final long started = System.currentTimeMillis();
        requestMsg = new Message();
        boolean unmarshalError = false;

        try {
          requestMsg.unmarshal(rpcReq, 0);
          if (logger.isDebugEnabled()) {
            logger.debug("Received RPC message with uuid: {}", getMessageUuid(requestMsg));
          }
        } catch (Exception e) {
          logger.error("Caught exception parsing message", e);
          unmarshalError = true;
        }

        // dispatch
        if (!unmarshalError) {
          try {
            replyMsg = dispatch(requestMsg);

            // send reply
            rpcSocket.send(ColferUtils.toBytes(replyMsg));

            if (logger.isDebugEnabled()) {
              final long took = System.currentTimeMillis() - started;
              if (logger.isDebugEnabled()) {
                logger.debug(
                    "Dispatched and sent message w/uuid: {} in reply to RPC request w/uuid: {} in {} ms",
                    getMessageUuid(replyMsg),
                    getMessageUuid(requestMsg),
                    took);
              }
            }
          } catch (Exception e) {
            logger.error("Error dispatching message w/uuid {}", getMessageUuid(requestMsg), e);
          }
        }
      }

      // parse and dispatch new JSON-RPC req
      if (jsonrpcMsg != null) {
        final long started = System.currentTimeMillis();
        boolean unmarshalError = false;
        JsonRpcRequest jsonRpcRequest = null;

        try {
          jsonRpcRequest = MessageUtils.parseAndValidateJsonRpcMessage(jsonrpcMsg.getJsonMessage());
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Received JSON-RPC message from client uuid: {}", jsonrpcMsg.getClientId());
          }
        } catch (Exception e) {
          logger.error("Caught exception parsing message", e);
          unmarshalError = true;
        }

        // dispatch
        if (!unmarshalError) {
          // create ExecMessage from JSON-RPC request message
          requestMsg =
              messageBuilder.jsonRpcRequestToExecMessage(jsonRpcRequest, jsonrpcMsg.getClientId());
          try {
            replyMsg = dispatch(requestMsg);

            // create JSON-RPC reply from ExecMessage reply
            final JsonRpcResponse jsonRpcResponse =
                messageBuilder.jsonRpcResponseFromExecMessageReply(replyMsg.getExecMessage());

            // send reply
            new OutboundJsonRpcResponseMsg(jsonrpcMsg.getClientId(), gson.toJson(jsonRpcResponse))
                .send(jsonrpcSocket);

            if (logger.isDebugEnabled()) {
              final long took = System.currentTimeMillis() - started;
              if (logger.isDebugEnabled()) {
                logger.debug(
                    "Dispatched and sent message w/uuid: {} in reply to JSON-RPC request w/uuid: {} in {} ms",
                    getMessageUuid(replyMsg),
                    getMessageUuid(requestMsg),
                    took);
              }
            }
          } catch (Exception e) {
            logger.error("Error dispatching message w/uuid {}", getMessageUuid(requestMsg), e);
          }
        }
      }
    }
    closeConnections();
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
