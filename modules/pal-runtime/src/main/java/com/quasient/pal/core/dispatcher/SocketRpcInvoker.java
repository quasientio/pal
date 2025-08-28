/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import static com.quasient.pal.serdes.colfer.ControlMessageUtils.getMessageTypeOf;
import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageId;
import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static com.quasient.pal.serdes.colfer.MetaMessageUtils.getMessageTypeOf;
import static com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.parseAndValidateJsonRpcMessage;

import com.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import com.quasient.pal.core.internal.messages.OutboundJsonRpcResponseMsg;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import com.quasient.pal.serdes.jsonrpc.JsonRpcRequestException;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * SocketRpcInvoker is responsible for receiving and dispatching ZMQ-RPC and JSON-RPC messages over
 * ZeroMQ sockets.
 *
 * <p>It creates and manages two separate REP sockets for handling binary (colfer) RPC messages and
 * JSON-RPC messages, registers them with a ZeroMQ poller, and dispatches incoming requests using a
 * MessageBuilder for (de)serialization. The class continues processing messages until interrupted
 * or a fatal socket error occurs.
 */
class SocketRpcInvoker extends AbstractMessageInvokerThread {

  /**
   * Runtime options controlling behavior (e.g. enabling/disabling ZMQ-RPC or JSON-RPC features).
   */
  private final Set<RunOptions> runOptions;

  /** The endpoint address for connecting to the ZMQ-RPC dealer. */
  private final String rpcDealerAddress;

  /** ZeroMQ socket used for handling ZMQ-RPC messages. */
  private ZMQ.Socket zmqRpcSocket;

  /** The endpoint address for connecting to the JSON-RPC dealer. */
  private final String jsonrpcDealerAddress;

  /** ZeroMQ socket used for handling JSON-RPC messages. */
  private ZMQ.Socket jsonrpcSocket;

  /** The ZeroMQ Poller instance used to monitor sockets for incoming events. */
  private Poller poller;

  /** Poller index for the ZMQ-RPC socket. A value of -1 indicates the socket is not registered. */
  private int zmqRpcSocketIndex = -1;

  /** Poller index for the JSON-RPC socket. A value of -1 indicates the socket is not registered. */
  private int jsonRpcSocketIndex = -1;

  /**
   * Constructs a new SocketRpcInvoker with the specified parameters.
   *
   * @param group the thread group to which this thread will belong
   * @param name the name of the thread
   * @param zmqContext the ZeroMQ context for socket creation
   * @param messageBuilder the builder used to convert and construct messages
   * @param runOptions the set of runtime options controlling enabled features
   * @param rpcDealerAddress the address of the ZMQ-RPC dealer for socket connection
   * @param jsonrpcDealerAddress the address of the JSON-RPC dealer for socket connection
   * @param incomingMessageDispatcher the dispatcher used for routing incoming messages
   * @param outboundMessageGateway the gateway instance for routing outgoing messages
   * @param peerUuid the unique identifier for this peer
   */
  public SocketRpcInvoker(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      Set<RunOptions> runOptions,
      String rpcDealerAddress,
      String jsonrpcDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      OutboundMessageGateway outboundMessageGateway,
      UUID peerUuid) {
    super(
        group,
        name,
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        outboundMessageGateway,
        peerUuid);
    this.runOptions = runOptions;
    this.rpcDealerAddress = rpcDealerAddress;
    this.jsonrpcDealerAddress = jsonrpcDealerAddress;
  }

  /**
   * Package-private constructor primarily used for unit testing.
   *
   * @param zmqContext the ZeroMQ context for socket creation
   * @param messageBuilder the builder used to construct messages
   * @param runOptions the set of runtime options controlling enabled features
   * @param rpcDealerAddress the address of the RPC dealer for socket connection
   * @param jsonrpcDealerAddress the address of the JSON-RPC dealer for socket connection
   * @param incomingMessageDispatcher the dispatcher for routing incoming messages
   * @param peerUuid the unique identifier for this peer
   */
  SocketRpcInvoker(
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

  /**
   * Main execution loop that monitors RPC and JSON-RPC sockets for incoming messages.
   *
   * <p>The method initializes the poller, then continuously polls the registered sockets, handles
   * any incoming ZMQ-RPC/JSON-RPC requests, and gracefully handles socket exceptions. The loop
   * terminates when the thread is interrupted or a fatal socket error occurs.
   */
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

  /**
   * Sets up the ZeroMQ Poller by creating and registering sockets for ZMQ-RPC and JSON-RPC.
   *
   * @return the configured Poller instance ready for monitoring socket events
   */
  private Poller setupPoller() {
    poller = zmqContext.createPoller(2);
    setupRpcSocket(poller);
    setupJsonRpcSocket(poller);
    return poller;
  }

  /**
   * Polls the provided Poller for any incoming events.
   *
   * <p>This method wraps the poller's poll() invocation and gracefully handles a
   * ClosedChannelException.
   *
   * @param poller the Poller instance to be polled for events
   * @return the number of sockets with pending events, or -1 if a ClosedChannelException occurs
   * @throws ZError.IOException if an unexpected I/O error occurs during polling
   */
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

  /**
   * Configures the ZMQ-RPC socket if enabled by {@code runOptions}.
   *
   * <p>A REP socket is created, connected to the ZMQ-RPC dealer address, and registered with the
   * poller.
   *
   * @param poller the Poller with which the ZMQ-RPC socket is to be registered
   */
  private void setupRpcSocket(Poller poller) {
    if (runOptions.contains(RunOptions.WITH_ZMQ_RPC)) {
      zmqRpcSocket = zmqContext.createSocket(SocketType.REP);
      boolean rpcSocketConnected = zmqRpcSocket.connect(rpcDealerAddress);
      if (rpcSocketConnected) {
        if (logger.isDebugEnabled()) {
          logger.debug("Connected to RPC dealer at {}", rpcDealerAddress);
        }
        zmqRpcSocketIndex = poller.register(zmqRpcSocket, Poller.POLLIN);
      } else {
        logger.error("Failed to connect to RPC dealer at {}", rpcDealerAddress);
      }
    }
  }

  /**
   * Configures the JSON-RPC socket if enabled by {@code runOptions}.
   *
   * <p>A REP socket is created, connected to the JSON-RPC dealer address, and registered with the
   * poller.
   *
   * @param poller the Poller with which the JSON-RPC socket is to be registered
   */
  private void setupJsonRpcSocket(Poller poller) {
    if (runOptions.contains(RunOptions.WITH_JSON_RPC)) {
      jsonrpcSocket = zmqContext.createSocket(SocketType.REP);
      boolean jsonrpcSocketConnected = jsonrpcSocket.connect(jsonrpcDealerAddress);
      if (jsonrpcSocketConnected) {
        if (logger.isDebugEnabled()) {
          logger.debug("Connected to JSON-RPC dealer at {}", jsonrpcDealerAddress);
        }
        jsonRpcSocketIndex = poller.register(jsonrpcSocket, Poller.POLLIN);
      } else {
        logger.error("Failed to connect to JSON-RPC dealer at {}", jsonrpcDealerAddress);
      }
    }
  }

  /**
   * Checks and processes an incoming BIN-RPC request if the ZMQ-RPC socket is ready.
   *
   * <p>The method receives the BIN-RPC message bytes and, if a message is present, dispatches it
   * for further processing.
   */
  private void handleRpcRequest() {
    if (zmqRpcSocketIndex != -1 && poller.pollin(zmqRpcSocketIndex)) {
      byte[] rpcReq = zmqRpcSocket.recv(0);
      if (rpcReq != null) {
        dispatchRpcRequest(rpcReq);
      }
    }
  }

  /**
   * Dispatches an ZMQ-RPC request by unmarshalling the message, processing it, and sending an
   * appropriate response.
   *
   * @param rpcReq the raw ZMQ-RPC message bytes to be processed
   */
  private void dispatchRpcRequest(byte[] rpcReq) {

    final long started = System.currentTimeMillis();
    final Message requestMsg = new Message();
    boolean unmarshalError = false;

    try {
      requestMsg.unmarshal(rpcReq, 0);
      if (logger.isDebugEnabled()) {
        logger.debug("Received ZMQ-RPC message with id: {}", getMessageId(requestMsg));
      }
    } catch (Exception e) {
      logger.error("Caught exception parsing message", e);
      unmarshalError = true;
    }

    // dispatch
    if (!unmarshalError) {
      try {
        final Message responseMessage = dispatch(requestMsg, MessageChannelType.ZMQ_SOCKET_RPC);

        // send response
        zmqRpcSocket.send(ColferUtils.toBytes(responseMessage));
        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched and sent message w/id: {} in response to RPC request"
                    + " w/id: {} in {} ms",
                getMessageId(responseMessage),
                getMessageId(requestMsg),
                took);
          }
        }
      } catch (Exception e) {
        logger.error("Error dispatching message w/id {}", getMessageId(requestMsg), e);
      }
    }
  }

  /**
   * Checks and processes an incoming JSON-RPC request if the JSON-RPC socket is ready.
   *
   * <p>The method receives an {@link InboundJsonRpcRequestMsg} from the JSON-RPC socket and
   * dispatches it.
   */
  private void handleJsonRpcRequest() {
    if (jsonRpcSocketIndex != -1 && poller.pollin(jsonRpcSocketIndex)) {
      InboundJsonRpcRequestMsg jsonrpcMsg = InboundJsonRpcRequestMsg.receive(jsonrpcSocket, true);
      if (jsonrpcMsg != null) {
        dispatchJsonRpcRequest(jsonrpcMsg);
      }
    }
  }

  /**
   * Dispatches a JSON-RPC request by validating, converting, and processing the message.
   *
   * <p>The method parses and validates the JSON-RPC message, identifies its type, converts it into
   * an internal (colfer-serialized) Exec message, and dispatches it to generate a response. In case
   * of any errors, an error response is generated and sent.
   *
   * @param jsonrpcMsg the inbound JSON-RPC request message containing the raw JSON payload and
   *     metadata
   */
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
                jsonrpcMsg.getPeerId(),
                JsonRpcSerializer.toJson(jsonRpcResponse),
                MessageType.UNKNOWN)
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
                jsonrpcMsg.getPeerId(),
                JsonRpcSerializer.toJson(jsonRpcResponse),
                MessageType.UNKNOWN)
            .send(jsonrpcSocket);
      } catch (JsonSerializationException ex) {
        logger.error("Error sending JSON-RPC response", ex);
      }
      logMessageDispatch(requestId, null, started);
      return;
    }

    // dispatch
    Message responseMessage;
    try {
      responseMessage = dispatch(requestMsg, MessageChannelType.WEBSOCKET_RPC);
    } catch (Exception dispatchException) {

      // dispatching failed, log and send error response
      logger.error(
          "Error dispatching message w/id {}", getMessageId(requestMsg), dispatchException);
      jsonRpcResponse = messageBuilder.jsonRpcResponseFromError(dispatchException, requestId);
      try {
        new OutboundJsonRpcResponseMsg(
                jsonrpcMsg.getPeerId(),
                JsonRpcSerializer.toJson(jsonRpcResponse),
                MessageType.UNKNOWN)
            .send(jsonrpcSocket);
      } catch (JsonSerializationException ex) {
        logger.error("Error sending JSON-RPC response", ex);
      }
      logMessageDispatch(requestMsg, jsonRpcResponse.getId(), started);
      return;
    }

    // create JSON-RPC response from MetaMessage / ExecMessage response
    MessageType responseMessageType;
    switch (requestMessageType.getFamily()) {
      case EXEC -> {
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromExecMessageResponse(responseMessage.getExecMessage());
        responseMessageType = getMessageTypeOf(responseMessage.getExecMessage());
      }
      case META -> {
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromMetaMessageResponse(responseMessage.getMetaMessage());
        responseMessageType = getMessageTypeOf(responseMessage.getMetaMessage());
      }
      case CONTROL -> {
        jsonRpcResponse =
            messageBuilder.jsonRpcResponseFromControlMessageResponse(
                responseMessage.getControlMessage());
        responseMessageType = getMessageTypeOf(responseMessage.getControlMessage());
      }
      default ->
          // we cannot get here: other branches ruled out in pre-dispatch switch
          responseMessageType = MessageType.UNKNOWN;
    }

    // send response
    try {
      new OutboundJsonRpcResponseMsg(
              jsonrpcMsg.getPeerId(),
              JsonRpcSerializer.toJson(jsonRpcResponse),
              responseMessageType)
          .send(jsonrpcSocket);
    } catch (JsonSerializationException ex) {
      logger.error("Error sending JSON-RPC response", ex);
    }
    logMessageDispatch(requestMsg, responseMessage, started);
  }

  /**
   * Handles a socket exception encountered during polling or message processing.
   *
   * <p>If the exception corresponds to termination or interruption events (ETERM or EINTR), the
   * method returns {@code true} to indicate that further socket processing should be aborted.
   * Otherwise, the exception is rethrown.
   *
   * @param ex the {@link ZMQException} encountered during socket operations
   * @return {@code true} if the exception indicates a termination condition; otherwise, never
   *     returns normally
   * @throws ZMQException if the exception is unexpected and not related to termination
   */
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

  /**
   * Closes the ZMQ-RPC and JSON-RPC sockets and invokes the superclass connection closure.
   *
   * <p>This method ensures that all socket resources are released gracefully.
   */
  @Override
  protected void closeConnections() {
    Arrays.stream(new ZMQ.Socket[] {zmqRpcSocket, jsonrpcSocket})
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
