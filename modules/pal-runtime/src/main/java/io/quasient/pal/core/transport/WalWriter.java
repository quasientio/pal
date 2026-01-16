/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.internal.concurrent.AdaptiveSpinParkWaitStrategy;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.service.ConnectedService;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.types.MessageType;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.jctools.queues.MessagePassingQueue;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** Base abstract implementation of WalWriter containing common functionality. */
public abstract class WalWriter extends ConnectedService {

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be written.
   */
  protected final MessagePassingQueue<OutboundMsg> walQueue;

  /** Default value for {@code flush_on_close} property. */
  protected static final boolean DEF_FLUSH_ON_CLOSE = true;

  /** Adaptive dynamic backoff strategy used by the queue drain operation. */
  protected static final MessagePassingQueue.WaitStrategy ADAPTIVE_100_MICROSECONDS =
      new AdaptiveSpinParkWaitStrategy();

  /** Offset events ring size */
  protected static final int OFFSETS_RING_SIZE = 1 << 16; // power of two

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

  /**
   * Indicates whether we are draining from the WAL queue inside run(), or if producers are writing
   * directly to the WAL using {@link #writeMessage(OutboundMsg)}
   */
  protected final boolean queueless;

  /** Monitor used to park the run() thread in direct-write mode. */
  protected final Object shutdownMonitor = new Object();

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

  /** Counter tracking total of messages received for writing */
  protected final AtomicInteger messagesReceived = new AtomicInteger(0);

  /** Counter of in-flight messages. */
  protected final AtomicInteger messagesInFlight = new AtomicInteger(0);

  /** Counter tracking the number of messages successfully written to the Log. (acks received) */
  protected final AtomicInteger messagesWritten = new AtomicInteger(0);

  /** Total of messages dropped due to write/append error */
  protected final AtomicInteger messagesDroppedError = new AtomicInteger(0);

  /** Disruptor used for publishing offsets without allocations */
  protected Disruptor<OffsetEvent> offsetsDisruptor;

  /** Ring buffer used to publish the offset events */
  protected RingBuffer<OffsetEvent> offsetsRing;

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
      String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      @Nullable HwmMessageQueue<OutboundMsg> walQueue,
      AtomicBoolean walFailed,
      String offsetPubAddress,
      @Nullable String flushOnClose) {
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
    queueless = walQueue == null;
  }

  /**
   * Returns a consistent, side-effect-free view of the counters.
   *
   * @return snapshot of live stats
   */
  public WalWriterStats getLiveStats() {
    return new WalWriterStats(
        messagesReceived.get(),
        messagesWritten.get(),
        messagesDroppedError.get(),
        messagesInFlight.get());
  }

  /**
   * Continuously receives messages from the WAL Queue and writes them to the implemented queue. The
   * method processes messages until shutdown is requested / the thread is interrupted, and may or
   * not flush the queue before shutdown depending on the value of {@link #isFlushOnClose}.
   */
  @Override
  public abstract void run();

  /**
   * Process an {@link OutboundMsg}, by writing it to the configured WAL.
   *
   * @param msg the WAL message to send/serialize.
   */
  public abstract void writeMessage(OutboundMsg msg);

  /**
   * Triggers a shutdown sequence for the service.
   *
   * <p>Override superclass because we don't want to interrupt the thread, causing in-flight
   * sends/appends, being executed from this service's main thread, to fail.
   */
  @Override
  protected void triggerStop() {
    shutdownRequested = true;
    synchronized (shutdownMonitor) {
      shutdownMonitor.notifyAll();
    }
  }

  /**
   * Configure the writer for a particular log.
   *
   * @param writeAheadLog log information containing details such as the Log name.
   * @param publishOffsets flag indicating whether written offsets/indexes should be published via
   *     ZeroMQ.
   */
  public abstract void writeToLog(LogInfo writeAheadLog, boolean publishOffsets);

  /** Simple bean holding offset publishing data */
  @SuppressFBWarnings(
      value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
      justification = "Ring buffer event - fields set via set() method for reuse")
  protected static final class OffsetEvent {
    /** The ID of the message written */
    public String msgId;

    /** The index where it was written */
    public long index;

    /** Required constructor */
    public OffsetEvent() {}

    /** sets both msg ID and index */
    public void set(String msgId, long index) {
      this.msgId = msgId;
      this.index = index;
    }

    /** clear to avoid retaining strings */
    public void clear() {
      this.msgId = null;
      this.index = 0;
    }
  }
}
