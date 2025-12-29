/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.chronicle;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.types.MessageFormatType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nullable;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;

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
   * @param skipWrittenOffsets Flag indicating if already written offsets should be skipped (not
   *     applicable for Chronicle but kept for interface consistency).
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
      if (!acceptingRequests) {
        lock.lock();
        try {
          while (!acceptingRequests) {
            logger.debug("Waiting to start accepting requests");
            acceptingRequestsCondition.await();
          }
        } catch (InterruptedException e) {
          logger.error("Interrupted while waiting to start request polling", e);
          break;
        } finally {
          lock.unlock();
        }
        logger.debug("Accepting requests now - reading from Chronicle log: {}", queueName);
      }

      // read from Chronicle queue
      long t0 = System.nanoTime();
      OutboundMsg msg = OutboundMsg.readNext(tailer);
      totalPollingNanos.getAndAdd(System.nanoTime() - t0);
      totalPolls.getAndIncrement();

      if (msg != null) {
        messagesReceived.getAndIncrement();
        final long messageIndex = tailer.index();

        if (logger.isDebugEnabled()) {
          logger.debug(
              "Processing received message #{} with index {} :\n type={}",
              messagesReceived.get(),
              messageIndex,
              msg.getMessageType());
        }

        // Send message to DEALER socket
        // Chronicle messages don't have Kafka-style headers, so create empty headers
        Headers emptyHeaders = new RecordHeaders();
        InboundLogMsg inboundMsg =
            new InboundLogMsg(messageIndex, MessageFormatType.BINARY, emptyHeaders, msg.getBody());
        inboundMsg.send(logDealerSocket);

        if (logger.isDebugEnabled()) {
          logger.debug("Dealt new log message with index: {}", messageIndex);
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
        logger.debug("Tailer resources released");
      } catch (Exception e) {
        logger.warn("Error releasing tailer", e);
      }
    }

    if (chronicleQueue != null) {
      try {
        chronicleQueue.close();
        logger.debug("Chronicle log closed");
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
    // Chronicle reader doesn't use offset subscriber socket
    logger.info("Closed Chronicle source log reader connections");
  }
}
