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
package io.quasient.pal.core.transport.kafka;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.types.MessageFormatType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.nio.channels.ClosedSelectorException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jctools.queues.SpscUnboundedArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * KafkaSourceLogReader is responsible for reading messages from a Kafka topic and dispatching them
 * via a ZeroMQ DEALER socket. It manages Kafka consumer configuration and offset skipping using
 * offset updates received over a ZMQ SUB socket. The class continuously polls for messages,
 * processes them based on their headers (e.g. message format and origin), and then forwards valid
 * messages for dispatching.
 */
@SuppressFBWarnings(
    value = {"CT_CONSTRUCTOR_THROW"},
    justification = "Kafka reader - constructor throws on configuration errors")
@Singleton
public class KafkaSourceLogReader extends SourceLogReader {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(KafkaSourceLogReader.class);

  /*----- Kafka-specific fields ------*/

  /** Duration to use for each Kafka poll operation. */
  private final Duration pollDuration;

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

  /**
   * Provider to obtain a connection with the Pal directory service for Log configuration retrieval.
   */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /**
   * Queue of Kafka offsets to skip, shared between the OffsetUpdater thread (producer) and the main
   * LogReader thread (consumer). Uses an SPSC unbounded array queue to avoid per-element node
   * allocation and reduce GC pressure under sustained write throughput.
   */
  private final SpscUnboundedArrayQueue<Long> skipOffsets = new SpscUnboundedArrayQueue<>(256);

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
      while (!shutdownRequested && !interrupted() && !socketError) {
        try {
          PublishedOffsetMsg msg = PublishedOffsetMsg.receive(offsetSubscriber, true);
          if (msg == null) {
            // ZMQ context probably closed - null returned from blocking receive
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Received null during blocking read, ZMQ context probably closed. Breaking out.");
            }
            socketError = true;
          } else {
            skipOffsets.add(msg.getOffset());
          }
        } catch (ClosedSelectorException ex) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedSelectorException. Breaking out.");
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
   * Constructs a new KafkaSourceLogReader with the required configurations for Kafka consumer and
   * ZMQ sockets, using the provided parameters.
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
   * @param sourceLogAddress Address for binding the Log inbound ZMQ DEALER socket.
   * @param offsetPubAddress Address to connect for offset publishing via ZMQ SUB socket.
   * @param directoryConnectionProvider Provider for connecting to the Pal directory service to
   *     retrieve log info.
   */
  @SuppressWarnings("unused")
  @Inject
  public KafkaSourceLogReader(
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
      @Named("source.log") String sourceLogAddress,
      @Named("offset.pub") String offsetPubAddress,
      DirectoryConnectionProvider directoryConnectionProvider) {
    super(
        peerUuid,
        context,
        syncSocketAddress,
        serviceThreadGroup,
        serviceName,
        sourceLogAddress,
        offsetPubAddress);
    this.directoryConnectionProvider = directoryConnectionProvider;
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
   * Constructs a KafkaSourceLogReader for testing purposes with a pre-configured Kafka consumer.
   * This constructor is primarily used for unit tests, allowing the injection of a mock Kafka
   * consumer.
   *
   * @param peerUuid Unique identifier for this peer.
   * @param context ZMQ context used for socket operations.
   * @param syncSocketAddress Address for service synchronization readiness.
   * @param serviceThreadGroup Thread group for this service.
   * @param serviceName Name of this service.
   * @param sourceLogAddress Address for the inbound Log ZMQ DEALER socket.
   * @param offsetPubAddress Address for the ZMQ SUB socket to receive offset updates.
   * @param directoryConnectionProvider Provider for obtaining the Pal directory connection.
   * @param consumer A Kafka consumer instance for reading messages.
   * @param pollDuration Polling duration in milliseconds.
   */
  KafkaSourceLogReader(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      String sourceLogAddress,
      String offsetPubAddress,
      DirectoryConnectionProvider directoryConnectionProvider,
      Consumer<String, byte[]> consumer,
      boolean autoCommit,
      long pollDuration) {
    super(
        peerUuid,
        context,
        syncSocketAddress,
        serviceThreadGroup,
        serviceName,
        sourceLogAddress,
        offsetPubAddress);
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.consumer = consumer;
    this.autoCommitEnabled = autoCommit;
    this.pollDuration = Duration.of(pollDuration, ChronoUnit.MILLIS);
    logger.info("Created Kafka source log reader for peer with id '{}'", peerUuid);
  }

  /**
   * Configures the KafkaSourceLogReader to start reading from a specified Log. This method sets the
   * Kafka topic, determines whether to skip externally written offsets, and configures the Kafka
   * consumer with the bootstrap servers from the Log information.
   *
   * @param log The Log information containing Log name and server details.
   * @param skipWrittenOffsets Flag indicating if already written offsets should be skipped.
   * @param initialOffset The initial Kafka offset from which to start reading; if null, processing
   *     starts from the beginning.
   * @throws Exception if an error occurs during Log configuration.
   */
  @Override
  public void readFromLog(
      LogInfo log,
      boolean skipWrittenOffsets,
      @Nullable Long initialOffset,
      boolean sourceLogWillBeCreated)
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
    // Note: sourceLogWillBeCreated not used for Kafka - topics can be auto-created
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
    logDealerSocket.bind(sourceLogAddress);
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
      if (!waitForAcceptingRequests(kafkaTopic)) {
        break;
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
      if (logger.isDebugEnabled() && records.count() > 0) {
        logger.debug("Records read: {}", records.count());
      }

      // process records if any
      boolean alreadySeeked = false;
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

        if (!recordProducedBySelf(record.headers())) {
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
          long nextOffset = nextOffset();
          if (nextOffset > lastOffsetRead + 1) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Skipping received records. Jumping from offset: {} to: {}",
                  lastOffsetRead,
                  nextOffset);
            }
            consumer.seek(topicPartition, nextOffset);
            alreadySeeked = true;
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

      // get next offset to poll (only if the record loop didn't already seek)
      if (skipWrittenOffsets && !alreadySeeked) {
        long nextOffset = nextOffset();
        if (nextOffset > lastOffsetRead + 1) {
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
          if (logger.isDebugEnabled()) {
            logger.debug("All processed offsets already committed (up-to {})", committed);
          }
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
          if (logger.isDebugEnabled()) {
            logger.debug("Restoring thread interrupt for outer handlers");
          }
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
  }

  /**
   * Checks if the provided message headers indicate that the message was produced by the current
   * peer. This method examines specific headers (e.g. "producer-id") to compare against the current
   * peer's UUID.
   *
   * @param headers Kafka message headers to inspect.
   * @return true if the message originated from the current peer, false otherwise.
   */
  private boolean recordProducedBySelf(Headers headers) {
    for (Header header : headers.headers("producer-id")) {
      UUID uuidInHeader = UuidUtils.fromBytes(header.value());
      if (peerUuid.equals(uuidInHeader)) {
        return true;
      }
    }
    return false;
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
  private long nextOffset() {
    // initial candidate == last read + 1
    long nextToRead = lastOffsetRead + 1;
    Long nextOffsetToSkip = skipOffsets.peek();
    // clean up all possible offsets up to and including last read
    while (nextOffsetToSkip != null && nextOffsetToSkip < nextToRead) {
      skipOffsets.poll();
      nextOffsetToSkip = skipOffsets.peek();
    }
    // while queue not empty, pop next offsets in sequence
    while (nextOffsetToSkip != null && nextOffsetToSkip == nextToRead) {
      skipOffsets.poll();
      nextToRead++;
      nextOffsetToSkip = skipOffsets.peek();
    }
    return nextToRead;
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
