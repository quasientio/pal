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
import java.util.Arrays;
import java.util.UUID;
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

class PeerMessageInvoker extends AbstractMessageInvokerThread {

  private final String rpcDealerAddress;
  private ZMQ.Socket rpcSocket;
  private final String jsonrpcDealerAddress;
  private ZMQ.Socket jsonrpcSocket;
  private static Gson gson = new Gson();

  public PeerMessageInvoker(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
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
    this.rpcDealerAddress = rpcDealerAddress;
    this.jsonrpcDealerAddress = jsonrpcDealerAddress;
  }

  // Constructor for unit-testing
  PeerMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String rpcDealerAddress,
      String jsonrpcDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    this.rpcDealerAddress = rpcDealerAddress;
    this.jsonrpcDealerAddress = jsonrpcDealerAddress;
  }

  @Override
  public void run() {

    // create and connect REP socket for RPC
    rpcSocket = zmqContext.createSocket(SocketType.REP);
    rpcSocket.connect(rpcDealerAddress);

    // create and connect REP socket for JSON-RPC
    jsonrpcSocket = zmqContext.createSocket(SocketType.REP);
    jsonrpcSocket.connect(jsonrpcDealerAddress);

    Poller poller = zmqContext.createPoller(2);
    poller.register(rpcSocket, Poller.POLLIN);
    poller.register(jsonrpcSocket, Poller.POLLIN);

    Message requestMsg;
    Message replyMsg;
    byte[] rpcReq;

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }

    boolean socketError = false;
    while (!interrupted() && !socketError) {
      rpcReq = null;
      InboundJsonRpcRequestMsg jsonrpcMsg = null;

      if (logger.isDebugEnabled()) {
        logger.debug("Ready and polling from sockets...");
      }

      poller.poll();

      try {
        // got a RPC request
        if (poller.pollin(0)) {
          rpcReq = rpcSocket.recv(0);
        }

        // got a JSON-RPC request
        if (poller.pollin(1)) {
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

        // create ExecMessage from JSON-RPC request message
        requestMsg =
            messageBuilder.jsonRpcRequestToExecMessage(jsonRpcRequest, jsonrpcMsg.getClientId());

        // dispatch
        if (!unmarshalError) {
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
