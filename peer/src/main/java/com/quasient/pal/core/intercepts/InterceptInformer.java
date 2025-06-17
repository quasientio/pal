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

package com.quasient.pal.core.intercepts;

import com.quasient.pal.common.directory.events.InterceptEvent;
import com.quasient.pal.common.directory.events.InterceptNodeListener;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.core.messages.InterceptEventMsg;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Handles the relay of intercept events to a specified intercept endpoint.
 *
 * <p>This class acts as an {@link InterceptNodeListener} that processes intercept events by either
 * registering all current intercepts in the system or by handling individual intercept events. It
 * uses a per-thread ZeroMQ REQ socket to send messages to the intercept service.
 */
@Singleton
public class InterceptInformer implements InterceptNodeListener {

  /** Logger instance used for logging events, errors, and debug information. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptInformer.class);

  /** ZeroMQ context used for creating and managing ZeroMQ sockets for intercept communications. */
  private final ZContext zmqContext;

  /** Builder to construct intercept messages from intercept requests. */
  private final MessageBuilder messageBuilder;

  /**
   * Provider for connections to the directory service from which peer and intercept information are
   * retrieved.
   */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /** Address string of the intercept endpoint to which intercept messages are sent. */
  private final String interceptsAddress;

  /** Unique identifier for the local peer. */
  private final UUID peerUuid;

  /**
   * Thread-local flag indicating whether the REQ socket has been created for the current thread.
   * This flag prevents attempts to close a socket that was never initialized.
   */
  private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

  /**
   * Thread-local REQ socket used to send intercept messages.
   *
   * <p>Each thread initializes its own socket which connects to the intercept endpoint specified by
   * {@code interceptsAddress}. The socket is created on first access.
   */
  private final ThreadLocal<Socket> threadSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(interceptsAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to interceptsAddress: {}", interceptsAddress);
          }
          threadSocketCreated.set(true);
          return worker;
        }
      };

  /**
   * Constructs an InterceptInformer instance with the necessary dependencies.
   *
   * @param zmqContext the ZeroMQ context for socket operations
   * @param messageBuilder builder to create intercept messages from requests
   * @param directoryConnectionProvider provider to retrieve the directory connection
   * @param peerUuid unique identifier for the local peer
   * @param interceptsAddress endpoint address (injected with qualifier "intercepts.reg")
   */
  @Inject
  public InterceptInformer(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider,
      UUID peerUuid,
      @Named("intercepts.reg") String interceptsAddress) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.peerUuid = peerUuid;
    this.interceptsAddress = interceptsAddress;
  }

  /**
   * Retrieves all current intercepts from the directory and sends them to the intercept endpoint.
   *
   * <p>This method connects to the directory service, obtains all peer information, and for each
   * peer, retrieves the associated intercept requests. It then builds and sends an intercept
   * message for each request via a ZeroMQ REQ socket. Any retrieval errors are logged, and
   * processing continues for remaining peers.
   */
  public void registerAllInterceptsInDirectory() {
    final Set<PeerInfo> peers;
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    try {
      peers = palDirectory.getAllPeers();
    } catch (Exception e) {
      logger.error("Error retrieving peers from directory", e);
      return;
    }

    final List<InterceptRequest<?>> allInterceptRequests = new ArrayList<>();
    for (PeerInfo peer : peers) {
      try {
        allInterceptRequests.addAll(palDirectory.getPeerInterceptRequests(peer.getUuid()));
      } catch (Exception e) {
        logger.error("Error retrieving intercepts for peer w/uuid:{}", peer.getUuid(), e);
      }
    }

    allInterceptRequests.forEach(
        interceptRequest -> {
          InterceptMessage interceptMessage =
              messageBuilder.buildInterceptMessage(interceptRequest);
          sendInterceptEventMsg(new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)));
        });
  }

  /**
   * {@inheritDoc}
   *
   * <p>Processes an intercept event received from the directory. Depending on the event type, it
   * either builds and sends an intercept message for new intercept registrations or sends a message
   * indicating removal. Self-produced intercept events (originating from the local peer) are
   * ignored.
   *
   * @param event the intercept event containing the type and associated data for processing
   * @throws IllegalStateException if the event type is unexpected
   */
  @Override
  public void interceptEvent(InterceptEvent event) {
    if (logger.isDebugEnabled()) {
      logger.debug("Got new intercept event: {}", event);
    }

    InterceptEventMsg interceptEventMsg;
    switch (event.type()) {
      case INTERCEPT_ADDED:
        final InterceptRequest<?> interceptRequest = event.interceptRequest();
        if (event.peerUuid().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring self-produced intercept request: {}", interceptRequest);
          }
          return;
        }
        Objects.requireNonNull(interceptRequest);
        InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);
        interceptEventMsg = new InterceptEventMsg(ColferUtils.toBytes(interceptMessage));
        break;
      case INTERCEPT_REMOVED:
        if (event.peerUuid().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Ignoring unregistration of self-produced intercept request: {}",
                event.interceptRequest());
          }
          return;
        }
        String interceptMsgId = event.interceptId();
        interceptEventMsg = new InterceptEventMsg(interceptMsgId);
        break;
      default:
        throw new IllegalStateException("Unexpected intercept event type: " + event.type());
    }

    sendInterceptEventMsg(interceptEventMsg);
  }

  /**
   * Sends an intercept event message using the per-thread ZeroMQ REQ socket. On error, fail-close.
   *
   * <p>This method logs the outgoing message at trace level, sends the message over the socket, and
   * then performs a blocking read to receive a response. If the response is not the expected
   * response ("0") or if a ZMQException occurs (e.g., due to interruption or termination), it logs
   * a warning and closes the socket.
   *
   * @param message the intercept event message to be sent
   */
  private void sendInterceptEventMsg(InterceptEventMsg message) {
    if (logger.isTraceEnabled()) {
      logger.trace("Sending new intercept evt message: {}", message);
    }
    // send
    Socket outSocket = threadSocket.get();
    message.send(outSocket);

    // receive
    String rcvdString = null;
    try {
      rcvdString = outSocket.recvStr();
    } catch (ZMQException ex) {
      int errorCode = ex.getErrorCode();
      if (errorCode == ZError.ETERM) {
        logger.warn("Caught ETERM during blocking read. Will close socket");
      } else if (errorCode == ZError.EINTR) {
        logger.warn("Caught EINTR during blocking read. Will close socket.");
      } else {
        logger.warn("Caught unknown error during blocking read. Will close socket.");
      }
      outSocket.close();
    }
    if (!"0".equals(rcvdString)) {
      logger.warn(
          "Received non-0 response (code={}) when informing of intercept event: {}",
          rcvdString,
          message);
    }
  }

  /**
   * Closes the thread-local ZeroMQ REQ socket if it has been created.
   *
   * <p>This method checks whether the current thread has an initialized socket (using a
   * thread-local flag) and closes it if available. It also cleans up the thread-local variables to
   * free resources.
   */
  public void closeThreadLocalSocket() {
    if (Boolean.TRUE.equals(threadSocketCreated.get())) {
      Socket outSocket = threadSocket.get();
      if (outSocket != null) {
        outSocket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local socket closed");
        }
      }
      threadSocket.remove();
    }
    threadSocketCreated.remove();
  }
}
