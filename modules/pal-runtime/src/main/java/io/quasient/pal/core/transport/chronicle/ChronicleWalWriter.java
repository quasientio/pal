/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.chronicle;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import zmq.ZMQ;

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
@SuppressFBWarnings(
    value = {"CT_CONSTRUCTOR_THROW", "EI_EXPOSE_REP2", "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"},
    justification =
        "WAL writer - constructor throws on configuration errors; two-phase initialization")
@Singleton
public class ChronicleWalWriter extends WalWriter {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ChronicleWalWriter.class);

  /** Default roll cycle if none provided. */
  private static final RollCycle DEFAULT_ROLL_CYCLE = RollCycles.TEN_MINUTELY;

  /** Default block size (bytes) if none provided. */
  private static final int DEFAULT_BLOCK_SIZE = 128 * 1024 * 1024; // 128 MiB

  /** Default index spacing if none provided. */
  private static final int DEFAULT_INDEX_SPACING = 1000;

  /**
   * Time cap for syncs: if this much time passes without a sync (even if count not reached), force
   * one.
   */
  private static final long SYNC_MAX_ELAPSED_NS = TimeUnit.MILLISECONDS.toNanos(2);

  // ─────────────────────────────── Injected collaborators & config ───────────────────────────────

  /** Base dir for chronicle queue files. */
  private final Path baseDir;

  /** Roll cycle to use for the {@link ChronicleQueue}. */
  private final RollCycle rollCycle;

  /** Block size to use for the {@link ChronicleQueue}. */
  private final int blockSize;

  /** Sync policy: -1 disables explicit syncs; >0 means sync every N messages or after time cap. */
  private final int syncEvery;

  /** Factory to create instances of {@link ChronicleQueue}. */
  private final ChronicleQueueFactory queueFactory;

  // ─────────────────────────────── Runtime state ───────────────────────────────

  /** The chronicle queue instance where messages are written. */
  private ChronicleQueue chronicleQueue;

  /** Appender instance used for writing messages read from the queue - single thread */
  private ExcerptAppender queueAppender;

  /** Per-thread appender instance used in direct-write mode (i.e. when running queueless) */
  private ThreadLocal<ExcerptAppender> tlAppender;

  /** Set of per-thread appenders, required for proper closing on shutdown. */
  private final Set<ExcerptAppender> perThreadAppenders = ConcurrentHashMap.newKeySet();

  /** Messages written since last explicit sync (when syncEvery > 0). */
  private final AtomicInteger msgsSinceLastSync = new AtomicInteger(0);

  /** NanoTime of last explicit sync (when syncEvery > 0). */
  private final AtomicLong lastSyncNs = new AtomicLong(0);

  /** Offsets handler-thread reuse for converting long to byte[] */
  private static final ThreadLocal<byte[]> TL8 = ThreadLocal.withInitial(() -> new byte[8]);

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
   * @param syncEvery explicit sync every N messages; -1 disables explicit syncs
   * @param queueFactory used to create queue instances for appending messages
   */
  @Inject
  public ChronicleWalWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("WalWriter.service") String serviceName,
      @Named("wal_queue") @Nullable HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      @Named("wal.flush_on_close") @Nullable String flushOnClose,
      @Named("chronicleBaseDir") Path baseDir,
      @Named("wal.chronicle.roll_cycle") @Nullable String rollCycle,
      @Named("wal.chronicle.block_size") @Nullable String blockSize,
      @Named("wal.chronicle.sync_every") @Nullable String syncEvery,
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
    this.syncEvery = (syncEvery != null && !syncEvery.isBlank()) ? Integer.parseInt(syncEvery) : -1;
    this.queueFactory = queueFactory;
    if (logger.isDebugEnabled()) {
      logger.debug(
          "new ChronicleWalWriter initialized w/offsetPubAddress={}, flushOnClose={}, baseDir={}, rollCycle={}, and syncEvery={}",
          offsetPubAddress,
          flushOnClose,
          baseDir,
          rollCycle,
          syncEvery);
    }
  }

  // ─────────────────────────────── Lifecycle ───────────────────────────────
  /** Optionally opens ZeroMQ connection for the offset publisher. */
  @Override
  protected void openConnections() {
    if (publishOffsets) {
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);

      offsetsDisruptor =
          new Disruptor<>(
              OffsetEvent::new,
              OFFSETS_RING_SIZE,
              r -> {
                Thread t = new Thread(r, serviceName + "-offset-publisher");
                t.setDaemon(true);
                return t;
              },
              ProducerType.MULTI,
              new BusySpinWaitStrategy()); // ultra-low latency

      // single owner thread of the PUB socket here => thread-safe
      offsetsDisruptor.handleEventsWith(
          (evt, sequence, endOfBatch) -> {
            // two frames: index (as 8 bytes), msgId (String)
            offsetPublisherSocket.sendMore(longToBytes(evt.index));
            offsetPublisherSocket.send(evt.msgId.getBytes(ZMQ.CHARSET));
            evt.clear();
          });

      offsetsDisruptor.start();
      offsetsRing = offsetsDisruptor.getRingBuffer();
    }

    logger.info("connections open - except chronicle queue");
  }

  /**
   * Configure the writer for a particular log and build the Chronicle queue/appender. This method
   * is intended to be called just once before starting the service.
   *
   * <p>Path resolution: {@code baseDir / logInfo.getName()}. {@inheritDoc}
   *
   * @param writeAheadLog log information containing details such as the Log name and bootstrap
   *     servers.
   * @param publishOffsets flag indicating whether message offsets should be published via ZeroMQ.
   * @throws IllegalStateException if the method is called more than once
   */
  @Override
  public void writeToLog(LogInfo writeAheadLog, boolean publishOffsets) {

    if (this.writeAheadLog != null) {
      throw new IllegalStateException(
          "writeAheadLog already set. This method can only be called once!");
    }

    this.writeAheadLog = writeAheadLog;
    this.publishOffsets = publishOffsets;

    // If the log name is an absolute path, use it directly; otherwise resolve against baseDir
    Path logNamePath = Path.of(writeAheadLog.getName());
    Path queuePath =
        logNamePath.isAbsolute() ? logNamePath : baseDir.resolve(writeAheadLog.getName());

    if (chronicleQueue == null) {
      chronicleQueue = queueFactory.create(queuePath, rollCycle, DEFAULT_INDEX_SPACING, blockSize);
    }

    if (queueless) {
      tlAppender =
          ThreadLocal.withInitial(
              () -> {
                ExcerptAppender a = chronicleQueue.createAppender();
                perThreadAppenders.add(a);
                return a;
              });
    } else {
      queueAppender = chronicleQueue.createAppender();
    }

    // initialise sync bookkeeping
    lastSyncNs.set(System.nanoTime());
    msgsSinceLastSync.set(0);

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

    if (!queueless) {
      walQueue.drain(
          m -> writeMessageUsingAppender(m, queueAppender),
          ADAPTIVE_100_MICROSECONDS,
          () -> !(shutdownRequested || Thread.currentThread().isInterrupted()));

      if (logger.isDebugEnabled()) {
        logger.debug("Thread interrupted or shutdown requested.");
      }

      if (!isFlushOnClose) {
        if (logger.isDebugEnabled()) {
          logger.debug("Shutting down immediately...");
        }
        return;
      }

      // after shutdown request, drain queue until empty
      if (logger.isDebugEnabled()) {
        logger.debug("Processing messages remaining in queue...");
      }
      OutboundMsg msg;
      while ((msg = walQueue.poll()) != null) {
        writeMessageUsingAppender(msg, queueAppender);
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Wal queue empty, shutting down...");
      }
      return;
    }

    // ───────────── Direct-write mode: don't drain, just wait for shutdown ─────────────
    logger.info("Direct-write mode enabled: queue draining is disabled; waiting for shutdown...");
    synchronized (shutdownMonitor) {
      while (!(shutdownRequested || Thread.currentThread().isInterrupted())) {
        try {
          shutdownMonitor.wait(0L); // wait indefinitely; we'll be notified on stop()
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Thread interrupted or shutdown requested.");
    }
  }

  /**
   * Handles a single {@link OutboundMsg} from the WAL queue or a direct producer thread and—under
   * normal circumstances—appends it to Chronicle queue.
   *
   * <p>This method is invoked by {@code walQueue.drain(…)} from the {@code run()} loop, or from
   * {@link io.quasient.pal.core.transport.gateway.OutboundMessageGateway} directly by the producer
   * threads. In the first case, the {@link #queueAppender} is used since it's used by a single
   * thread. In the second scenario, thread-local appender is used.
   *
   * <p>It must be <strong>non-blocking</strong> and <strong>exception-free</strong>; all error
   * handling is done inside the method so that the queue’s drain loop can continue (or terminate)
   * deterministically.
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
   *   <li>Called from the consumer thread or a per-thread local; no external synchronisation
   *       required.
   *   <li>Uses {@link java.util.concurrent.atomic.AtomicBoolean AtomicBoolean}s (`walFailed`,
   *       `pillSent`) for single-execution guarantees across possible future callers.
   *   <li>The queue is fully drained or cleared before the thread exits, so no user payload is left
   *       reachable.
   * </ul>
   *
   * @param msg the message to be written; never {@code null}. May be the special {@link
   *     #POISON_PILL} sentinel to signal shutdown.
   */
  public void writeMessageUsingAppender(OutboundMsg msg, ExcerptAppender appender) {

    if (shutdownRequested) {
      logger.warn(
          "Shutdown in progress, cannot append. Message w/id: {} will be discarded",
          msg.getMessageId());
      return;
    }

    if (POISON_PILL.equals(msg)) {
      return; // graceful exit
    }

    messagesReceived.getAndIncrement();

    try {
      long index = msg.appendTo(appender);
      messagesWritten.incrementAndGet();

      if (publishOffsets) {
        long seq = offsetsRing.next(); // or tryNext() with drop/backpressure
        try {
          OffsetEvent e = offsetsRing.get(seq);
          e.set(msg.getMessageId(), index);
        } finally {
          offsetsRing.publish(seq);
        }
      }

      if (syncEvery > 0) {
        msgsSinceLastSync.getAndIncrement();
        long now = System.nanoTime();
        if (msgsSinceLastSync.get() >= syncEvery
            || (now - lastSyncNs.get()) >= SYNC_MAX_ELAPSED_NS) {
          appender.sync(); // fsync-like durability fence
          msgsSinceLastSync.set(0);
          lastSyncNs.set(now);
        }
      }
    } catch (Exception ex) {
      messagesDroppedError.incrementAndGet();
      logger.error(
          "Chronicle failed appending message w/id {} → halting WAL", msg.getMessageId(), ex);
      if (walFailed.compareAndSet(false, true)) {
        if (!queueless) {
          walQueue.clear();
          if (pillSent.compareAndSet(false, true)) {
            while (!walQueue.offer(POISON_PILL)) {
              Thread.onSpinWait();
            }
          }
        }
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * {@inheritDoc} This method is intended to be called exclusively from producer threads through
   * {@link io.quasient.pal.core.transport.gateway.OutboundMessageGateway} and will use a
   * Thread-local appender.
   */
  @Override
  public void writeMessage(OutboundMsg msg) {
    writeMessageUsingAppender(msg, tlAppender.get());
  }

  /** Closes all open connections and resources used by the ZMQ socket and Chronicle queue. */
  @Override
  protected void closeConnections() {

    // If configured to flush on close, do a final sync before closing the queue.
    if (isFlushOnClose && queueAppender != null) {
      try {
        queueAppender.sync();
      } catch (Exception e) {
        logger.warn("Final sync on close failed", e);
      }
    }

    // close thread-local appenders
    if (queueless) {
      for (ExcerptAppender a : perThreadAppenders) {
        try {
          a.sync();
        } catch (Exception e) {
          logger.warn("Final per-thread sync failed", e);
        }
        try {
          a.close();
        } catch (Exception e) {
          logger.warn("Closing per-thread appender failed", e);
        }
      }
      perThreadAppenders.clear();
    }

    if (publishOffsets && offsetsDisruptor != null) {
      offsetsDisruptor.shutdown(); // waits for consumer to drain
    }

    closeConnection(queueAppender, "Error closing appender");
    closeConnection(offsetPublisherSocket, "Error closing offset publisher");
    closeConnection(chronicleQueue, "Error closing chronicle queue");
    logger.info("Closed connections");
  }

  /**
   * Returns a byte array representation of a long reusing a thread-local array - avoids allocations
   *
   * @param v the long value
   * @return the byte array
   */
  private static byte[] longToBytes(long v) {
    byte[] b = TL8.get();
    // big-endian
    b[0] = (byte) (v >>> 56);
    b[1] = (byte) (v >>> 48);
    b[2] = (byte) (v >>> 40);
    b[3] = (byte) (v >>> 32);
    b[4] = (byte) (v >>> 24);
    b[5] = (byte) (v >>> 16);
    b[6] = (byte) (v >>> 8);
    b[7] = (byte) v;
    return b;
  }
}
