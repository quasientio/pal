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

import com.quasient.pal.core.internal.concurrent.AdaptiveSpinParkWaitStrategy;
import com.quasient.pal.core.service.ConnectedService;
import com.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Service responsible for publishing outbound messages.
 *
 * <p>If LogWriter writes WAL messages to Log, this services parallels that behavior, forwarding
 * such outbound messages through a tcp PUB socket instead. The service stops when interrupted or
 * upon encountering critical socket errors.
 */
@Singleton
public class MessagePublisher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class);

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be published.
   */
  private final MessagePassingQueue<OutboundMsg> pubQueue;

  /** Adaptive dynamic backoff strategy used by the queue drain operation. */
  private static final MessagePassingQueue.WaitStrategy ZMQ_WAIT =
      new AdaptiveSpinParkWaitStrategy(20, 50_000L); // 50 µs

  /** ZeroMQ PUB socket used to publish messages to subscribed services. */
  private Socket pubSocket;

  /** true while we are seeing consecutive send failures */
  private boolean pubDown = false;

  /** # messages dropped since the socket first failed */
  private long dropped = 0;

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
   * @param pubQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param outPubAddress Socket address for the outgoing PUB (publish) endpoint.
   */
  @Inject
  public MessagePublisher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("MessagePublisher.service") String serviceName,
      @Named("pub_queue") MessagePassingQueue<OutboundMsg> pubQueue,
      @Named("out.pub") String outPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.pubQueue = pubQueue;
    this.outPubAddress = outPubAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Opens and binds the PUB socket.
   */
  @Override
  protected void openConnections() {
    // open PUB socket
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(outPubAddress);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Continuously receives messages from the configured Pub Queue and publishes them to
   * subscribers through the PUB socket. The method processes messages until the thread is
   * interrupted.
   */
  @Override
  public final void run() {

    pubQueue.drain(
        this::handleOutboundMessage, ZMQ_WAIT, () -> !Thread.currentThread().isInterrupted());
  }

  /**
   * Handles a single {@link OutboundMsg} taken from the pub queue and sends it out via ZMQ PUB.
   *
   * @param msg the dequeued message; never {@code null}.
   */
  private void handleOutboundMessage(OutboundMsg msg) {

    if (msg.send(pubSocket)) { // ── success ──
      if (pubDown) {
        logger.info("PUB socket recovered – {} messages were dropped", dropped);
        pubDown = false;
        dropped = 0;
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Published msg {} ({} bytes)", msg.getMessageId(), msg.getSize());
      }
      return;
    }

    /* ── failure ─────────────────────────────────────────────────────── */
    dropped++;
    if (!pubDown) { // first failure in a streak
      logger.warn("PUB socket congested – starting to drop messages");
      pubDown = true;
    }
    // else: still congested – do not flood the log
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the ZeroMQ PUB sockets, ensuring that all associated resources are properly released.
   */
  @Override
  protected void closeConnections() {
    closeConnection(pubSocket, "Error closing PUB socket");
  }
}
