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

import static com.quasient.pal.serdes.colfer.ColferUtils.format;
import static com.quasient.pal.serdes.colfer.ColferUtils.toBytes;

import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.InterceptMatcher;
import com.quasient.pal.core.RunOptions;
import com.quasient.pal.core.messages.SessionCommandMsg;
import com.quasient.pal.core.messages.SessionResponseMsg;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * Handles routing of execution messages to the WAL and PUB interfaces, intercept callbacks, and
 * session service through ZeroMQ sockets. This class creates and manages thread-local sockets for
 * different purposes, such as publishing execution messages, sending session commands, and handling
 * callback messages. It uses injected dependencies to build messages, resolve peer connections, and
 * manage intercept logic.
 */
@Singleton
public class DispatcherConnector {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(DispatcherConnector.class);

  /** Timeout (in milliseconds) for receiving responses on callback sockets. */
  private static final int CALLBACK_RECEIVE_TIMEOUT_MS = 3000;

  /** ZeroMQ context used to create and manage zmq sockets. */
  private final ZContext zmqContext;

  /** Unique identifier of the current peer instance. */
  private final UUID peerUuid;

  /** Utility for constructing various message objects used in execution and callback processing. */
  private final MessageBuilder messageBuilder;

  /** Provider to access the directory, which supplies connection details for remote peers. */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /** Matcher used to determine and retrieve intercepts applicable to execution messages. */
  private final InterceptMatcher interceptMatcher;

  /** Network address used for publishing execution messages. */
  private final String msgPublisherAddress;

  /** Network address of the session service, used for sending session-related commands. */
  private final String sessionServiceAddress;

  /**
   * Precomputed list of internal headers used for the write-ahead mechanism in message processing.
   */
  private final List<InternalHeader> writeAheadHeaders;

  /** Set of runtime options that control behavior such as intercept handling and TCP publishing. */
  private final Set<RunOptions> runOptions;

  /**
   * Atomic counter tracking the cumulative time spent on publishing messages via the publisher
   * socket.
   */
  private final AtomicLong totalPubSocketTime = new AtomicLong();

  /** Atomic counter tracking the total number of published requests. */
  private final AtomicLong totalPublishedRequests = new AtomicLong();

  /**
   * Thread-local REQ socket instance used for publishing execution messages. This socket is created
   * and connected to the message publisher address upon first access.
   */
  private final ThreadLocal<Socket> threadPubSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(msgPublisherAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected new REQ socket to outCellAddress: {}", msgPublisherAddress);
          }
          threadPubSocketCreated.set(true);
          return worker;
        }
      };

  /**
   * Thread-local flag indicating whether the publisher socket has been created for the current
   * thread.
   */
  private final ThreadLocal<Boolean> threadPubSocketCreated = ThreadLocal.withInitial(() -> false);

  /**
   * Thread-local REQ socket instance used for communication with the session service. This socket
   * is created and connected to the session service address upon first access.
   */
  private final ThreadLocal<Socket> threadSessionsSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(sessionServiceAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected new REQ socket to sessionServiceAddress: {}",
                sessionServiceAddress);
          }
          threadSessionsSocketCreated.set(true);
          return worker;
        }
      };

  /**
   * Thread-local flag indicating whether the session service socket has been created for the
   * current thread.
   */
  private final ThreadLocal<Boolean> threadSessionsSocketCreated =
      ThreadLocal.withInitial(() -> false);

  /**
   * Thread-local mapping of peer UUIDs to REQ sockets used for handling intercept callback
   * messages.
   */
  private final ThreadLocal<Map<UUID, Socket>> callbackSockets =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * Constructs a new DispatcherConnector with the provided communication context, identifiers,
   * message builders, directory connection provider, runtime options, intercept matcher, and
   * service addresses.
   *
   * @param zmqContext the ZeroMQ context used for socket creation and management
   * @param peerUuid the unique identifier for the current peer
   * @param messageBuilder the builder instance for constructing message objects
   * @param directoryConnectionProvider provider for retrieving peer connection details
   * @param runOptions the set of runtime options that influence message processing behavior
   * @param interceptMatcher matcher used for determining applicable intercepts for messages
   * @param msgPublisherAddress the address used for publishing execution messages
   * @param sessionServiceAddress the address for the session service communication
   */
  @Inject
  public DispatcherConnector(
      ZContext zmqContext,
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider,
      Set<RunOptions> runOptions,
      InterceptMatcher interceptMatcher,
      @Named("out.cell") String msgPublisherAddress,
      @Named("session.svc") String sessionServiceAddress) {
    this.zmqContext = zmqContext;
    this.peerUuid = peerUuid;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.runOptions = runOptions;
    this.interceptMatcher = interceptMatcher;
    this.msgPublisherAddress = msgPublisherAddress;
    this.sessionServiceAddress = sessionServiceAddress;
    this.writeAheadHeaders =
        Collections.singletonList(messageBuilder.buildWriteAheadHeader(peerUuid));
  }

  /**
   * Sends out an execution message for processing. This method delegates to the overloaded version
   * with null headers.
   *
   * @param message the message containing execution information to be sent
   * @param execPhase the phase of execution associated with the message
   * @return the resulting ExecMessage after processing and potential intercept handling
   */
  public ExecMessage sendExecMessage(Message message, ExecPhase execPhase) {
    return sendExecMessage(message, execPhase, null);
  }

  /**
   * Sends out an execution message with optional headers for processing. Handles intercept
   * processing, WAL and stream publishing based on the runtime options.
   *
   * @param message the message containing execution details; must include a non-null ExecMessage
   * @param execPhase the phase of execution during which the message is sent
   * @param headers optional headers for write-ahead or additional processing; can be null
   * @return the ExecMessage processed or returned by the callback logic, or the original if no
   *     intercepts apply
   * @throws IllegalArgumentException if the contained ExecMessage in the provided message is null
   */
  private ExecMessage sendExecMessage(
      Message message, ExecPhase execPhase, @Nullable List<InternalHeader> headers) {
    if (message.getExecMessage() == null) {
      throw new IllegalArgumentException("ExecMessage is null");
    }
    final ExecMessage execMessage = message.getExecMessage();
    if (logger.isTraceEnabled()) {
      logger.trace(
          "sendExecMessage:in w/ execMessage: {}, execPhase: {}\n, headers: {}",
          format(execMessage),
          execPhase,
          headers);
    }

    MessageType messageType = MessageType.fromId(message.getMessageType());
    List<InterceptMessage> matchingIntercepts = null;

    if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isInterceptableType(messageType)) {
      // find matching intercepts for execMessage
      matchingIntercepts =
          interceptMatcher.getMatchingIntercepts(execMessage, messageType, execPhase);
    }

    // publish execMessage -- TODO should we in case of intercepts publish it after
    if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
      final OutboundMsg msg =
          new OutboundMsg(
              messageType,
              execPhase,
              headers,
              execMessage.getMessageId(),
              execMessage.getResponseToId(),
              message);
      publishMessage(msg);
    }

    if (!runOptions.contains(RunOptions.WITH_INTERCEPTS)) {
      return execMessage; // we're done
    }

    // if no intercepts, return received ExecMessage
    if (matchingIntercepts == null || matchingIntercepts.isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("No intercepts for execMessage w/id: {}", execMessage.getMessageId());
      }
      return execMessage;
    }

    // deal with intercepts
    final ExecMessage returnValue;
    // TODO: deal with all possible intercepts (now we only care about the first intercept request)
    InterceptMessage interceptMessage = matchingIntercepts.get(0);
    ExecMessage callbackMessage =
        messageBuilder.buildCallbackForInterceptRequest(peerUuid, execMessage, interceptMessage);
    UUID interceptor = UUID.fromString(interceptMessage.getPeerUuid());

    InterceptType interceptType = InterceptType.fromByte(interceptMessage.getInterceptType());
    try {
      if (interceptType.equals(InterceptType.BEFORE_ASYNC)
          || interceptType.equals(InterceptType.AFTER_ASYNC)) {
        sendAsyncCallbackToPeer(interceptor, callbackMessage);
      } else if (interceptType.equals(InterceptType.BEFORE)
          || interceptType.equals(InterceptType.AFTER)) {
        @SuppressWarnings("unused")
        final byte[] unusedResponse;
        unusedResponse = sendCallbackToPeer(interceptor, callbackMessage);
      } else {
        logger.error("Unsupported callback type: {}", interceptType);
      }
    } catch (Exception ex) {
      logger.error(
          "Error sending callback to peer w/uuid: {}, callback execMessage: {}",
          interceptor,
          ColferUtils.format(callbackMessage),
          ex);
    }

    //      if (interceptMessage.getType().equals(Intercepts.InterceptType.AROUND)) {
    // TODO in case of AROUND we should return the execMessage returned by callback only in
    // ExecPhase.After
    // response = sendCallbackToPeer(interceptor, callbackMessage);
    // only parse execMessage when needed
    // final ExecMessage responseMessage = ExecMessage.parseFrom(response);
    // returnValue = responseMessage;
    //      } else {
    returnValue = execMessage;
    //    }

    if (logger.isTraceEnabled()) {
      logger.trace("out w/ {}", ColferUtils.format(returnValue));
    }
    return returnValue;
  }

  /**
   * Publishes the given outbound message on the thread-local publisher socket. Tracks publishing
   * time and the number of published requests, and logs warnings if the publisher returns a
   * non-zero response.
   *
   * @param message the OutboundMsg to be sent over the network
   */
  private void publishMessage(OutboundMsg message) {
    Socket publisherReqSocket = threadPubSocket.get();
    long start = Instant.now().toEpochMilli();
    message.send(publisherReqSocket);
    try {
      String response = publisherReqSocket.recvStr();
      if (!"0".equalsIgnoreCase(response)) {
        logger.warn("Non-zero response from message publisher for message: {}", message);
      }
    } catch (ZMQException e) {
      logger.error("Error receiving response from publisher socket", e);
    } finally {
      totalPubSocketTime.getAndAdd((Instant.now().toEpochMilli() - start));
      totalPublishedRequests.getAndIncrement();
    }
  }

  /**
   * Writes the provided execution message ahead by publishing it if TCP publishing is enabled. This
   * mechanism is typically used for logging or preliminary handling before main processing.
   *
   * @param message the ExecMessage to be written ahead
   * @param messageType the type of the message for contextual processing
   */
  public void writeAhead(ExecMessage message, MessageType messageType) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "writeAhead:in w/ message with id: {},from {}",
          message.getMessageId(),
          message.getPeerUuid());
    }

    if (!runOptions.contains(RunOptions.WITH_TCP_PUB)) {
      return;
    }

    final OutboundMsg msg =
        new OutboundMsg(
            messageType,
            ExecPhase.BEFORE,
            writeAheadHeaders,
            message.getMessageId(),
            message.getResponseToId(),
            messageBuilder.wrap(message));

    // no intercept matching, just publish it
    publishMessage(msg);
  }

  /**
   * Sends a callback execution message to the specified peer. Optionally waits for and returns a
   * response if requested. The method obtains or creates a thread-local REQ socket connected to the
   * peer's address, sends the callback, and handles retries if necessary.
   *
   * @param interceptor the unique identifier of the peer to which the callback is sent
   * @param callbackMessage the execution message to be used as a callback
   * @param getResponse flag indicating whether to wait for and return a response from the peer
   * @return a byte array representing the response from the peer if getResponse is true; otherwise,
   *     null
   * @throws Exception if an error occurs during socket creation, message sending, or response
   *     retrieval
   */
  private @Nullable byte[] sendCallbackMessageToPeer(
      UUID interceptor, ExecMessage callbackMessage, boolean getResponse) throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending callback message: {} to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          interceptor);
    }
    byte[] response;
    // get socket for peer and send callback msg
    Socket req = getConnectedReqSocketFor(interceptor);
    final boolean sentOk = req.send(toBytes(messageBuilder.wrap(callbackMessage)), 0);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent callback message: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          sentOk,
          interceptor);
    }

    // block until we get a response or peer is disconnected
    response = null;
    if (getResponse) {
      boolean peerIsUp = true;
      boolean gotResponse = false;
      while (!gotResponse && peerIsUp) {
        response = req.recv(0);
        if (response != null) {
          gotResponse = true;
          if (logger.isDebugEnabled()) {
            final Message callbackResponseMessage = new Message();
            callbackResponseMessage.unmarshal(response, 0);
            logger.debug(
                "Got response from callback: {}", ColferUtils.format(callbackResponseMessage));
          }
        } else { // we hit the timeout, check if peer is alive
          final PalDirectory palDirectory =
              directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
          peerIsUp = palDirectory.peerExists(interceptor);
          if (peerIsUp) {
            logger.warn(
                "Peer w/uuid: {} is taking long to reply, but is alive, so we wait", interceptor);
          } else {
            logger.warn("Peer w/uuid: {} is disconnected. Giving up, returning null", interceptor);
          }
        }
      }
    }
    // TODO getResponse  == false --> we still have to receive() !!
    return response;
  }

  /**
   * Sends an asynchronous callback message to the specified interceptor. This method does not wait
   * for a response.
   *
   * @param interceptor the unique identifier of the interceptor peer
   * @param message the callback execution message to send
   * @throws Exception if an error occurs during sending the callback message
   */
  private void sendAsyncCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    sendCallbackMessageToPeer(interceptor, message, false);
  }

  /**
   * Sends a session command message to the session service and returns the corresponding response.
   * It uses a thread-local REQ socket to communicate with the session service.
   *
   * @param sessionCommandMsg the session command message to be sent
   * @return the SessionResponseMsg received from the session service, or null if the message was
   *     not sent successfully
   */
  public SessionResponseMsg sendMessageToSessionService(SessionCommandMsg sessionCommandMsg) {
    SessionResponseMsg responseMessage = null;
    final Socket sessionServiceSocket = threadSessionsSocket.get();
    final boolean msgSent = sessionCommandMsg.send(sessionServiceSocket);
    if (msgSent) {
      if (logger.isDebugEnabled()) {
        logger.debug("Sent session command message: {}", sessionCommandMsg);
      }
      responseMessage = SessionResponseMsg.receive(sessionServiceSocket, true);
      if (logger.isDebugEnabled()) {
        logger.debug("Received session response message: {}", responseMessage);
      }
    }
    return responseMessage;
  }

  /**
   * Sends a synchronous callback message to the specified peer and waits for its response.
   *
   * @param interceptor the unique identifier of the target peer
   * @param message the callback execution message to be sent
   * @return a byte array representing the response from the peer
   * @throws Exception if an error occurs during message transmission or response retrieval
   */
  private byte[] sendCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    return sendCallbackMessageToPeer(interceptor, message, true);
  }

  /**
   * Retrieves an existing or creates a new thread-local REQ socket connected to the specified peer.
   * The connection is established using the peer's address obtained from the directory provider.
   *
   * @param peer the unique identifier of the target peer
   * @return a connected REQ Socket for communication with the specified peer
   * @throws Exception if the peer's information cannot be retrieved from the directory
   */
  private Socket getConnectedReqSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (callbackSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing REQ socket for peer w/uuid: {}", peer);
      }
      return callbackSockets.get().get(peer);
    }

    // else, create and connect new socket
    if (logger.isDebugEnabled()) {
      logger.debug("Connecting new REQ socket to peer w/uuid: {}", peer);
    }
    final Socket reqSocket = zmqContext.createSocket(SocketType.REQ);
    // set receive timeout
    reqSocket.setReceiveTimeOut(CALLBACK_RECEIVE_TIMEOUT_MS);
    // get peer's address
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    String interceptorAddress = palDirectory.getPeerInfo(peer).getRpcAddress();
    reqSocket.connect(interceptorAddress);
    // store in thread-local peer->socket map
    callbackSockets.get().put(peer, reqSocket);

    return reqSocket;
  }

  /**
   * Determines whether the provided message type is eligible for intercept processing.
   *
   * @param type the MessageType to evaluate
   * @return true if the type supports intercept handling; false otherwise
   */
  private boolean isInterceptableType(MessageType type) {
    return switch (type) {
      case EXEC_CONSTRUCTOR,
              EXEC_INSTANCE_METHOD,
              EXEC_CLASS_METHOD,
              EXEC_GET_STATIC,
              EXEC_GET_FIELD,
              EXEC_PUT_STATIC,
              EXEC_PUT_FIELD ->
          true;
      default -> false;
    };
  }

  /**
   * Prints aggregated statistics regarding message publishing, including total time spent and the
   * number of requests published.
   */
  void printStats() {
    logger.info("totalPubSocketTime = {} ms", totalPubSocketTime);
    logger.info("totalPublishedRequests = {}", totalPublishedRequests);
  }

  /**
   * Closes all thread-local sockets used for publishing, session service communication, and
   * callback handling. Prior to closing, the method prints performance statistics and cleans up
   * thread-local state.
   */
  void closeThreadLocalSockets() {
    printStats();
    if (totalPublishedRequests.longValue() != 0) {
      logger.info(
          "avg ms per pub = {} ms",
          totalPubSocketTime.longValue() / totalPublishedRequests.longValue());
    }

    if (Boolean.TRUE.equals(threadPubSocketCreated.get())) {
      Socket socket = threadPubSocket.get();
      if (socket != null) {
        socket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local REQ socket for publishing closed");
        }
      }
      threadPubSocket.remove();
    }
    threadPubSocketCreated.remove();

    if (Boolean.TRUE.equals(threadSessionsSocketCreated.get())) {
      Socket socket = threadSessionsSocket.get();
      if (socket != null) {
        socket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local REQ socket for session service closed");
        }
      }
      threadSessionsSocket.remove();
    }
    threadSessionsSocketCreated.remove();

    callbackSockets
        .get()
        .forEach(
            (uuid, socket) -> {
              if (socket != null) {
                socket.close();
              }
              if (logger.isDebugEnabled()) {
                logger.debug("Closed thread-local REQ socket to remote peer w/uuid: {}", uuid);
              }
            });
    callbackSockets.remove();
  }
}
