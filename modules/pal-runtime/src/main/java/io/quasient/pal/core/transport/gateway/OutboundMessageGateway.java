/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.gateway;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.internal.messages.SessionResponseMsg;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import io.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * A one-stop outbound gateway with the following responsibilities:
 *
 * <ul>
 *   <li>Enqueue a write-ahead record.
 *   <li>Fan a copy to the external PUB stream.
 *   <li>Relay a session command.
 * </ul>
 */
@SuppressFBWarnings(
    value = {"CT_CONSTRUCTOR_THROW", "EI_EXPOSE_REP2"},
    justification = "Gateway pattern - shared references to ZMQ context and WAL writer")
@Singleton
public class OutboundMessageGateway {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(OutboundMessageGateway.class);

  /**
   * A shared counter to accumulate the number of messages to be published that were dropped due to
   * queue congestion.
   */
  private static final LongAdder totalDroppedPub = new LongAdder();

  /** ZeroMQ context used to create and manage zmq sockets. */
  private final ZContext zmqContext;

  /** Network address of the session service, used for sending session-related commands. */
  private final String sessionServiceAddress;

  /**
   * Precomputed list of internal headers used for the write-ahead mechanism in message processing.
   */
  private final List<InternalHeader> writeAheadHeaders;

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be written.
   */
  private final MessagePassingQueue<OutboundMsg> walQueue;

  /**
   * Instance of the configured WAL Writer implementation, to directly send messages to WAL when not
   * using the {@link #walQueue}.
   */
  private final WalWriter walWriter;

  /** Required handle to the {@link PublishingDropPolicy}. */
  final PublishingDropPolicy publishingDropPolicy;

  /**
   * Global failure flag, used to let producer threads in {@link OutboundMessageGateway} know WAL is
   * down.
   */
  private final AtomicBoolean walFailed;

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be published.
   */
  private final HwmMessageQueue<OutboundMsg> pubQueue;

  /** A per-thread counter of messages dropped without publishing, due to queue congestion. */
  private final ThreadLocal<Long> localDroppedPub = ThreadLocal.withInitial(() -> Long.valueOf(0));

  /** Registry of per-thread WAL-queue wait stats. */
  private static final ConcurrentMap<Long, WaitStats> WAL_Q_WAIT_REGISTRY =
      new ConcurrentHashMap<>();

  /** Per-thread Wait Stats for pushing messages into the WAL queue. */
  private static final ThreadLocal<WaitStats> WAL_QUEUE_WAIT_STATS =
      ThreadLocal.withInitial(
          () -> {
            WaitStats ws = new WaitStats();
            WAL_Q_WAIT_REGISTRY.put(Thread.currentThread().getId(), ws);
            return ws;
          });

  /** Registry of per-thread PUB-queue wait stats. */
  private static final ConcurrentMap<Long, WaitStats> PUB_Q_WAIT_REGISTRY =
      new ConcurrentHashMap<>();

  /** Per-thread Wait Stats for pushing messages into the PUB queue. */
  private static final ThreadLocal<WaitStats> PUB_QUEUE_WAIT_STATS =
      ThreadLocal.withInitial(
          () -> {
            WaitStats ws = new WaitStats();
            PUB_Q_WAIT_REGISTRY.put(Thread.currentThread().getId(), ws);
            return ws;
          });

  /** Set of runtime options that dictate behavior such as, PUB, WAL, intercepts, etc. */
  private final Set<RunOptions> runOptions;

  /**
   * Thread-local REQ socket instance used for communication with the session service. This socket
   * is created and connected to the session service address upon first access.
   */
  private final ThreadLocal<Socket> threadSessionsSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(sessionServiceAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected new REQ socket to sessionServiceAddress: {}",
                sessionServiceAddress);
          }
          threadSessionsSocketCreated.set(true);
          return worker;
        }
      };

  /**
   * Thread-local flag indicating whether the session service socket has been created for the
   * current thread.
   */
  private final ThreadLocal<Boolean> threadSessionsSocketCreated =
      ThreadLocal.withInitial(() -> false);

  /**
   * Constructs a new OutboundMessageGateway with the provided communication context, identifiers,
   * message builders, directory connection provider, runtime options, and service addresses.
   *
   * @param zmqContext the ZeroMQ context used for socket creation and management
   * @param runOptions the set of runtime options that influence message processing behavior
   * @param walWriter the configured {@link WalWriter} instance
   * @param walQueue shared queue to put/offer outbound messages to write-ahead
   * @param walFailed global flag used by the KafkaWalWriter to inform of failure and WAL halting
   * @param pubQueue where to enqueue outbound messages to publish
   * @param sessionServiceAddress the address for the session service communication
   */
  @Inject
  public OutboundMessageGateway(
      ZContext zmqContext,
      UUID peerUuid,
      MessageBuilder messageBuilder,
      Set<RunOptions> runOptions,
      @Nullable WalWriter walWriter,
      @Named("wal_queue") @Nullable HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("pub_queue") HwmMessageQueue<OutboundMsg> pubQueue,
      PublishingDropPolicy publishingDropPolicy,
      @Named("sessionServiceEndpoint") @Nullable String sessionServiceAddress) {

    if (runOptions.contains(RunOptions.WITH_WAL) && (walWriter == null && walQueue == null)) {
      throw new IllegalStateException("WAL configured but both WAL writer and WAL queue are null");
    }
    this.zmqContext = zmqContext;
    this.runOptions = runOptions;
    this.walWriter = walWriter;
    this.walQueue = walQueue;
    this.walFailed = walFailed;
    this.pubQueue = pubQueue;
    this.publishingDropPolicy = publishingDropPolicy;
    this.sessionServiceAddress = sessionServiceAddress;
    this.writeAheadHeaders =
        Collections.singletonList(messageBuilder.buildWriteAheadHeader(peerUuid));
  }

  /**
   * Sends out an execution message for processing. This method delegates to the overloaded version
   * with null headers.
   *
   * @param message the message containing execution information to be sent
   * @param execPhase the phase of execution associated with the message
   * @return the resulting ExecMessage after processing and potential intercept handling
   */
  public ExecMessage sendExecMessage(Message message, ExecPhase execPhase) {
    return sendExecMessage(message, execPhase, null);
  }

  /**
   * Sends out an execution message with optional headers for processing. Handles intercept
   * processing, WAL and stream publishing based on the runtime options.
   *
   * @param message the message containing execution details; must include a non-null ExecMessage
   * @param execPhase the phase of execution during which the message is sent
   * @param headers optional headers for write-ahead or additional processing; can be null
   * @return the ExecMessage processed or returned by the callback logic, or the original if no
   *     intercepts apply
   * @throws IllegalArgumentException if the contained ExecMessage in the provided message is null
   */
  private ExecMessage sendExecMessage(
      Message message, ExecPhase execPhase, @Nullable List<InternalHeader> headers) {
    if (message.getExecMessage() == null) {
      throw new IllegalArgumentException("ExecMessage is null");
    }
    final ExecMessage execMessage = message.getExecMessage();
    if (logger.isTraceEnabled()) {
      logger.trace(
          "sendExecMessage:in w/ execMessage: {}, execPhase: {}\n, headers: {}",
          ColferUtils.format(execMessage),
          execPhase,
          headers);
    }

    MessageType messageType = MessageType.fromId(message.getMessageType());

    // write-ahead and/or publish
    if (runOptions.contains(RunOptions.WITH_WAL) || runOptions.contains(RunOptions.WITH_TCP_PUB)) {

      // concat headers if required
      List<InternalHeader> internalHeaders;
      if (headers == null) {
        internalHeaders = writeAheadHeaders;
      } else { // internalHeaders = writeAheadHeaders + headers
        internalHeaders =
            Stream.concat(writeAheadHeaders.stream(), headers.stream())
                .collect(Collectors.toList());
      }

      // prepare outbound message
      final OutboundMsg outboundMsg =
          new OutboundMsg(
              messageType,
              execPhase,
              internalHeaders,
              execMessage.getMessageId(),
              execMessage.getResponseToId(),
              message);

      if (runOptions.contains(RunOptions.WITH_WAL)) {
        writeAhead(outboundMsg);
      }

      if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
        publishMessage(outboundMsg);
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("sendExecMessage:out");
    }
    return execMessage;
  }

  /**
   * Enqueues the provided execution message for publishing by the running {@link MessagePublisher}.
   *
   * @param message the OutboundMessage to be published
   */
  private void publishMessage(OutboundMsg message) {

    /* ---------------- soft back-pressure for DropPolicy.NONE ------------ */
    if (publishingDropPolicy == PublishingDropPolicy.NONE) {

      // queue is a HwmMessageQueue, so capacity() is O(1)
      final int softCap = pubQueue.capacity() * 95 / 100;

      int spins = 0;
      WaitStats ws = null; // <── delay ThreadLocal.get()

      // Wait under the soft cap. We don't "offer" here, so only parks/parkedNanos are recorded.
      while (pubQueue.currentSize() >= softCap) {
        // 256 tight spins ≈ 100–150 ns on modern CPUs
        if ((++spins & 0xFF) != 0) {
          Thread.onSpinWait();
        } else {
          if (ws == null) {
            ws = PUB_QUEUE_WAIT_STATS.get(); // <-- first actual stall, start tracking
          }
          long t0 = System.nanoTime();
          LockSupport.parkNanos(1_000); // 1 µs
          long t1 = System.nanoTime();
          ws.parks++;
          ws.parkedNanos += (t1 - t0);
        }
      }

      // hard guarantee: enqueue or wait until it fits (stats recorded inside)
      blockUntilEnqueued(message);
      return;
    }

    /* ---------- DROP_NEW / DROP_OLD path -------------------- */
    if (!pubQueue.offer(message)) {
      PUB_QUEUE_WAIT_STATS.get().failedOffers++;
      localDroppedPub.set(localDroppedPub.get() + 1);
      totalDroppedPub.increment();
    }
  }

  /**
   * Spin/park helper to do a blocking-enqueue msg into the Pub queue.
   *
   * @param msg message to enqueue
   */
  private void blockUntilEnqueued(OutboundMsg msg) {
    int spins = 0;
    WaitStats ws = null; // <── delay ThreadLocal.get()

    while (!pubQueue.offer(msg)) {
      if (ws == null) {
        ws = PUB_QUEUE_WAIT_STATS.get(); // <-- only when we actually stall
      }
      ws.failedOffers++; // count every failed offer

      // 256 tight spins ≈ 100–150 ns on modern CPU
      if ((++spins & 0xFF) != 0) {
        Thread.onSpinWait();
        continue;
      }

      // Every 256 failed attempts, give the scheduler a chance
      long t0 = System.nanoTime();
      LockSupport.parkNanos(1_000); // 1 µs
      long t1 = System.nanoTime();
      ws.parks++;
      ws.parkedNanos += (t1 - t0);
      spins = 0; // reset counter
    }
  }

  /**
   * Writes the provided execution message when WAL is enabled and the WAL writer is not in Failed
   * state.
   *
   * @param message the OutboundMessage to be written ahead
   * @throws IllegalStateException in case of queue overflow
   */
  private void writeAhead(OutboundMsg message) throws IllegalStateException {

    if (walFailed.get()) { // WAL writer unavailable
      // silently drop (the WAL writer must have already logged the error when shutting down)
      return;
    }

    // direct-write mode
    if (walQueue == null) {
      walWriter.writeMessage(message);
      return;
    }

    int spins = 0;
    WaitStats ws = null; // <── delay ThreadLocal.get()

    while (!walQueue.offer(message)) {
      if (ws == null) {
        ws = WAL_QUEUE_WAIT_STATS.get(); // <-- only when we actually stall
      }
      ws.failedOffers++;
      // Lightweight back-off
      if ((++spins & 0xFF) == 0) {
        long t0 = System.nanoTime();
        // after 256 failed offers, give scheduler a chance
        LockSupport.parkNanos(1_000); // 1 µs
        long t1 = System.nanoTime();
        ws.parks++;
        ws.parkedNanos += (t1 - t0); // actual parked duration
        if (walFailed.get()) {
          return; // re-check failure flag
        }
      }
    }
  }

  /**
   * Sends a session command message to the session service and returns the corresponding response.
   * It uses a thread-local REQ socket to communicate with the session service.
   *
   * @param sessionCommandMsg the session command message to be sent
   * @return the SessionResponseMsg received from the session service, or null if the message was
   *     not sent successfully
   */
  public SessionResponseMsg sendMessageToSessionService(SessionCommandMsg sessionCommandMsg) {

    if (!runOptions.contains(RunOptions.WITH_SESSIONS) || sessionServiceAddress == null) {
      throw new RuntimeException("Session service not available, or endpoint not set");
    }

    SessionResponseMsg responseMessage = null;
    final Socket sessionServiceSocket = threadSessionsSocket.get();
    final boolean msgSent = sessionCommandMsg.send(sessionServiceSocket);
    if (msgSent) {
      if (logger.isDebugEnabled()) {
        logger.debug("Sent session command message: {}", sessionCommandMsg);
      }
      responseMessage = SessionResponseMsg.receive(sessionServiceSocket, true);
      if (logger.isDebugEnabled()) {
        logger.debug("Received session response message: {}", responseMessage);
      }
    }
    return responseMessage;
  }

  /** Snapshot only the per-thread PUB wait stats (no aggregation). */
  public List<ThreadWaitSnapshot> getPUBWaitSnapshot() {
    List<ThreadWaitSnapshot> list = new ArrayList<>();
    PUB_Q_WAIT_REGISTRY.forEach(
        (id, ws) -> {
          Thread t = findThread(id);
          String name = (t != null) ? t.getName() : ("thread-" + id);
          list.add(new ThreadWaitSnapshot(id, name, ws.parkedNanos, ws.parks, ws.failedOffers));
        });
    return List.copyOf(list); // immutable copy
  }

  /** Snapshot only the per-thread WAL wait stats (no aggregation). */
  public List<ThreadWaitSnapshot> getWALWaitSnapshot() {
    List<ThreadWaitSnapshot> list = new ArrayList<>();
    WAL_Q_WAIT_REGISTRY.forEach(
        (id, ws) -> {
          Thread t = findThread(id);
          String name = (t != null) ? t.getName() : ("thread-" + id);
          list.add(new ThreadWaitSnapshot(id, name, ws.parkedNanos, ws.parks, ws.failedOffers));
        });
    return List.copyOf(list); // immutable copy
  }

  /** Returns a consistent, side-effect-free view of the PUB queue stats and counters. */
  public MessageQueueStats getPubQueueStats() {

    long totalParked = 0;
    int totalParks = 0;
    int totalFailed = 0;

    List<ThreadWaitSnapshot> perThread = getPUBWaitSnapshot();

    for (ThreadWaitSnapshot s : perThread) {
      totalParked += s.parkedNanos();
      totalParks += s.parks();
      totalFailed += s.failedOffers();
    }

    return new MessageQueueStats(
        totalDroppedPub.sum(), totalParked, totalParks, totalFailed, perThread);
  }

  /** Returns a consistent, side-effect-free view of the WAL queue stats and counters. */
  public MessageQueueStats getWalQueueStats() {
    long totalParked = 0;
    int totalParks = 0;
    int totalFailed = 0;

    List<ThreadWaitSnapshot> perThread = getWALWaitSnapshot();

    for (ThreadWaitSnapshot s : perThread) {
      totalParked += s.parkedNanos();
      totalParks += s.parks();
      totalFailed += s.failedOffers();
    }

    return new MessageQueueStats(0, totalParked, totalParks, totalFailed, perThread);
  }

  /**
   * Cheap-ish way to find a thread by id. Used to get its name for stats.
   *
   * @param id thread id to find on the stack traces
   * @return the thread with the given id, or null if not found
   */
  private static Thread findThread(long id) {
    // cheap-ish lookup
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if (t.getId() == id) return t;
    }
    return null;
  }

  /** Prints aggregated statistics regarding message write-ahead and publishing. */
  public void printAggregateStats() {

    if (runOptions.contains(RunOptions.WITH_TCP_PUB)) {
      MessageQueueStats s = getPubQueueStats();
      long parkedMicros = TimeUnit.NANOSECONDS.toMicros(s.totalParkedNanos());

      logger.info(
          "PUB Queue backpressure: parks={}, parked={}µs, failedOffers={}",
          s.totalParks(),
          parkedMicros,
          s.totalFailedOffers());
      logger.info(
          "Dropped {} messages unpublished due to PUB queue congestion", s.messagesDropped());

      if (logger.isDebugEnabled()) {
        for (ThreadWaitSnapshot tws : s.perThread()) {
          logger.debug(
              "T[{}:{}] parks={}, parked={}µs, failedOffers={}",
              tws.threadId(),
              tws.threadName(),
              tws.parks(),
              TimeUnit.NANOSECONDS.toMicros(tws.parkedNanos()),
              tws.failedOffers());
        }
      }
    }

    if (runOptions.contains(RunOptions.WITH_WAL)) {
      MessageQueueStats s = getWalQueueStats();
      long parkedMicros = TimeUnit.NANOSECONDS.toMicros(s.totalParkedNanos());

      logger.info(
          "WAL Queue backpressure: parks={}, parked={}µs, failedOffers={}",
          s.totalParks(),
          parkedMicros,
          s.totalFailedOffers());

      if (logger.isDebugEnabled()) {
        for (ThreadWaitSnapshot tws : s.perThread()) {
          logger.debug(
              "T[{}:{}] parks={}, parked={}µs, failedOffers={}",
              tws.threadId(),
              tws.threadName(),
              tws.parks(),
              TimeUnit.NANOSECONDS.toMicros(tws.parkedNanos()),
              tws.failedOffers());
        }
      }
    }
  }

  /**
   * Closes all thread-local sockets used for session service communication. Prior to closing, the
   * method prints performance statistics and cleans up thread-local state.
   */
  public void closeThreadLocalSockets() {

    if (Boolean.TRUE.equals(threadSessionsSocketCreated.get())) {
      Socket socket = threadSessionsSocket.get();
      if (socket != null) {
        socket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local REQ socket for session service closed");
        }
      }
      threadSessionsSocket.remove();
    }
    threadSessionsSocketCreated.remove();
  }
}
