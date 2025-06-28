/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.core.messages.InboundLogMsg;
import com.quasient.pal.core.messages.PublishedOffsetMsg;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.messages.types.MessageFormatType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.channels.ClosedSelectorException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.AbstractQueue;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * LogReader is responsible for reading messages from a Kafka topic and dispatching them via a
 * ZeroMQ DEALER socket. It manages Kafka consumer configuration and offset skipping using offset
 * updates received over a ZMQ SUB socket. The class continuously polls for messages, processes them
 * based on their headers (e.g. message format and origin), and then forwards valid messages for
 * dispatching. TODO: Optimize - sampling with visualvm shows this class as the one with highest
 * memory allocation per thread.
 */
@Singleton
public class LogReader extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(LogReader.class);

  /**
   * Indicates whether the LogReader is currently accepting Log requests. Volatile ensures thread
   * visibility.
   */
  private volatile boolean acceptingRequests = false;

  /*----- ZMQ stuff ------*/

  /** ZMQ DEALER socket used for sending inbound Log messages to connected consumers. */
  private Socket logDealerSocket;

  /** ZMQ SUB socket used for receiving published offset updates when skipping written messages. */
  private Socket offsetSubscriberSocket;

  /** ZMQ address for binding the Log message receiving socket. */
  private final String inLogAddress;

  /** ZMQ address used to connect for receiving offset update messages. */
  private final String offsetPubAddress;

  /*----- Counters ------*/

  /** Cumulative nanoseconds spent polling Kafka for performance monitoring. */
  private final AtomicLong totalPollingNanos = new AtomicLong(0);

  /** Count of total Kafka poll operations performed. */
  private final AtomicInteger totalPolls = new AtomicInteger(0);

  /** Count of total messages received from Kafka. */
  private final AtomicInteger messagesReceived = new AtomicInteger(0);

  /*----- Kafka stuff ------*/

  /**
   * Flag to indicate if offsets that have already been written should be skipped during processing.
   */
  private boolean skipWrittenOffsets;

  /** Duration to use for each Kafka poll operation. */
  private final Duration pollDuration;

  /**
   * Initial Kafka offset from which to start reading; if null, reading starts from the beginning.
   */
  private Long initialOffset;

  /** Highest offset we know was *successfully* committed on the broker (-1 = none). */
  private final AtomicLong lastCommittedOffset = new AtomicLong(-1);

  /** Name of the Kafka topic (or Log) from which messages are read. */
  private String kafkaTopic;

  /** Kafka topic partition assigned to the consumer for reading messages. */
  private TopicPartition topicPartition;

  /** Flag to indicate if processed offsets are to be auto-committed. */
  private final boolean autoCommitEnabled;

  /** Kafka consumer instance used for reading messages from the Log. */
  private Consumer<String, byte[]> consumer;

  /** Properties used to configure the Kafka consumer. */
  private final Properties consumerProperties = new Properties();

  /** Tracks the last processed Kafka message offset. */
  private volatile long lastOffsetRead = -1;

  /** Timeout duration for gracefully closing the Kafka consumer. */
  private static final Duration CONSUMER_CLOSE_TIMEOUT = Duration.of(10, ChronoUnit.SECONDS);

  // synchronization to avoid busy-waiting before acceptingRequests

  /** Lock for synchronizing the acceptance of Log requests. */
  private final ReentrantLock lock = new ReentrantLock();

  /** Condition variable for signaling when the LogReader starts accepting requests. */
  private final Condition acceptingRequestsCondition = lock.newCondition();

  /**
   * Provider to obtain a connection with the Pal directory service for Log configuration retrieval.
   */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  // shared by threads OffsetUpdater and LogReader: TODO avoid sharing

  /**
   * Queue of Kafka offsets to skip, shared between the OffsetUpdater thread and the main LogReader.
   */
  private final AbstractQueue<Long> skipOffsets = new ConcurrentLinkedQueue<>();

  /** An OffsetUpdater instance, which enables skipping processing of self-produced messages. */
  private OffsetUpdater offsetUpdater;

  /**
   * A dedicated thread that listens for published offset messages through a ZMQ SUB socket. The
   * thread enqueues received offsets into the skipOffsets queue, which instructs the LogReader to
   * skip processing messages with those specific offsets.
   */
  private final class OffsetUpdater extends Thread {
    /** ZMQ subscriber socket used to receive published offset updates. */
    private final Socket offsetSubscriber;

    /**
     * Constructs an OffsetUpdater thread.
     *
     * @param offsetSubscriber ZMQ subscriber socket for receiving offset update messages.
     */
    OffsetUpdater(Socket offsetSubscriber) {
      super("offset-updater");
      this.offsetSubscriber = offsetSubscriber;
    }

    /**
     * Continuously receives published offset updates from the configured ZMQ subscriber socket and
     * enqueues them in the skipOffsets queue. The loop terminates when a shutdown is requested, the
     * thread is interrupted, or a socket error occurs.
     */
    @Override
    public void run() {
      if (logger.isDebugEnabled()) {
        logger.debug("OffsetUpdater running");
      }
      boolean socketError = false;
      while (!shutdownRequested && !Thread.interrupted() && !socketError) {
        try {
          PublishedOffsetMsg msg = PublishedOffsetMsg.receive(offsetSubscriber, true);
          assert msg != null;
          skipOffsets.add(msg.getOffset());
        } catch (ClosedSelectorException ex) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedSelectorException. Breaking out.");
          }
          socketError = true;
        } catch (NullPointerException ex) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Caught NPE during blocking read, ZMQ context probably closed. Breaking out.", ex);
          }
          socketError = true;
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught ETERM during blocking read. Breaking out.");
            }
            socketError = true;
          } else if (errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught EINTR during blocking read. Breaking out.");
            }
            socketError = true;
          } else {
            throw ex;
          }
        }
      }
    }
  }

  /**
   * Constructs a new LogReader with the required configurations for Kafka consumer and ZMQ sockets,
   * using the provided parameters.
   *
   * @param peerUuid Unique identifier for this peer.
   * @param context ZMQ context used for socket creation.
   * @param syncSocketAddress Address for service synchronization readiness.
   * @param serviceThreadGroup Thread group for service threads.
   * @param serviceName Name for this Log reader service.
   * @param keyDeserializer Kafka key deserializer class name.
   * @param valueDeserializer Kafka value deserializer class name.
   * @param autoCommit Flag to enable automatic committing of consumer offsets.
   * @param autoCommitInterval Interval (in ms) for automatic offset committing.
   * @param autoOffsetReset Strategy for resetting offsets if no offset is present.
   * @param sessionTimeout Session timeout duration for Kafka consumer.
   * @param peerId String identifier for this peer, used as Kafka consumer group id.
   * @param pollDuration Duration for which each Kafka poll will block (in millis, provided as
   *     string).
   * @param inLogAddress Address for binding the Log inbound ZMQ DEALER socket.
   * @param offsetPubAddress Address to connect for offset publishing via ZMQ SUB socket.
   * @param directoryConnectionProvider Provider for connecting to the Pal directory service to
   *     retrieve log info.
   */
  @Inject
  public LogReader(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("LogReader.service") String serviceName,
      @Named("key.deserializer") String keyDeserializer,
      @Named("value.deserializer") String valueDeserializer,
      @Named("enable.auto.commit") String autoCommit,
      @Named("auto.commit.interval.ms") String autoCommitInterval,
      @Named("auto.offset.reset") String autoOffsetReset,
      @Named("session.timeout.ms") String sessionTimeout,
      @Named("id") String peerId,
      @Named("pollDuration") String pollDuration,
      @Named("in.log") String inLogAddress,
      @Named("offset.pub") String offsetPubAddress,
      DirectoryConnectionProvider directoryConnectionProvider) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.directoryConnectionProvider = directoryConnectionProvider;
    // zmq addresses
    this.inLogAddress = inLogAddress;
    this.offsetPubAddress = offsetPubAddress;
    // prepare Kafka consumer
    this.pollDuration = Duration.of(Long.parseLong(pollDuration), ChronoUnit.MILLIS);
    consumerProperties.put("group.id", peerId);
    consumerProperties.put("key.deserializer", keyDeserializer);
    consumerProperties.put("value.deserializer", valueDeserializer);
    consumerProperties.put("enable.auto.commit", autoCommit);
    this.autoCommitEnabled = Boolean.parseBoolean(autoCommit);
    if (autoCommitEnabled) {
      consumerProperties.put("auto.commit.interval.ms", autoCommitInterval);
    }
    consumerProperties.put("auto.offset.reset", autoOffsetReset);
    consumerProperties.put("session.timeout.ms", sessionTimeout);
    StringBuilder propsStr = new StringBuilder();
    for (String propKey : consumerProperties.stringPropertyNames()) {
      propsStr
          .append(propKey)
          .append('=')
          .append(consumerProperties.getProperty(propKey))
          .append(", ");
    }
    logger.info(
        "Created log reader for peer with id '{}' and properties: [{}]", peerUuid, propsStr);
  }

  /**
   * Constructs a LogReader for testing purposes with a pre-configured Kafka consumer. This
   * constructor is primarily used for unit tests, allowing the injection of a mock Kafka consumer.
   *
   * @param peerUuid Unique identifier for this peer.
   * @param context ZMQ context used for socket operations.
   * @param syncSocketAddress Address for service synchronization readiness.
   * @param serviceThreadGroup Thread group for this service.
   * @param serviceName Name of this service.
   * @param inLogAddress Address for the inbound Log ZMQ DEALER socket.
   * @param offsetPubAddress Address for the ZMQ SUB socket to receive offset updates.
   * @param directoryConnectionProvider Provider for obtaining the Pal directory connection.
   * @param consumer A Kafka consumer instance for reading messages.
   * @param pollDuration Polling duration in milliseconds.
   */
  LogReader(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      String inLogAddress,
      String offsetPubAddress,
      DirectoryConnectionProvider directoryConnectionProvider,
      Consumer<String, byte[]> consumer,
      boolean autoCommit,
      long pollDuration) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.inLogAddress = inLogAddress;
    this.offsetPubAddress = offsetPubAddress;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.consumer = consumer;
    this.autoCommitEnabled = autoCommit;
    this.pollDuration = Duration.of(pollDuration, ChronoUnit.MILLIS);
    logger.info("Created log reader for peer with id '{}'", peerUuid);
  }

  /**
   * Configures the LogReader to start reading from a specified Log. This method sets the Kafka
   * topic, determines whether to skip externally written offsets, and configures the Kafka consumer
   * with the bootstrap servers from the Log information.
   *
   * @param log The Log information containing Log name and server details.
   * @param skipWrittenOffsets Flag indicating if already written offsets should be skipped.
   * @param initialOffset The initial Kafka offset from which to start reading; if null, processing
   *     starts from the beginning.
   * @throws Exception if an error occurs during Log configuration.
   */
  public void readFromLog(LogInfo log, boolean skipWrittenOffsets, Long initialOffset)
      throws Exception {
    this.kafkaTopic = log.getName();
    this.skipWrittenOffsets = skipWrittenOffsets;
    this.initialOffset = initialOffset;
    Optional<PalDirectory> palDirectory = directoryConnectionProvider.get();
    final LogInfo logInfo =
        palDirectory.isPresent() ? palDirectory.get().getLogInfo(log.getName()) : log;
    consumerProperties.put("bootstrap.servers", logInfo.getBootstrapServers());
    logger.info(
        "Reading from log: {}, w/ bootstrapServers: {}, starting at offset: {},"
            + " {}skipping written offsets",
        logInfo.getName(),
        logInfo.getBootstrapServers(),
        initialOffset,
        skipWrittenOffsets ? "" : "NOT ");
  }

  /**
   * Opens and initializes the necessary connections for Log reading. This method establishes
   * connections to Kafka by creating a consumer if one is not already provided. It configures
   * partition assignment and offset seeking, and when configured, the ZMQ SUB socket for receiving
   * offset updates. Additionally, it sets up the ZMQ DEALER socket for handing out Log messages to
   * the dispatching threads.
   */
  @Override
  protected void openConnections() {
    // only configure consumer if no consumer passed in constructor
    if (consumer == null) {
      this.consumer = new KafkaConsumer<>(consumerProperties);
      // manual assignment of partition so we can control offset seek
      topicPartition = new TopicPartition(kafkaTopic, 0);
      final List<TopicPartition> topicPartitionList = Collections.singletonList(topicPartition);
      consumer.assign(topicPartitionList);
      if (initialOffset == null) {
        consumer.seekToBeginning(topicPartitionList);
      } else {
        consumer.seek(topicPartition, initialOffset);
      }
    }
    this.logDealerSocket = zmqContext.createSocket(SocketType.DEALER);
    logDealerSocket.bind(inLogAddress);
    // subscriber to get the offsets written by the message writer
    if (skipWrittenOffsets) {
      this.offsetSubscriberSocket = zmqContext.createSocket(SocketType.SUB);
      offsetSubscriberSocket.connect(offsetPubAddress);
      offsetSubscriberSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
      this.offsetUpdater = new OffsetUpdater(offsetSubscriberSocket);
      this.offsetUpdater.start();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Executes the main service loop for reading from Kafka and dispatching Log messages. The loop
   * waits for the reader to accept requests, polls Kafka for records, processes each record based
   * on header metadata, and handles offset skipping. The thread exits upon interruption or when a
   * poll interruption occurs.
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
        logger.debug("Accepting requests now - polling from log: {}", kafkaTopic);
      }

      // read from kafka
      ConsumerRecords<String, byte[]> records;
      long t0;
      t0 = System.nanoTime();
      try {
        records = consumer.poll(pollDuration);
      } catch (InterruptException e) {
        break;
      }
      totalPollingNanos.getAndAdd(System.nanoTime() - t0);
      totalPolls.getAndIncrement();
      if (logger.isTraceEnabled()) {
        logger.trace("{} messages read during poll of {}", records.count(), pollDuration);
      }
      if (logger.isDebugEnabled() && records.count() > 0) {
        logger.debug("Records read: {}", records.count());
      }

      // process records if any
      for (var record : records) {
        messagesReceived.getAndIncrement();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Processing received record #{} with offset {} :\n {}",
              messagesReceived,
              record.offset(),
              record);
        }
        final long messageOffset = record.offset();
        lastOffsetRead = messageOffset;

        // get message format (JSON, BINARY, etc.)
        var messageFormat = getMessageFormatFromHeader(record.headers());
        if (messageFormat == null) {
          logger.error(
              "Message format not found in headers, skipping message with offset: {}",
              messageOffset);
          continue;
        }

        if (!recordProducedOrDispatchingBySelf(record.headers())) {
          // send request to DEALER socket
          InboundLogMsg msg =
              new InboundLogMsg(messageOffset, messageFormat, record.headers(), record.value());
          msg.send(logDealerSocket);
          if (logger.isDebugEnabled()) {
            logger.debug("Dealt new log message with offset: {}", messageOffset);
          }
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Skipped msg with offset: {}", messageOffset);
          }
        }
        // get next offset to poll
        if (skipWrittenOffsets) {
          Long nextOffset = nextOffset();
          if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Skipping received records. Jumping from offset: {} to: {}",
                  lastOffsetRead,
                  nextOffset);
            }
            consumer.seek(topicPartition, nextOffset);
            break;
          }
        }
      }

      // async-commit offsets of processed messages
      if (!autoCommitEnabled && records.count() > 0) {
        try {
          consumer.commitAsync(
              (offsets, exc) -> {
                if (exc != null) {
                  logger.warn("Async offset commit failed", exc);
                } else {
                  OffsetAndMetadata om = offsets.get(topicPartition);
                  if (om != null) {
                    // om.offset() == next to read  →  committed up-to = om.offset()-1
                    long justCommitted = om.offset() - 1;
                    lastCommittedOffset.updateAndGet(prev -> Math.max(prev, justCommitted));
                  }
                }
              });
        } catch (InterruptException e) {
          break;
        }
      }

      // let's not to be eager
      Thread.yield();

      // get next offset to poll
      if (skipWrittenOffsets) {
        Long nextOffset = nextOffset();
        if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
          if (logger.isDebugEnabled()) {
            logger.debug("Jumping from offset: {} to: {}", lastOffsetRead, nextOffset);
          }
          consumer.seek(topicPartition, nextOffset);
        }
      }
    }
  }

  /**
   * Gracefully shuts down the Kafka consumer.
   *
   * <p>Steps:
   *
   * <ol>
   *   <li>If auto-commit is disabled, determine if some processed records are still uncommitted
   *       (committed < processed), then attempt a final {@code commitSync()}.
   *   <li>If that commit is interrupted, log <em>WARN</em> and continue shutdown.
   *   <li>Temporarily clear the thread-interrupt flag, call {@code consumer.close()} so the
   *       coordinator/fetcher can close without emitting an ERROR, then restore the flag.
   * </ol>
   */
  private void closeConsumer() {
    if (consumer == null) {
      return;
    }

    try {
      // Final commit if needed when auto-commit is OFF
      if (!autoCommitEnabled) {
        long processed = lastOffsetRead; // last record handled
        long committed = lastCommittedOffset.get(); // last known broker commit

        if (committed < processed) { // something still pending
          logger.info(
              "Committing final offsets (processed={}, committed={})", processed, committed);
          try {
            Thread.interrupted(); // clear interrupted flag
            consumer.commitSync();
            lastCommittedOffset.set(processed);
          } catch (InterruptException ie) {
            Thread.currentThread().interrupt();
            logger.warn(
                "Interrupted while committing final offsets; " + "data may be re-processed", ie);
          } catch (Exception ce) {
            logger.warn("Final offset commit failed", ce);
          }
        } else {
          // Everything already flushed by the async callback
          logger.debug("All processed offsets already committed (up-to {})", committed);
        }
      }

      // normal close, shielding Kafka from the interrupt flag
      boolean wasInterrupted = Thread.interrupted(); // clears the flag & remembers state
      try {
        long startTime = System.currentTimeMillis();
        consumer.close(CONSUMER_CLOSE_TIMEOUT);
        long endTime = System.currentTimeMillis();
        long durationMillis = endTime - startTime;
        if (logger.isDebugEnabled()) {
          logger.debug("Consumer closed in {} ms", durationMillis);
        }
      } finally {
        if (wasInterrupted) {
          logger.debug("Restoring thread interrupt for outer handlers");
          Thread.currentThread().interrupt();
        }
      }

    } catch (Exception outer) {
      logger.warn("Fatal error during consumer shutdown", outer);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes all active connections including the Kafka consumer and ZeroMQ sockets. Any errors
   * encountered during closing a socket are logged.
   */
  @Override
  protected void closeConnections() {
    closeConsumer();
    closeConnection(logDealerSocket, "Error closing dealer");
    closeConnection(offsetSubscriberSocket, "Error closing offset subscriber");
    // TODO: send uncommitted offset, etc.
  }

  /**
   * Checks if the provided message headers indicate that the message was produced or dispatched by
   * the current peer. This method examines specific headers ("producer-id" and "dispatcher-id") to
   * compare against the current peer's UUID.
   *
   * @param headers Kafka message headers to inspect.
   * @return true if the message originated from the current peer, false otherwise.
   */
  private boolean recordProducedOrDispatchingBySelf(Headers headers) {
    return Stream.of("producer-id", "dispatcher-id")
        .anyMatch(
            hdrName -> {
              for (Header header : headers.headers(hdrName)) {
                UUID uuidInHeader = UuidUtils.fromBytes(header.value());
                if (peerUuid.equals(uuidInHeader)) {
                  if (logger.isDebugEnabled()) {
                    logger.debug("Will skip message with self uuid in header {}", hdrName);
                  }
                  return true;
                }
              }
              return false;
            });
  }

  /**
   * Retrieves the message format from the Kafka message headers. Searches for a header with the key
   * "message-format" and returns the corresponding MessageFormatType.
   *
   * @param headers Kafka message headers.
   * @return The MessageFormatType identified from the headers, or null if not found.
   */
  private MessageFormatType getMessageFormatFromHeader(Headers headers) {
    for (Header header : headers.headers("message-format")) {
      byte formatByte = header.value()[0];
      return MessageFormatType.fromByte(formatByte);
    }
    return null;
  }

  /**
   * Determines the next Kafka offset to read based on the last processed offset and the skipOffsets
   * queue. This method removes any stale offsets from the skipOffsets queue and iteratively
   * increments the expected offset if it matches any skip request.
   *
   * @return The computed next Kafka offset to poll.
   */
  private Long nextOffset() {
    if (logger.isTraceEnabled()) {
      final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
      logger.trace("in w/ lastOffsetRead = {}, and queue: {}", lastOffsetRead, queueStr);
    }
    // initial candidate == last read + 1
    Long nextToRead = lastOffsetRead + 1;
    Long nextOffsetToSkip = skipOffsets.peek();
    // clean up all possible offsets up to and including last read
    while ((nextOffsetToSkip != null) && (nextOffsetToSkip < nextToRead)) {
      skipOffsets.poll();
      nextOffsetToSkip = skipOffsets.peek();
    }
    // while queue not empty, pop next offsets in sequence
    while (nextToRead.equals(nextOffsetToSkip)) {
      skipOffsets.poll();
      nextToRead++;
      nextOffsetToSkip = skipOffsets.peek();
    }
    if (logger.isTraceEnabled()) {
      final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
      logger.trace(
          "out w/ nextToRead = {} with lastOffsetRead = {}, and final queue: {}",
          nextToRead,
          lastOffsetRead,
          queueStr);
    }
    return nextToRead;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initiates the shutdown process for the LogReader, including stopping the acceptance of new
   * Log requests.
   */
  @Override
  protected void triggerStop() {
    super.triggerStop();
    acceptingRequests = false;
    if (offsetUpdater != null) {
      offsetUpdater.interrupt();
    }
  }

  /**
   * Logs debug-level statistics including messages received, total polling duration, and poll
   * count.
   */
  @SuppressWarnings("unused")
  protected void logDebugStats() {
    if (logger.isDebugEnabled()) {
      logger.debug("--------STATS--------");
      logger.debug("# of messages received from k-log: {}", messagesReceived.get());
      logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
      logger.debug("# polls: {}", totalPolls.get());
      logger.debug("-----END OF STATS-----");
    }
  }

  /**
   * Checks whether the LogReader is currently accepting incoming Log requests.
   *
   * @return true if the LogReader is accepting requests, false otherwise.
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
}
