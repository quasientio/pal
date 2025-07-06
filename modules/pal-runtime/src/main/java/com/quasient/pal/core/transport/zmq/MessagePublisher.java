/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.zmq;

import com.quasient.pal.core.service.ConnectedService;
import com.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

// TODO replace this with a XPUB-XSUB proxy

/**
 * Service responsible for publishing outbound messages.
 *
 * <p>This class initializes ZeroMQ REP and PUB sockets to receive requests and publish messages
 * respectively. It listens continuously for outbound messages, sending an acknowledgment to the
 * requester while forwarding the message to subscribers. The service stops when interrupted or upon
 * encountering critical socket errors.
 */
@Singleton
public class MessagePublisher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class);

  /** Reply constant indicating successful message processing. */
  private static final String OK_REPLY = "0";

  /** Reply constant indicating an error occurred during message processing. */
  private static final String ERROR_REPLY = "1";

  /** ZeroMQ REP socket used to receive requests and respond with acknowledgments. */
  private Socket repSocket;

  /** ZeroMQ PUB socket used to publish messages to subscribed services. */
  private Socket pubSocket;

  /**
   * Address for binding the REP socket; designates where this service listens for incoming
   * requests.
   */
  private final String outRepAddress;

  /** Address for binding the PUB socket; designates where this service publishes messages. */
  private final String outPubAddress;

  /**
   * Constructs a new MessagePublisher instance that sets up the messaging endpoints.
   *
   * @param peerUuid Unique identifier representing this peer.
   * @param context ZeroMQ context used for creating and managing sockets.
   * @param syncSocketAddress Address of the synchronization socket for service readiness.
   * @param serviceThreadGroup Thread group under which the service runs.
   * @param serviceName Unique name identifying this service instance.
   * @param outRepAddress Socket address for the outgoing REP (reply) endpoint.
   * @param outPubAddress Socket address for the outgoing PUB (publish) endpoint.
   */
  @Inject
  public MessagePublisher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("MessagePublisher.service") String serviceName,
      @Named("out.cell") String outRepAddress,
      @Named("out.pub") String outPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.outRepAddress = outRepAddress;
    this.outPubAddress = outPubAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Opens and binds the ZeroMQ REP and PUB sockets to their respective configured addresses.
   */
  @Override
  protected void openConnections() {
    // open REP and PUB sockets
    repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.bind(outRepAddress);
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(outPubAddress);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Runs the service loop to continuously receive outbound messages. Each received message is
   * acknowledged via a reply on the REP socket and then published to subscribers through the PUB
   * socket. The method handles specific ZeroMQ exceptions to ensure graceful service shutdown.
   */
  @Override
  public final void run() {
    boolean socketError = false;
    while (!Thread.interrupted() && !socketError) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.receive(repSocket, true);
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
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving message", e);
        repSocket.send(ERROR_REPLY);
      }
      if (msg != null) {
        // response OK
        repSocket.send(OK_REPLY);
        // publish the message
        msg.send(pubSocket);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Published new message w/id: {} ({} bytes)", msg.getMessageId(), msg.getSize());
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the ZeroMQ REP and PUB sockets, ensuring that all associated resources are properly
   * released.
   */
  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
    closeConnection(pubSocket, "Error closing PUB socket");
  }
}
