/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.transport.chronicle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.types.MessageFormatType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.channels.ClosedSelectorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.jctools.queues.SpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * ChronicleSourceLogReader is responsible for reading messages from a Chronicle queue and
 * dispatching them via a ZeroMQ DEALER socket. It continuously reads messages from the queue and
 * forwards valid messages for dispatching.
 */
@SuppressFBWarnings(
    value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
    justification =
        "Log reader - tailer initialized in openConnections() - two-phase initialization")
@Singleton
public class ChronicleSourceLogReader extends SourceLogReader {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ChronicleSourceLogReader.class);

  /** Shared empty headers instance — Chronicle messages have no Kafka-style headers. */
  private static final Headers EMPTY_HEADERS = new RecordHeaders();

  /**
   * SPSC queue for cross-thread delivery of Chronicle indices to skip. The {@link OffsetUpdater}
   * thread produces into this queue; the reader thread drains it into {@link #localSkipSet} before
   * each check. Array-backed chunks avoid per-element node allocation.
   */
  private final SpscUnboundedArrayQueue<Long> skipQueue = new SpscUnboundedArrayQueue<>(256);

  /**
   * Reader-thread-local set of indices to skip, populated by draining {@link #skipQueue}. No
   * concurrent access — only touched by the reader thread — so a plain {@link HashSet} suffices.
   */
  private final Set<Long> localSkipSet = new HashSet<>();

  /** OffsetUpdater instance that receives published indices to skip via ZMQ SUB. */
  private OffsetUpdater offsetUpdater;

  /**
   * A dedicated thread that listens for published offset messages through a ZMQ SUB socket. The
   * thread adds received indices to the {@link #localSkipSet} set, which instructs the reader to
   * skip dispatching messages with those specific Chronicle indices.
   */
  private final class OffsetUpdater extends Thread {

    /** ZMQ subscriber socket used to receive published offset updates. */
    private final ZMQ.Socket offsetSubscriber;

    /**
     * Constructs an OffsetUpdater thread.
     *
     * @param offsetSubscriber ZMQ subscriber socket for receiving offset update messages.
     */
    OffsetUpdater(ZMQ.Socket offsetSubscriber) {
      super("chronicle-offset-updater");
      this.offsetSubscriber = offsetSubscriber;
    }

    /**
     * Continuously receives published offset updates from the configured ZMQ subscriber socket and
     * enqueues them into the skipQueue. The loop terminates when a shutdown is requested, the
     * thread is interrupted, or a socket error occurs.
     */
    @Override
    public void run() {
      if (logger.isDebugEnabled()) {
        logger.debug("Chronicle OffsetUpdater running");
      }
      boolean socketError = false;
      while (!shutdownRequested && !interrupted() && !socketError) {
        try {
          PublishedOffsetMsg msg = PublishedOffsetMsg.receive(offsetSubscriber, true);
          if (msg == null) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Received null during blocking read, ZMQ context probably closed. Breaking out.");
            }
            socketError = true;
          } else {
            skipQueue.offer(msg.getOffset());
          }
        } catch (ClosedSelectorException ex) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedSelectorException. Breaking out.");
          }
          socketError = true;
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM || errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught ZMQ error {} during blocking read. Breaking out.", errorCode);
            }
            socketError = true;
          } else {
            throw ex;
          }
        }
      }
    }
  }

  /** Base directory for Chronicle queue files. */
  private final Path baseDir;

  /** Factory to create instances of {@link ChronicleQueue}. */
  private final ChronicleQueueFactory queueFactory;

  /** The Chronicle queue instance from which messages are read. */
  private ChronicleQueue chronicleQueue;

  /** Tailer instance used for reading messages from the queue. */
  private ExcerptTailer tailer;

  /** Name/path of the Chronicle queue from which messages are read. */
  private String queueName;

  /**
   * Constructs a new ChronicleSourceLogReader instance with the required dependencies and
   * configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this source log reader service.
   * @param sourceLogAddress address for binding the Log inbound ZMQ DEALER socket.
   * @param offsetPubAddress address to connect for offset publishing via ZMQ SUB socket.
   * @param baseDir base directory path for Chronicle queue files
   * @param queueFactory used to create queue instances for reading messages
   */
  @Inject
  public ChronicleSourceLogReader(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("LogReader.service") String serviceName,
      @Named("source.log") String sourceLogAddress,
      @Named("offset.pub") String offsetPubAddress,
      @Named("chronicleBaseDir") Path baseDir,
      ChronicleQueueFactory queueFactory) {
    super(
        peerUuid,
        context,
        syncSocketAddress,
        serviceThreadGroup,
        serviceName,
        sourceLogAddress,
        offsetPubAddress);
    this.baseDir = baseDir;
    this.queueFactory = queueFactory;
    logger.info("Created Chronicle source log reader for peer with id '{}'", peerUuid);
  }

  /**
   * Configures the ChronicleSourceLogReader to start reading from a specified Chronicle queue. This
   * method sets the queue path and initial index, and verifies the queue exists if necessary.
   *
   * @param log The Log information containing the queue name/path.
   * @param skipWrittenOffsets Flag indicating if already written offsets should be skipped. When
   *     true (source log and WAL are the same Chronicle queue), the reader subscribes to offset
   *     updates via ZMQ and skips dispatching messages at published indices.
   * @param initialOffset The initial Chronicle index from which to start reading; if null,
   *     processing starts from the beginning.
   * @param sourceLogWillBeCreated Flag indicating whether this source log will also be written to
   *     (i.e., used with --log option). When true, the queue doesn't need to exist yet.
   * @throws IllegalStateException if the Chronicle queue does not exist and sourceLogWillBeCreated
   *     is false
   */
  @Override
  public void readFromLog(
      LogInfo log,
      boolean skipWrittenOffsets,
      @Nullable Long initialOffset,
      boolean sourceLogWillBeCreated) {
    this.queueName = log.getName();
    this.skipWrittenOffsets = skipWrittenOffsets;
    this.initialOffset = initialOffset;

    // Verify the Chronicle queue exists before proceeding, but only if this source log won't be
    // created by a WAL writer. When sourceLogWillBeCreated is true (e.g., --log option), the WAL
    // writer will create the queue if it doesn't exist.
    if (!sourceLogWillBeCreated) {
      // If the queue name is an absolute path, use it directly; otherwise resolve against baseDir
      Path queueNamePath = Path.of(queueName);
      Path queuePath = queueNamePath.isAbsolute() ? queueNamePath : baseDir.resolve(queueName);

      if (!Files.exists(queuePath)) {
        String errorMsg =
            String.format(
                "Chronicle source log does not exist: %s%nEnsure the log was previously written to via --wal option.",
                queuePath);
        logger.error(errorMsg);
        throw new IllegalStateException(errorMsg);
      }
    }

    logger.info(
        "Reading from Chronicle log: {}, starting at index: {}, {}skipping written offsets",
        queueName,
        initialOffset,
        skipWrittenOffsets ? "" : "NOT ");
  }

  /**
   * Opens and initializes the necessary connections for Log reading. This method establishes the
   * Chronicle queue connection and creates the tailer. It also sets up the ZMQ DEALER socket for
   * handing out Log messages to the dispatching threads.
   */
  @Override
  protected void openConnections() {
    // If the queue name is an absolute path, use it directly; otherwise resolve against baseDir
    Path queueNamePath = Path.of(queueName);
    Path queuePath = queueNamePath.isAbsolute() ? queueNamePath : baseDir.resolve(queueName);

    logger.info("Opening Chronicle log at: {}", queuePath);

    // Create Chronicle queue (read-only)
    // Note: existence check is performed earlier in readFromLog()
    try {
      chronicleQueue = queueFactory.createReadOnly(queuePath);
    } catch (Exception e) {
      logger.error("Failed to open Chronicle log at {}: {}", queuePath, e.getMessage());
      throw new IllegalStateException(
          "Cannot open Chronicle log at "
              + queuePath
              + ". Ensure the log exists and was previously written to.",
          e);
    }

    // Create tailer
    tailer = chronicleQueue.createTailer();

    // Seek to initial index if specified
    if (initialOffset != null) {
      if (!tailer.moveToIndex(initialOffset)) {
        logger.warn(
            "Could not move to requested index {}. Log may not contain that index yet.",
            initialOffset);
      }
      logger.info("Tailer positioned at index: {}", initialOffset);
    } else {
      tailer.toStart();
      logger.info("Tailer positioned at start of log");
    }

    // Log queue info
    long firstIndex = chronicleQueue.firstIndex();
    long lastIndex = chronicleQueue.lastIndex();
    logger.info(
        "Chronicle log open: firstIndex={}, lastIndex={}, file={}",
        firstIndex,
        lastIndex,
        chronicleQueue.file());

    // Create ZMQ DEALER socket
    this.logDealerSocket = zmqContext.createSocket(SocketType.DEALER);
    logDealerSocket.bind(sourceLogAddress);

    // If skipping written offsets, subscribe to the WAL writer's offset publisher
    if (skipWrittenOffsets) {
      this.offsetSubscriberSocket = zmqContext.createSocket(SocketType.SUB);
      offsetSubscriberSocket.connect(offsetPubAddress);
      offsetSubscriberSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
      this.offsetUpdater = new OffsetUpdater(offsetSubscriberSocket);
      this.offsetUpdater.start();
      logger.info("Started Chronicle OffsetUpdater, subscribing to {}", offsetPubAddress);
    }

    logger.info("Chronicle queue reader connections open for log: {}", queuePath);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Executes the main service loop for reading from Chronicle queue and dispatching Log
   * messages. The loop waits for the reader to accept requests, reads messages from the queue,
   * processes each message, and handles seeking. The thread exits upon interruption.
   */
  @Override
  @SuppressWarnings("ThreadPriorityCheck")
  public final void run() {
    while (!Thread.interrupted()) {

      // wait until we are ready to accept requests
      if (!waitForAcceptingRequests(queueName)) {
        break;
      }

      // read from Chronicle queue — capture index before read because readNext() advances the
      // tailer past the entry, making tailer.index() return the NEXT entry's index
      final long indexBeforeRead = tailer.index();
      long t0 = System.nanoTime();
      OutboundMsg msg = OutboundMsg.readNext(tailer);
      totalPollingNanos.getAndAdd(System.nanoTime() - t0);
      totalPolls.getAndIncrement();

      if (msg != null) {
        messagesReceived.getAndIncrement();
        final long messageIndex = indexBeforeRead;

        if (logger.isDebugEnabled()) {
          logger.debug(
              "Processing received message #{} with index {} :\n type={}",
              messagesReceived.get(),
              messageIndex,
              msg.getMessageType());
        }

        // Skip self-produced messages when source log and WAL are the same queue.
        // Drain the SPSC queue into the local set first, then check.
        if (skipWrittenOffsets) {
          Long idx;
          while ((idx = skipQueue.poll()) != null) {
            localSkipSet.add(idx);
          }
        }
        if (skipWrittenOffsets && localSkipSet.remove(messageIndex)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipped self-produced message at index: {}", messageIndex);
          }
        } else {
          // Send message to DEALER socket
          InboundLogMsg inboundMsg =
              new InboundLogMsg(
                  messageIndex, MessageFormatType.BINARY, EMPTY_HEADERS, msg.getBody());
          inboundMsg.send(logDealerSocket);

          if (logger.isDebugEnabled()) {
            logger.debug("Dealt new log message with index: {}", messageIndex);
          }
        }
      }

      // let's not be too eager
      Thread.yield();
    }
  }

  /**
   * Gracefully shuts down the Chronicle queue tailer and queue.
   *
   * <p>Closes the tailer and queue resources cleanly.
   */
  private void closeChronicleQueue() {
    if (tailer != null) {
      try {
        tailer.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Tailer resources released");
        }
      } catch (Exception e) {
        logger.warn("Error releasing tailer", e);
      }
    }

    if (chronicleQueue != null) {
      try {
        chronicleQueue.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Chronicle log closed");
        }
      } catch (Exception e) {
        logger.warn("Error closing Chronicle log", e);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes all active connections including the Chronicle queue and ZeroMQ sockets. Any errors
   * encountered during closing are logged.
   */
  @Override
  protected void closeConnections() {
    closeChronicleQueue();
    closeConnection(logDealerSocket, "Error closing dealer");
    if (offsetSubscriberSocket != null) {
      closeConnection(offsetSubscriberSocket, "Error closing offset subscriber");
    }
    logger.info("Closed Chronicle source log reader connections");
  }

  /**
   * {@inheritDoc}
   *
   * <p>Triggers shutdown and stops the offset updater thread if running.
   */
  @Override
  protected void triggerStop() {
    super.triggerStop();
    if (offsetUpdater != null) {
      offsetUpdater.interrupt();
    }
  }
}
