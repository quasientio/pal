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

package com.quasient.pal.core.rpc;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageId;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;

import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.core.messages.InboundLogMsg;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.jsonrpc.JsonRpcRequestException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * A specialized invoker thread for processing inbound Log messages received via a ZeroMQ REP
 * socket.
 *
 * <p>This class continuously listens for messages in either JSON-RPC or binary format, performing
 * parsing, validation, and dispatching to appropriate handlers. It is intended to connect to a
 * designated Log dealer endpoint.
 */
class LogMessageInvoker extends AbstractMessageInvokerThread {

  /** The network endpoint of the Log dealer to which the socket connects. */
  private final String logDealerAddress;

  /** The ZeroMQ socket used for communication with the log dealer. */
  private ZMQ.Socket socket;

  /**
   * Constructs a LogMessageInvoker thread with specified configuration parameters.
   *
   * @param group the thread group for the invoker thread
   * @param name the name of the thread
   * @param zmqContext the ZeroMQ context used for socket creation
   * @param messageBuilder builder for converting messages between representations
   * @param logDealerAddress network address of the Log dealer endpoint
   * @param incomingMessageDispatcher dispatcher for processing incoming messages
   * @param dispatcherConnector connector used by the dispatcher for message routing
   * @param peerUuid identifier of the peer associated with this invoker
   */
  public LogMessageInvoker(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String logDealerAddress,
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
    this.logDealerAddress = logDealerAddress;
  }

  /**
   * Constructs a LogMessageInvoker with a simplified configuration, automatically assigning thread
   * group and name.
   *
   * @param zmqContext the ZeroMQ context used for socket creation
   * @param messageBuilder builder for converting messages between representations
   * @param logDealerAddress network address of the log dealer endpoint
   * @param incomingMessageDispatcher dispatcher for processing incoming messages
   * @param peerUuid identifier of the peer associated with this invoker
   */
  LogMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String logDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    this.logDealerAddress = logDealerAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method creates a ZeroMQ REP socket connected to the Log dealer and enters a loop to
   * continuously receive and process Log messages. Messages are handled based on their declared
   * format (JSON-RPC or binary), with parsing, validation, and dispatching performed as necessary.
   * The loop terminates gracefully on thread interruption or critical socket exceptions.
   */
  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(SocketType.REP);
    socket.connect(logDealerAddress);

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }
    while (!Thread.interrupted()) {
      // receive message
      InboundLogMsg msg = null;
      try {
        msg = InboundLogMsg.receive(socket, true);
        assert msg != null;
        if (logger.isDebugEnabled()) {
          logger.debug("Getting message with kafka offset: {}", msg.getOffset());
        }
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          break;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          break;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving/parsing message", e);
      }

      if (msg == null) {
        continue;
      }

      final long started = System.currentTimeMillis();

      switch (msg.getMessageFormat()) {
        case JSON -> {
          JsonRpcRequest jsonRpcRequest = null;
          final JsonRpcResponse jsonRpcResponse;
          String requestId = null;
          Exception parseException = null;

          // parse and validate JSON-RPC message
          try {
            jsonRpcRequest =
                parseAndValidateJsonRpcMessage(new String(msg.getBody(), StandardCharsets.UTF_8));
            requestId = jsonRpcRequest.getId();
            if (logger.isDebugEnabled()) {
              logger.debug("Received JSON-RPC request message with id: {}", requestId);
            }
          } catch (JsonRpcRequestException e) {
            logger.error("Caught exception parsing message", e);
            requestId = e.getRequestId();
            parseException = e;
          } catch (Exception e) {
            parseException = e;
            logger.error("Caught unexpected exception parsing message", e);
          }

          if (parseException != null) {
            // parsing+validating failed, log and send error response
            jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(parseException, requestId);
            // TODO write to Log (ie. send to LogWriter) ->
            // JsonRpcSerializer.toJson(jsonRpcResponse)
            logMessageDispatch(requestId, null, started);
            return;
          }

          if (logger.isDebugEnabled()) {
            logger.debug("Received message with offset: {}, id: {}", msg.getOffset(), requestId);
          }

          UUID fromPeerUuid = null;
          try {
            byte[] producerIdBytes =
                msg.getHeaders().headers("producer-id").iterator().next().value();
            fromPeerUuid = UuidUtils.fromBytes(producerIdBytes);
          } catch (Exception e) {
            logger.error("Error getting producer-id header", e);
          }

          // create ExecMessage from JSON-RPC request message
          final Message requestMsg =
              messageBuilder.jsonRpcRequestToExecMessage(jsonRpcRequest, fromPeerUuid);

          // dispatch
          Message responseMessage;
          try {
            responseMessage = dispatch(requestMsg);
          } catch (Exception dispatchException) {

            // dispatching failed, log and send error response
            logger.error(
                "Error dispatching message w/id {}", getMessageId(requestMsg), dispatchException);
            jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(dispatchException, requestId);
            // TODO write to Log (ie. send to LogWriter) ->
            // JsonRpcSerializer.toJson(jsonRpcResponse)
            logMessageDispatch(requestMsg, jsonRpcResponse.getId(), started);
            return;
          }
          // create JSON-RPC response from ExecMessage response
          jsonRpcResponse =
              messageBuilder.jsonRpcResponseFromExecMessageResponse(
                  responseMessage.getExecMessage());

          // send response
          // TODO write to Log (ie. send to LogWriter) -> JsonRpcSerializer.toJson(jsonRpcResponse)
          logMessageDispatch(requestMsg, responseMessage, started);
        }
        case BINARY -> {
          final Message requestMsg = new Message();
          // parse req
          try {
            requestMsg.unmarshal(msg.getBody(), 0);
          } catch (Exception e) {
            logger.error("Caught exception parsing message", e);
            continue;
          }

          if (logger.isDebugEnabled()) {
            logger.debug(
                "Received message with offset: {}, id: {}",
                msg.getOffset(),
                getMessageId(requestMsg));
          }

          // dispatch it
          dispatch(requestMsg, msg.getOffset());
          if (logger.isDebugEnabled()) {
            final long took = System.currentTimeMillis() - started;
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Dispatched log message with id: {} in {} ms", getMessageId(requestMsg), took);
            }
          }
        }
        default ->
            logger.error(
                "Unknown message format: {}, skipping message with offset: {}",
                msg.getMessageFormat(),
                msg.getOffset());
      }
    }

    closeConnections();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the ZeroMQ socket used for communication with the log dealer, handling any exceptions
   * that occur during the closure process. After closing the socket, it delegates to the superclass
   * to perform any additional cleanup.
   */
  @Override
  protected void closeConnections() {
    try {
      socket.close();
    } catch (Exception e) {
      logger.debug("Error closing socket", e);
    }
    super.closeConnections();
  }
}
