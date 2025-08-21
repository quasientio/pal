/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.chronicle;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;

/**
 * <b>ChronicleWalWriter</b> consumes {@link OutboundMsg} objects from a single-consumer queue and
 * appends them to a {@link ChronicleQueue}.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Drain a high-watermark MPSC queue using a non-blocking wait strategy.
 *   <li>Convert optional internal headers into a compact persisted flag.
 *   <li>Append payload+metadata to a <em>Single Chronicle Queue</em> (SCQ) using raw bytes (no Wire
 *       field names).
 *   <li>Optionally publish the resulting index (offset) via a ZeroMQ PUB socket.
 *   <li>Expose live stats via {@link #getLiveStats()}.
 *   <li>Fail fast on unrecoverable Chronicle errors, signalling producers via {@code walFailed} and
 *       a poison pill.
 * </ul>
 *
 * <h2>Performance choices</h2>
 *
 * <ul>
 *   <li>SCQ (single file per cycle) is used; we avoid higher-level Wire APIs for best throughput.
 *   <li>Roll cycle and block size are injected so they can be tuned without code changes.
 *   <li>A simple binary layout is written (see {@link OutboundMsg}).
 * </ul>
 */
@Singleton
public class ChronicleWalWriter extends WalWriter {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ChronicleWalWriter.class);

  /** Default roll cycle if none provided. */
  private static final RollCycle DEFAULT_ROLL_CYCLE = RollCycles.TEN_MINUTELY;

  /** Default block size (bytes) if none provided. */
  private static final int DEFAULT_BLOCK_SIZE = 128 * 1024 * 1024; // 128 MiB

  /** Default index spacing if none provided. */
  private static final int DEFAULT_INDEX_SPACING = 256;

  // ─────────────────────────────── Injected collaborators & config ───────────────────────────────

  /** Base dir for chronicle queue files. */
  private final Path baseDir;

  /** Roll cycle to use for the {@link ChronicleQueue}. */
  private final RollCycle rollCycle;

  /** Block size to use for the {@link ChronicleQueue}. */
  private final int blockSize;

  /** Factory to create instances of {@link ChronicleQueue}. */
  private final ChronicleQueueFactory queueFactory;

  // ─────────────────────────────── Runtime state ───────────────────────────────

  /** The chronicle queue instance where messages are written. */
  private ChronicleQueue chronicleQueue;

  /** Appender instance */
  private ExcerptAppender appender;

  /**
   * Constructs a new ChronicleWalWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this WAL writer service.
   * @param walQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param walFailed global flag that used to indicate failure when appending to the Chronicle
   *     queue so producers halt pushing messages to {@code walQueue}.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   * @param baseDir base directory path for creating Chronicle queue files
   * @param rollCycle the roll cycle to use for the {@link ChronicleQueue}
   * @param blockSize the block size to use for the {@link ChronicleQueue}
   * @param queueFactory used to create queue instances for appending messages
   */
  @Inject
  public ChronicleWalWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("WalWriter.service") String serviceName,
      @Named("wal_queue") HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      @Named("wal.flush_on_close") @Nullable String flushOnClose,
      @Named("chronicleBaseDir") Path baseDir,
      @Named("wal.chronicle.roll_cycle") @Nullable String rollCycle,
      @Named("wal.chronicle.block_size") @Nullable String blockSize,
      ChronicleQueueFactory queueFactory) {
    super(
        peerUuid,
        context,
        syncSocketAddress,
        serviceThreadGroup,
        serviceName,
        walQueue,
        walFailed,
        offsetPubAddress,
        flushOnClose);

    this.baseDir = baseDir;
    this.rollCycle =
        rollCycle != null && !rollCycle.isBlank()
            ? RollCycles.valueOf(rollCycle)
            : DEFAULT_ROLL_CYCLE;
    this.blockSize =
        blockSize != null && !blockSize.isBlank()
            ? Integer.parseInt(blockSize)
            : DEFAULT_BLOCK_SIZE;
    this.queueFactory = queueFactory;
  }

  // ─────────────────────────────── Lifecycle ───────────────────────────────
  /** Optionally opens ZeroMQ connection for the offset publisher. */
  @Override
  protected void openConnections() {
    if (publishOffsets) {
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);
    }
    logger.info("connections open - except chronicle queue");
  }

  /**
   * Configure the writer for a particular log and build the Chronicle queue/appender.
   *
   * <p>Path resolution: {@code baseDir / logInfo.getName()}. {@inheritDoc}
   */
  @Override
  public void writeToLog(LogInfo writeAheadLog, boolean publishOffsets) {
    this.writeAheadLog = writeAheadLog;
    this.publishOffsets = publishOffsets;

    Path queuePath = baseDir.resolve(writeAheadLog.getName());

    if (chronicleQueue == null) {
      chronicleQueue = queueFactory.create(queuePath, rollCycle, DEFAULT_INDEX_SPACING, blockSize);
      appender = chronicleQueue.createAppender();
    }
    logger.info(
        "Writing to chronicle queue at: {} (rollCycle={}, blockSize={})",
        queuePath,
        rollCycle,
        blockSize);
  }

  /**
   * Continuously receives messages from the WAL Queue and processes them to be appended to the
   * configured chronicle queue. The method processes messages until shutdown requested / the thread
   * is interrupted.
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
   * Handles a single {@link OutboundMsg} taken from the WAL queue and—under normal
   * circumstances—appends it to Chronicle queue.
   *
   * <p>This method is invoked by {@code walQueue.drain(…)} from the {@code run()} loop,
   * i.e.&nbsp;always on the <em>single</em> consumer thread. It must be
   * <strong>non-blocking</strong> and <strong>exception-free</strong>; all error handling is done
   * inside the method so that the queue’s drain loop can continue (or terminate) deterministically.
   *
   * <h3>Control paths</h3>
   *
   * <ol>
   *   <li><b>Graceful exit&nbsp;⟶&nbsp;POISON_PILL</b><br>
   *       If the message is the sentinel {@link #POISON_PILL}, the method returns immediately.
   *       {@code run()} will reach the idle strategy, see that {@link Thread#isInterrupted() the
   *       interrupt flag} is now {@code true} (set elsewhere) and exit.
   *   <li><b>Normal publishing</b><br>
   *       &nbsp;&nbsp;• Delegates to {@link OutboundMsg#appendTo(ExcerptAppender)}.<br>
   *       Any exception thrown by the chronicle appender is caught by the <em>fatal&nbsp;path</em>
   *       below.
   *   <li><b>Fatal path&nbsp;⟶&nbsp;Chronicle error</b><br>
   *       When <code>OutboundMsg.appendTo</code> throws, we:
   *       <ol type="a">
   *         <li>Log the failure.
   *         <li>Atomically flip the shared {@code walFailed} flag (<em>first</em> thread wins), so
   *             producer threads stop enqueuing.
   *         <li>Clear the queue to release memory.
   *         <li>Insert a single {@link #POISON_PILL} (guarded by {@code pillSent}) to wake any
   *             parked consumer.
   *         <li>Set the thread’s interrupt flag. This causes the drain loop’s exit-condition {@code
   *             () -> !Thread.currentThread().isInterrupted()} to evaluate to {@code false} ⇒
   *             {@code run()} returns ⇒ the service thread terminates.
   *       </ol>
   *       All actions after the CAS are idempotent; if multiple messages fail in quick succession
   *       only the first one performs the cleanup.
   * </ol>
   *
   * <h3>Thread-safety &amp; memory</h3>
   *
   * <ul>
   *   <li>Called only from the consumer thread; no external synchronisation required.
   *   <li>Uses {@link java.util.concurrent.atomic.AtomicBoolean AtomicBoolean}s (`walFailed`,
   *       `pillSent`) for single-execution guarantees across possible future callers.
   *   <li>The queue is fully drained or cleared before the thread exits, so no user payload is left
   *       reachable.
   * </ul>
   *
   * @param msg the dequeued message; never {@code null}. May be the special {@link #POISON_PILL}
   *     sentinel to signal shutdown.
   */
  @Override
  protected void handleOutboundMessage(OutboundMsg msg) {
    if (POISON_PILL.equals(msg)) {
      return; // graceful exit
    }
    messagesReceived++;
    try {
      long index = msg.appendTo(appender);
      messagesWritten.incrementAndGet();

      if (publishOffsets && offsetPublisherSocket != null) {
        offsetPublisherSocket.sendMore(msg.getMessageId());
        offsetPublisherSocket.send(Long.toString(index));
      }
    } catch (Exception ex) {
      messagesDroppedError.incrementAndGet();
      logger.error(
          "Chronicle failed appending message w/id {} → halting WAL", msg.getMessageId(), ex);
      if (walFailed.compareAndSet(false, true)) {
        walQueue.clear();
        if (pillSent.compareAndSet(false, true)) {
          while (!walQueue.offer(POISON_PILL)) {
            Thread.onSpinWait();
          }
        }
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Closes all open connections and resources used by the ZMQ socket and Chronicle queue. */
  @Override
  protected void closeConnections() {
    closeConnection(offsetPublisherSocket, "Error closing offset publisher");
    closeConnection(chronicleQueue, "Error closing chronicle queue");
    logger.info("Closed connections");
  }
}
