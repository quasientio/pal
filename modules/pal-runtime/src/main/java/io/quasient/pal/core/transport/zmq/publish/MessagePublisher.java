/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.zmq.publish;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.core.internal.concurrent.AdaptiveSpinParkWaitStrategy;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.service.ConnectedService;
import io.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.UUID;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * Service responsible for publishing outbound messages.
 *
 * <p>If KafkaWalWriter writes WAL messages to Log, this services parallels that behavior,
 * forwarding such outbound messages through a tcp PUB socket instead. The service stops when
 * interrupted or upon encountering critical socket errors.
 */
@SuppressFBWarnings(
    value = {
      "DB_DUPLICATE_SWITCH_CLAUSES",
      "EI_EXPOSE_REP2",
      "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"
    },
    justification =
        "Publisher - duplicate switch intentional for clarity; two-phase initialization")
@Singleton
public class MessagePublisher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class);

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be published.
   */
  protected final MessagePassingQueue<OutboundMsg> pubQueue;

  /** Adaptive dynamic backoff strategy used by the queue drain operation. */
  private static final MessagePassingQueue.WaitStrategy ZMQ_WAIT =
      new AdaptiveSpinParkWaitStrategy(20, 50_000L); // 50 µs

  /** ZeroMQ PUB socket used to publish messages to subscribed services. */
  private Socket pubSocket;

  /** Instance of record holding runtime configuration. */
  private final MessagePublisherConfig config;

  /** # messages published since the socket first failed */
  protected long messagesPublished = 0;

  /** total of messages received from the {@code pubQueue} */
  private long messagesReceived;

  /** total of messages dropped due to PUB send unsuccessful */
  private long messagesDroppedPublishUnsuccessful = 0;

  /** total of messages dropped due to PUB socket error */
  private long messagesDroppedPublishSocketError = 0;

  /**
   * total of new messages dropped due to internal SPSC queue congestion and {@link
   * PublishingDropPolicy#DROP_NEW}
   */
  private long messagesDroppedUnforwarded = 0;

  /**
   * total of old messages dropped/evicted following {@link PublishingDropPolicy#DROP_OLD} policy.
   */
  private long messagesDroppedEvicted = 0;

  /** Snapshot of run stats taken when closing down (to capture SPSC size() before clearing). */
  private MessagePublisherStats endStats;

  /**
   * Flag that allows us to only print the 'SPSC QUEUE full' warning once, to prevent flooding the
   * logs.
   */
  private boolean spscQueueFullWarningGiven = false;

  // ---------------- forwarding/buffering ----------------

  /** SPSC queue – single producer (forwarder), single consumer (network). */
  private final SpscArrayQueue<OutboundMsg> spscQueue;

  /** Thread that owns the PUB socket and performs the actual ZMQ send(). */
  protected Thread networkThread;

  /**
   * Constructs a new MessagePublisher instance that sets up the messaging endpoints.
   *
   * @param peerUuid Unique identifier representing this peer.
   * @param context ZeroMQ context used for creating and managing sockets.
   * @param syncSocketAddress Address of the synchronization socket for service readiness.
   * @param serviceThreadGroup Thread group under which the service runs.
   * @param serviceName Unique name identifying this service instance.
   * @param pubQueue initialized {@link OutboundMsg} queue instance from which to consume
   * @param config instance of {@link MessagePublisherConfig} with the runtime configuration.
   */
  @Inject
  public MessagePublisher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("MessagePublisher.service") String serviceName,
      @Named("pub_queue") HwmMessageQueue<OutboundMsg> pubQueue,
      MessagePublisherConfig config) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.pubQueue = pubQueue;
    this.config = config;
    this.spscQueue = new SpscArrayQueue<>(config.spscSize());
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
    pubSocket.bind(config.zmqPubAddress());

    // Non-blocking policy:
    pubSocket.setLinger(config.zmqLinger());
    pubSocket.setSendTimeOut(config.zmqSendTimeOut());
    pubSocket.setSndHWM(config.zmqSndHWM());

    // kick off the network thread (consumer of the SPSC)
    networkThread = new Thread(this::networkLoop, serviceName + "-net");
    networkThread.setDaemon(false);
    networkThread.start();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Continuously receives messages from the shared Pub Queue, and forwards them to the SPSC
   * internal queue for publishing through the PUB socket. The method processes messages in {@code
   * pubQueue} until the thread is interrupted or {@code shutdownRequested = true}.
   */
  @Override
  public void run() {

    // QUEUE mode → drain external MPSC
    pubQueue.drain(
        this::forwardToNetworkThread,
        ZMQ_WAIT,
        () -> !(Thread.currentThread().isInterrupted() || shutdownRequested));
  }

  /**
   * Single hop from MPSC to SPSC – drops on overflow.
   *
   * @param msg the received message to handle. Never {@code null}.
   */
  protected void forwardToNetworkThread(OutboundMsg msg) {
    messagesReceived++;

    switch (config.dropPolicy()) {
      case DROP_NEW -> {
        if (!spscQueue.offer(msg)) {
          messagesDroppedUnforwarded++;
          warnOnce();
        }
      }
      case DROP_OLD -> { // current trimming logic
        while (!spscQueue.offer(msg)) {
          Thread.onSpinWait(); // will be trimmed soon
        }
      }
      case NONE -> { // ← new branch
        while (!spscQueue.offer(msg)) {
          Thread.onSpinWait(); // back-pressure here too
        }
      }
    }
  }

  /** Logs warning about dropping new messages, ensuring it is logged only <em>ONCE</em> */
  private void warnOnce() {
    if (!spscQueueFullWarningGiven) {
      logger.warn(
          "Internal SPSC full – dropping messages. NOTE that this warning appears only once."
              + " Total # of messages dropped will be printed on shutdown.");
      spscQueueFullWarningGiven = true;
    }
  }

  /**
   * The network thread's job: to poll the inner SPSC queue and batch messages up to {@code BATCH}
   * size, calling flushBurst() with each batch to publish them. Enforces {@link
   * PublishingDropPolicy} and honours the flush-on-close flag.
   */
  protected void networkLoop() {

    final int batchSize = config.pubBatchSize();
    final int capacity = config.spscSize();
    final int highWater = capacity * config.highWaterPercent() / 100;
    final int keepThreshold = capacity * config.keepPercent() / 100;
    final boolean flushOnClose = config.flushPubSocketOnClose();

    final OutboundMsg[] burst = new OutboundMsg[batchSize];
    int n = 0;

    for (; ; ) {

      /* ---------- shutdown handling ---------- */
      if (shutdownRequested) {
        if (!flushOnClose) { // fast path – no flush
          break;
        }
        if (spscQueue.isEmpty()) { // flush requested & done
          break;
        }
        /* else: we must flush everything → ignore interrupt flag */
      } else {
        /* honour interrupts only while service is running */
        if (Thread.currentThread().isInterrupted()) {
          break;
        }
      }
      /* --------------------------------------- */

      /* ---------- optional trimming ---------- */
      if (!shutdownRequested // trimming only while running
          && config.dropPolicy() == PublishingDropPolicy.DROP_OLD) {

        int backlog = spscQueue.size();
        if (backlog >= highWater) {
          int toDrop = backlog - keepThreshold;
          for (int i = 0; i < toDrop; i++) {
            if (spscQueue.poll() == null) break;
            messagesDroppedEvicted++;
          }
          continue; // skip send this cycle
        }
      }
      /* --------------------------------------- */

      OutboundMsg m = spscQueue.poll();

      if (m == null) { // nothing ready
        if (n > 0) { // flush partial batch
          flushBurst(burst, n);
          n = 0;
        }
        Thread.onSpinWait();
        continue;
      }

      burst[n++] = m;
      if (n == batchSize) { // reached burst size
        flushBurst(burst, n);
        n = 0;
      }
    }

    if (flushOnClose && n > 0) { // send leftovers
      flushBurst(burst, n);
    }
  }

  /**
   * Send {code batch} of dequeued {@link OutboundMsg} messages to the PUB socket. When msg.send()
   * returns false, messages are dropped.
   *
   * @param batch array of messages to be published.
   * @param size the size of the batch (i.e. # of messages to send)
   */
  protected void flushBurst(OutboundMsg[] batch, int size) {
    for (int i = 0; i < size; i++) {
      OutboundMsg msg = batch[i];
      try {
        if (msg.send(pubSocket, ZMQ.DONTWAIT)) {
          messagesPublished++;
        } else {
          messagesDroppedPublishUnsuccessful++;
        }
      } catch (ZMQException ex) {
        messagesDroppedPublishSocketError++;
      }
      batch[i] = null; // help GC
    }
  }

  /**
   * Returns a consistent, side-effect-free view of the counters.
   *
   * @return snapshot of live stats
   */
  public MessagePublisherStats getLiveStats() {
    return new MessagePublisherStats(
        messagesReceived,
        messagesPublished,
        messagesDroppedUnforwarded,
        messagesDroppedEvicted,
        spscQueue.size(),
        messagesDroppedPublishUnsuccessful,
        messagesDroppedPublishSocketError);
  }

  /**
   * Returns the stats captured during {@link #closeConnections()}.
   *
   * @return snapshot of stats at shut down, including number of unprocessed messages in SPSC queue
   */
  public MessagePublisherStats getEndStats() {
    return endStats;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stops the network thread and closes the ZeroMQ PUB socket.
   */
  @Override
  protected void closeConnections() {

    if (config.flushPubSocketOnClose() && networkThread != null) {
      waitForNetworkThread();
    } else if (networkThread != null) {
      networkThread.interrupt();
      waitForNetworkThread();
    }

    // save and print last stats
    endStats = getLiveStats();
    logger.info("Messages received: {}", endStats.messagesReceived());
    logger.info("Messages published: {}", endStats.messagesPublished());
    logger.info(
        "Messages dropped due to SPSC congestion: {}", endStats.messagesDroppedUnforwarded());
    logger.info("Messages evicted due to SPSC congestion: {}", endStats.messagesDroppedEvicted());
    logger.info("Messages remaining in SPSC queue: {}", endStats.messagesInSpsc());
    logger.info("Messages dropped due to failed send(): {}", endStats.messagesDroppedPubFail());
    logger.info("Messages dropped due to socket error: {}", endStats.messagesDroppedSocketErr());

    // close PUB socket
    closeConnection(pubSocket, "Error closing PUB socket");
    logger.info("Closed connections");

    // clear internal queue
    spscQueue.clear();
  }

  /** Waits for the network thread to finish. */
  private void waitForNetworkThread() {
    boolean interrupted = false;
    while (networkThread.isAlive()) {
      try {
        networkThread.join();
      } catch (InterruptedException ie) {
        interrupted = true; // remember and keep waiting
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt(); // restore status
    }
  }
}
