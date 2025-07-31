/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.internal.concurrent.AdaptiveSpinParkWaitStrategy;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.service.ConnectedService;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ConstructorCall;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Named;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jctools.queues.MessagePassingQueue;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** Base abstract implementation of WalWriter containing common functionality. */
public abstract class AbstractWalWriter extends ConnectedService implements WalWriter {

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be written.
   */
  protected final MessagePassingQueue<OutboundMsg> walQueue;

  /** Adaptive dynamic backoff strategy used by the queue drain operation. */
  protected static final MessagePassingQueue.WaitStrategy ADAPTIVE_100_MICROSECONDS =
      new AdaptiveSpinParkWaitStrategy();

  /** ZeroMQ publisher socket used to publish message offsets when enabled. */
  protected ZMQ.Socket offsetPublisherSocket;

  /** ZeroMQ address to bind the offset publisher socket for delivering offset updates. */
  protected final String offsetPubAddress;

  /** Flag indicating whether message offsets should be published. */
  protected boolean publishOffsets;

  /** Information describing the Log to which messages are written. */
  protected LogInfo writeAheadLog;

  /** Poison pill to enqueue for graceful self-shutdown, when writing/appending fails. */
  protected static final OutboundMsg POISON_PILL =
      new OutboundMsg(
          MessageType.UNKNOWN, ExecPhase.UNDEFINED, null, "POISON", null, new ConstructorCall());

  /** Atomic guard to prevent to ensure the poison pill is just sent once. */
  protected final AtomicBoolean pillSent = new AtomicBoolean(false);

  /**
   * Global failure flag, used to let producer threads in {@link OutboundMessageGateway} know WAL is
   * down.
   */
  protected final AtomicBoolean walFailed;

  /** Counter tracking total of messages received from the {@code walQueue} */
  protected long messagesReceived;

  /** Counter of in-flight messages. */
  protected final AtomicInteger messagesInFlight = new AtomicInteger(0);

  /** Counter tracking the number of messages successfully written to the Log. (acks received) */
  protected final AtomicInteger messagesWritten = new AtomicInteger(0);

  /** Total of messages dropped due to write/append error */
  protected final AtomicInteger messagesDroppedError = new AtomicInteger(0);

  /**
   * Constructs a new WalWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this WAL writer service.
   * @param walQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param walFailed global flag used to indicate failure when writing/appending to the WAL, so
   *     that producers halt enqueuing.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   */
  protected AbstractWalWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("WalWriter.service") String serviceName,
      @Named("wal_queue") HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.walQueue = walQueue;
    this.walFailed = walFailed;
    this.offsetPubAddress = offsetPubAddress;
  }

  /** {@inheritDoc} */
  @Override
  public WalWriterStats getLiveStats() {
    return new WalWriterStats(
        messagesReceived,
        messagesWritten.get(),
        messagesDroppedError.get(),
        messagesInFlight.get());
  }

  /**
   * Triggers a shutdown sequence for the service.
   *
   * <p>Override superclass because we don't want to interrupt the thread, causing in-flight
   * sends/appends, being executed from this service's main thread, to fail.
   */
  @Override
  protected void triggerStop() {
    shutdownRequested = true;
  }

  /** {@inheritDoc} */
  @Override
  public LogInfo getCurrentWal() {
    return writeAheadLog;
  }
}
