/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import static com.quasient.pal.serdes.colfer.ColferUtils.toBytes;

import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Dispatches intercept callbacks to remote peers.
 *
 * <p>This class is responsible for sending callback messages to peers that have registered
 * intercepts. It manages per-thread ZeroMQ sockets for callback communication and handles both
 * synchronous (BEFORE/AFTER) and asynchronous (BEFORE_ASYNC/AFTER_ASYNC) intercept types.
 *
 * <p>Socket Management: This dispatcher uses two separate socket caches: REQ sockets for
 * synchronous callbacks (which require request-reply pattern) and DEALER sockets for asynchronous
 * callbacks (which allow fire-and-forget). The server-side ROUTER socket in ZmqRpcServer accepts
 * connections from both REQ and DEALER clients.
 *
 * <p>Thread Safety: This class uses ThreadLocal storage for sockets, making it safe for
 * multithreaded use. Each thread maintains its own socket connections to target peers.
 */
@Singleton
public class InterceptCallbackDispatcher {

  /** Logger instance for debugging callback dispatch operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptCallbackDispatcher.class);

  /** Timeout in milliseconds for receiving responses from synchronous callback requests. */
  private static final int CALLBACK_RECEIVE_TIMEOUT_MS = 3000;

  /** The unique identifier of this peer. */
  private final UUID peerUuid;

  /** ZeroMQ context used for creating sockets. */
  private final ZContext zmqContext;

  /** Message builder for constructing callback messages. */
  private final MessageBuilder messageBuilder;

  /** Provider for accessing the PAL directory to look up peer endpoints. */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /**
   * Per-thread socket cache for synchronous callback communication. Each thread maintains its own
   * map of peer UUID to connected REQ socket for BEFORE/AFTER intercepts.
   */
  private final ThreadLocal<Map<UUID, Socket>> syncCallbackSockets =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * Per-thread socket cache for asynchronous callback communication. Each thread maintains its own
   * map of peer UUID to connected DEALER socket for BEFORE_ASYNC/AFTER_ASYNC intercepts.
   */
  private final ThreadLocal<Map<UUID, Socket>> asyncCallbackSockets =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * Constructs a new InterceptCallbackDispatcher with the specified dependencies.
   *
   * @param peerUuid the unique identifier for this peer
   * @param zmqContext the ZeroMQ context for socket creation
   * @param messageBuilder the message builder for constructing callback messages
   * @param directoryConnectionProvider provider for accessing peer directory information
   */
  @Inject
  public InterceptCallbackDispatcher(
      UUID peerUuid,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider) {
    this.peerUuid = peerUuid;
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
  }

  /**
   * Sends callbacks for all matching remote intercepts.
   *
   * <p>This method iterates through all remote intercepts in the check result and sends callback
   * messages to the corresponding peers. Synchronous intercepts (BEFORE/AFTER) block until a
   * response is received or timeout occurs. Asynchronous intercepts (BEFORE_ASYNC/AFTER_ASYNC)
   * fire-and-forget without waiting for responses.
   *
   * @param interceptCheckResult result from InterceptChecker containing matched intercepts
   * @param execMessage the execution message to send in callbacks
   */
  public void sendCallbacks(InterceptCheckResult interceptCheckResult, ExecMessage execMessage) {
    if (!interceptCheckResult.hasRemoteIntercepts()) {
      return;
    }

    List<InterceptMessage> remoteIntercepts = interceptCheckResult.getRemoteIntercepts();

    for (InterceptMessage interceptMessage : remoteIntercepts) {
      UUID interceptorPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
      InterceptType interceptType = InterceptType.fromByte(interceptMessage.getInterceptType());

      ExecMessage callbackMessage =
          messageBuilder.buildCallbackForInterceptRequest(peerUuid, execMessage, interceptMessage);

      try {
        if (interceptType.equals(InterceptType.BEFORE_ASYNC)
            || interceptType.equals(InterceptType.AFTER_ASYNC)) {
          sendAsyncCallbackToPeer(interceptorPeerUuid, callbackMessage);
        } else if (interceptType.equals(InterceptType.BEFORE)
            || interceptType.equals(InterceptType.AFTER)) {
          @SuppressWarnings("unused")
          byte[] unusedResponse = sendSyncCallbackToPeer(interceptorPeerUuid, callbackMessage);
        } else {
          logger.error("Unsupported callback type: {}", interceptType);
        }
      } catch (Exception ex) {
        logger.error(
            "Error sending callback to peer w/uuid: {}, callback execMessage: {}",
            interceptorPeerUuid,
            ColferUtils.format(callbackMessage),
            ex);
      }
    }
  }

  /**
   * Sends an asynchronous callback message to the specified peer (fire-and-forget).
   *
   * <p>Uses DEALER socket which allows sending without waiting for response, unlike REQ sockets
   * which require strict send-recv alternation. DEALER sockets must send an empty delimiter frame
   * before the payload to match the ROUTER socket's expected envelope format.
   *
   * @param targetPeerUuid the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @throws Exception if an error occurs while sending the message
   */
  private void sendAsyncCallbackToPeer(UUID targetPeerUuid, ExecMessage callbackMessage)
      throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending async callback message: {} to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          targetPeerUuid);
    }

    // Get DEALER socket for peer and send callback msg (no response expected)
    // DEALER sends: [empty delimiter, payload]
    Socket dealer = getConnectedDealerSocketFor(targetPeerUuid);
    dealer.sendMore(new byte[0]); // Empty delimiter frame
    final boolean sentOk = dealer.send(toBytes(messageBuilder.wrap(callbackMessage)), 0);

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent async callback message: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          sentOk,
          targetPeerUuid);
    }
  }

  /**
   * Sends a synchronous callback message to the specified peer and waits for a response.
   *
   * @param targetPeerUuid the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @return the response bytes received from the target peer
   * @throws Exception if an error occurs while sending the message or receiving the response
   */
  private byte[] sendSyncCallbackToPeer(UUID targetPeerUuid, ExecMessage callbackMessage)
      throws Exception {
    return sendCallbackMessageToPeer(targetPeerUuid, callbackMessage);
  }

  /**
   * Sends a callback message to the specified peer, optionally waiting for a response.
   *
   * @param interceptor the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @return the response bytes if getResponse is true, null otherwise
   * @throws Exception if an error occurs during message sending or receiving
   */
  private byte[] sendCallbackMessageToPeer(UUID interceptor, ExecMessage callbackMessage)
      throws Exception {

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
              "Timed out waiting for callback response from peer w/uuid: {}. "
                  + "Peer still exists, will retry.",
              interceptor);
        } else {
          logger.warn(
              "Timed out waiting for callback response from peer w/uuid: {}. "
                  + "Peer no longer exists.",
              interceptor);
        }
      }
    }
    return response;
  }

  /**
   * Retrieves an existing or creates a new thread-local REQ socket connected to the specified peer.
   *
   * <p>The connection is established using the peer's ZMQ RPC address obtained from the directory
   * provider. Sockets are cached per-thread to avoid repeated connection overhead.
   *
   * @param peer the unique identifier of the target peer
   * @return a connected REQ Socket for communication with the specified peer
   * @throws Exception if the peer's information cannot be retrieved from the directory
   */
  private Socket getConnectedReqSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (syncCallbackSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing REQ socket for peer w/uuid: {}", peer);
      }
      return syncCallbackSockets.get().get(peer);
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
    String interceptorAddress = palDirectory.getPeer(peer).getZmqRpcAddress();
    reqSocket.connect(interceptorAddress);

    // store in thread-local peer->socket map
    syncCallbackSockets.get().put(peer, reqSocket);

    return reqSocket;
  }

  /**
   * Retrieves an existing or creates a new thread-local DEALER socket connected to the specified
   * peer.
   *
   * <p>DEALER sockets are used for asynchronous callbacks because they allow multiple sends without
   * requiring a response, unlike REQ sockets which require strict send-recv alternation. The
   * server-side ROUTER socket in ZmqRpcServer accepts connections from both REQ and DEALER clients.
   *
   * <p>The connection is established using the peer's ZMQ RPC address obtained from the directory
   * provider. Sockets are cached per-thread to avoid repeated connection overhead.
   *
   * @param peer the unique identifier of the target peer
   * @return a connected DEALER Socket for communication with the specified peer
   * @throws Exception if the peer's information cannot be retrieved from the directory
   */
  private Socket getConnectedDealerSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (asyncCallbackSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing DEALER socket for peer w/uuid: {}", peer);
      }
      return asyncCallbackSockets.get().get(peer);
    }

    // else, create and connect new socket
    if (logger.isDebugEnabled()) {
      logger.debug("Connecting new DEALER socket to peer w/uuid: {}", peer);
    }

    final Socket dealerSocket = zmqContext.createSocket(SocketType.DEALER);

    // get peer's address
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    String interceptorAddress = palDirectory.getPeer(peer).getZmqRpcAddress();
    dealerSocket.connect(interceptorAddress);

    // store in thread-local peer->socket map
    asyncCallbackSockets.get().put(peer, dealerSocket);

    return dealerSocket;
  }

  /**
   * Cleans up thread-local sockets when a thread terminates.
   *
   * <p>This method should be called when a thread that has used this dispatcher is about to
   * terminate, to properly close all open sockets and free resources. It cleans up both synchronous
   * (REQ) and asynchronous (DEALER) socket caches.
   */
  public void cleanup() {
    // Clean up sync sockets (REQ)
    Map<UUID, Socket> syncSockets = syncCallbackSockets.get();
    syncSockets.values().forEach(Socket::close);
    syncSockets.clear();
    syncCallbackSockets.remove();

    // Clean up async sockets (DEALER)
    Map<UUID, Socket> asyncSockets = asyncCallbackSockets.get();
    asyncSockets.values().forEach(Socket::close);
    asyncSockets.clear();
    asyncCallbackSockets.remove();
  }
}
