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
import com.quasient.pal.core.service.ConnectedService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Base abstract implementation of SourceLogReader containing common functionality for reading
 * messages from a log and dispatching them via ZeroMQ.
 *
 * <p>This class provides common infrastructure for log readers regardless of the underlying log
 * implementation (Kafka, Chronicle, etc.).
 */
public abstract class SourceLogReader extends ConnectedService {

  /** Logger instance for this abstract base class. */
  protected static final Logger logger = LoggerFactory.getLogger(SourceLogReader.class);

  /**
   * Indicates whether the SourceLogReader is currently accepting Log requests. Volatile ensures
   * thread visibility.
   */
  protected volatile boolean acceptingRequests = false;

  /** ZMQ address for binding the Log message receiving socket. */
  protected final String sourceLogAddress;

  /** ZMQ address used to connect for receiving offset update messages. */
  protected final String offsetPubAddress;

  /** Cumulative nanoseconds spent polling/reading for performance monitoring. */
  protected final AtomicLong totalPollingNanos = new AtomicLong(0);

  /** Count of total poll/read operations performed. */
  protected final AtomicInteger totalPolls = new AtomicInteger(0);

  /** Count of total messages received from the log. */
  protected final AtomicInteger messagesReceived = new AtomicInteger(0);

  /**
   * Flag to indicate if offsets that have already been written should be skipped during processing.
   */
  protected boolean skipWrittenOffsets;

  /**
   * Initial offset/index from which to start reading; if null, reading starts from the beginning.
   */
  protected Long initialOffset;

  /** ZMQ DEALER socket used for sending inbound Log messages to connected consumers. */
  protected Socket logDealerSocket;

  /** ZMQ SUB socket used for receiving published offset updates when skipping written messages. */
  protected Socket offsetSubscriberSocket;

  /** Lock for synchronizing the acceptance of Log requests. */
  protected final ReentrantLock lock = new ReentrantLock();

  /** Condition variable for signaling when the reader starts accepting requests. */
  protected final Condition acceptingRequestsCondition = lock.newCondition();

  /**
   * Constructs a new SourceLogReader with the required configurations.
   *
   * @param peerUuid Unique identifier for this peer.
   * @param context ZMQ context used for socket creation.
   * @param syncSocketAddress Address for service synchronization readiness.
   * @param serviceThreadGroup Thread group for service threads.
   * @param serviceName Name for this Log reader service.
   * @param sourceLogAddress Address for binding the Log inbound ZMQ DEALER socket.
   * @param offsetPubAddress Address to connect for offset publishing via ZMQ SUB socket.
   */
  protected SourceLogReader(
      UUID peerUuid,
      ZContext context,
      String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      String sourceLogAddress,
      String offsetPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.sourceLogAddress = sourceLogAddress;
    this.offsetPubAddress = offsetPubAddress;
  }

  /**
   * Configures the SourceLogReader to start reading from a specified Log. This method sets the log
   * details, determines whether to skip externally written offsets, and prepares the reader for
   * operation.
   *
   * @param log The Log information containing Log name and server details.
   * @param skipWrittenOffsets Flag indicating if already written offsets should be skipped.
   * @param initialOffset The initial offset/index from which to start reading; if null, processing
   *     starts from the beginning.
   * @param sourceLogWillBeCreated Flag indicating whether this source log will also be written to
   *     (i.e., it's also used as a WAL). When true, the log doesn't need to exist yet.
   * @throws Exception if an error occurs during Log configuration.
   */
  public abstract void readFromLog(
      LogInfo log,
      boolean skipWrittenOffsets,
      @Nullable Long initialOffset,
      boolean sourceLogWillBeCreated)
      throws Exception;

  /**
   * Checks whether the SourceLogReader is currently accepting incoming Log requests.
   *
   * @return true if the reader is accepting requests, false otherwise.
   */
  public boolean isAcceptingRequests() {
    return acceptingRequests;
  }

  /**
   * Enables or disables the acceptance of incoming Log requests. If accepting requests, signals any
   * waiting threads that the reader is ready.
   *
   * @param acceptRequests true to start accepting Log requests; false to stop.
   */
  public void acceptRequests(boolean acceptRequests) {
    lock.lock();
    try {
      this.acceptingRequests = acceptRequests;
      if (acceptRequests) {
        acceptingRequestsCondition.signalAll();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Triggers a shutdown sequence for the service.
   *
   * <p>Initiates the shutdown process for the SourceLogReader, including stopping the acceptance of
   * new Log requests.
   */
  @Override
  protected void triggerStop() {
    super.triggerStop();
    acceptingRequests = false;
  }

  /**
   * Logs debug-level statistics including messages received, total polling duration, and poll
   * count.
   */
  @SuppressWarnings("unused")
  protected void logDebugStats() {
    if (logger.isDebugEnabled()) {
      logger.debug("--------STATS--------");
      logger.debug("# of messages received from log: {}", messagesReceived.get());
      logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
      logger.debug("# polls: {}", totalPolls.get());
      logger.debug("-----END OF STATS-----");
    }
  }
}
