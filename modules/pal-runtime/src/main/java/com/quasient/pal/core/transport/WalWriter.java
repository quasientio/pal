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
import javax.annotation.Nullable;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** Base abstract implementation of WalWriter containing common functionality. */
public abstract class WalWriter extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(WalWriter.class);

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be written.
   */
  protected final MessagePassingQueue<OutboundMsg> walQueue;

  /** Default value for {@code flush_on_close} property. */
  protected static final boolean DEF_FLUSH_ON_CLOSE = true;

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

  /**
   * Flag to indicate whether we should flush on close, waiting for queued and in-flight messages to
   * be written, or if we shut down immediately. If not given, it is set to {@link
   * WalWriter#DEF_FLUSH_ON_CLOSE}.
   */
  protected final boolean isFlushOnClose;

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
  protected WalWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("WalWriter.service") String serviceName,
      @Named("wal_queue") HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      @Named("wal.flush_on_close") @Nullable String flushOnClose) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.walQueue = walQueue;
    this.walFailed = walFailed;
    this.offsetPubAddress = offsetPubAddress;

    // other WAL properties
    if (flushOnClose != null && !flushOnClose.isBlank()) {
      isFlushOnClose = Boolean.parseBoolean(flushOnClose);
    } else {
      isFlushOnClose = DEF_FLUSH_ON_CLOSE;
    }
  }

  /**
   * Returns a consistent, side-effect-free view of the counters.
   *
   * @return snapshot of live stats
   */
  public WalWriterStats getLiveStats() {
    return new WalWriterStats(
        messagesReceived,
        messagesWritten.get(),
        messagesDroppedError.get(),
        messagesInFlight.get());
  }

  /**
   * Continuously receives messages from the WAL Queue and writes them to either the implemented
   * queue. The method processes messages until shutdown is requested / the thread is interrupted,
   * and may or not flush the queue before shutdown depending on the .
   */
  @Override
  public void run() {
    walQueue.drain(
        this::handleOutboundMessage,
        ADAPTIVE_100_MICROSECONDS,
        () -> !(shutdownRequested || Thread.currentThread().isInterrupted()));

    logger.debug("Thread interrupted or shutdown requested.");

    if (!isFlushOnClose) {
      logger.debug("Shutting down immediately...");
      return;
    }

    // after shutdown request, drain queue until empty
    logger.debug("Processing messages remaining in queue...");
    OutboundMsg msg;
    while ((msg = walQueue.poll()) != null) {
      handleOutboundMessage(msg);
    }

    logger.debug("Wal queue empty, shutting down...");
  }

  /**
   * Process an {@link OutboundMsg} from the queue, by publishing/writing it to the configured
   * target.
   *
   * @param msg the WAL message to send/serialize.
   */
  protected abstract void handleOutboundMessage(OutboundMsg msg);

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

  /**
   * Returns the destination {@link LogInfo}, where WAL messages are being written.
   *
   * @return the current LogInfo object
   */
  public LogInfo getCurrentWal() {
    return writeAheadLog;
  }

  /**
   * Configure the writer for a particular log.
   *
   * @param writeAheadLog log information containing details such as the Log name.
   * @param publishOffsets flag indicating whether written offsets/indexes should be published via
   *     ZeroMQ.
   */
  public abstract void writeToLog(LogInfo writeAheadLog, boolean publishOffsets);
}
