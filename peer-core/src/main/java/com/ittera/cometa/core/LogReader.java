package com.ittera.cometa.core;

import com.google.common.primitives.Ints;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.common.util.UUIDUtils;
import com.ittera.cometa.core.messages.InboundLogMsg;
import com.ittera.cometa.core.messages.PublishedOffsetMsg;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageType;
import java.nio.channels.ClosedSelectorException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

/**
 * TODO Optimize - :sampling with visualvm shows this class as the one with highest memory
 * allocation per thread.
 */
@Singleton
public class LogReader extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(LogReader.class);

  private volatile boolean acceptingRequests = false;

  // zmq stuff
  private Socket logDealer;
  private Socket offsetSubscriber;
  private final String inLogAddress, offsetPubAddress;

  // counters
  private final AtomicLong totalPollingNanos = new AtomicLong(0);
  private final AtomicInteger totalPolls = new AtomicInteger(0);
  private final AtomicInteger messagesRcvd = new AtomicInteger(0);

  // kafka stuff
  private boolean skipWrittenOffsets;
  private final Duration pollDuration;
  private Long initialOffset;
  private String kafkaTopic;
  private TopicPartition topicPartition;
  private Consumer<String, byte[]> consumer;
  private final Properties consumerProperties = new Properties();
  private volatile long lastOffsetRead = -1;

  // pal directory
  private PALDirectory palDirectory;

  // shared by threads OffsetUpdater and LogReader: TODO avoid sharing
  private final AbstractQueue<Long> skipOffsets = new ConcurrentLinkedQueue<>();

  private final class OffsetUpdater extends Thread {
    private final Socket offsetSubscriber;

    OffsetUpdater(Socket offsetSubscriber) {
      super("offset-updater");
      this.offsetSubscriber = offsetSubscriber;
    }

    @Override
    public void run() {
      if (logger.isDebugEnabled()) {
        logger.debug("OffsetUpdater running");
      }
      while (!shutdownRequested && !Thread.interrupted()) {
        try {
          PublishedOffsetMsg msg = PublishedOffsetMsg.recvMsg(offsetSubscriber);
          if (msg == null) {
            continue;
          }
          skipOffsets.add(msg.getOffset());
        } catch (ClosedSelectorException ex) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ClosedSelectorException. Breaking out.");
          }
          break;
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught ETERM during blocking read. Breaking out.");
            }
            break;
          } else if (errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught EINTR during blocking read. Breaking out.");
            }
            break;
          } else {
            throw ex;
          }
        }
      }
    }
  }

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
      PALDirectory palDirectory) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.palDirectory = palDirectory;
    // zmq addresses
    this.inLogAddress = inLogAddress;
    this.offsetPubAddress = offsetPubAddress;
    // prepare Kafka consumer
    this.pollDuration = Duration.of(Long.parseLong(pollDuration), ChronoUnit.MILLIS);
    consumerProperties.put("group.id", peerId);
    consumerProperties.put("key.deserializer", keyDeserializer);
    consumerProperties.put("value.deserializer", valueDeserializer);
    consumerProperties.put("enable.auto.commit", autoCommit);
    consumerProperties.put("auto.commit.interval.ms", autoCommitInterval);
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
        "Created log reader for peer with id '{}' and properties: [{}]",
        peerUuid,
        propsStr.toString());
  }

  /** Used from unit tests with MockConsumer */
  LogReader(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      String inLogAddress,
      String offsetPubAddress,
      PALDirectory palDirectory,
      Consumer<String, byte[]> consumer,
      long pollDuration) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.inLogAddress = inLogAddress;
    this.offsetPubAddress = offsetPubAddress;
    this.palDirectory = palDirectory;
    this.consumer = consumer;
    this.pollDuration = Duration.of(pollDuration, ChronoUnit.MILLIS);
    logger.info("Created log reader for peer with id '{}'", peerUuid);
  }

  public void readFromLog(String logName, boolean skipWrittenOffsets, Long initialOffset)
      throws Exception {
    this.kafkaTopic = logName;
    this.skipWrittenOffsets = skipWrittenOffsets;
    this.initialOffset = initialOffset;
    LogInfo logInfo = palDirectory.getLogInfo(logName);
    consumerProperties.put("bootstrap.servers", logInfo.getBootstrapServers());
    logger.info(
        "Reading from log: {}, w/ bootstrapServers: {}, starting at offset: {}, {}skipping written offsets",
        logInfo.getName(),
        logInfo.getBootstrapServers(),
        initialOffset,
        skipWrittenOffsets ? "" : "NOT ");
  }

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
    this.logDealer = zmqContext.createSocket(SocketType.DEALER);
    logDealer.bind(inLogAddress);
    // subscriber to get the offsets written by the message writer
    if (skipWrittenOffsets) {
      this.offsetSubscriber = zmqContext.createSocket(SocketType.SUB);
      offsetSubscriber.connect(offsetPubAddress);
      offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
      new OffsetUpdater(offsetSubscriber).start();
    }
  }

  @Override
  public final void run() {
    main_loop:
    while (!Thread.interrupted()) {

      while (!acceptingRequests) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          break main_loop;
        }
      }

      // read from kafka
      ConsumerRecords<String, byte[]> records;
      long t0;
      t0 = System.nanoTime();
      try {
        records = consumer.poll(pollDuration);
      } catch (InterruptException e) {
        break main_loop;
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
      for (ConsumerRecord record : records) {
        messagesRcvd.getAndIncrement();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Processing received record # {} with offset {} :\n {}",
              messagesRcvd,
              record.offset(),
              record);
        }
        final long messageOffset = record.offset();
        lastOffsetRead = messageOffset;
        if (!recordProducedOrDispatchingBySelf(record.headers())) {
          // send request to DEALER socket
          InboundLogMsg msg =
              new InboundLogMsg(
                  getMessageType(record.headers()), messageOffset, (byte[]) record.value());
          msg.send(logDealer);
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
      // short pause, not to be eager
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        break;
      }
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

  @Override
  protected void closeConnections() {
    closeConnection(consumer, "Error closing consumer");
    closeConnection(logDealer, "Error closing dealer");
    closeConnection(offsetSubscriber, "Error closing offset subscriber");
    // TODO: send uncommitted offset, etc.
  }

  private boolean recordProducedOrDispatchingBySelf(Headers headers) {
    return Stream.of("produced-by", "dispatching-by")
        .anyMatch(
            hdrName -> {
              for (Header header : headers.headers(hdrName)) {
                UUID uuidInHeader = UUIDUtils.fromBytes(header.value());
                if (peerUuid.equals(uuidInHeader)) {
                  if (logger.isDebugEnabled()) {
                    logger.debug("Will skip message {} self", hdrName);
                  }
                  return true;
                }
              }
              return false;
            });
  }

  private MessageType getMessageType(Headers headers) {
    for (Header header : headers.headers("type")) {
      return MessageType.values[Ints.fromByteArray(header.value())];
    }
    return MessageType.Unknown;
  }

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

  @Override
  protected void triggerStop() {
    super.triggerStop();
    acceptingRequests = false;
  }

  protected void logDebugStats() {
    if (logger.isDebugEnabled()) {
      logger.debug("--------STATS--------");
      logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
      logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
      logger.debug("# polls: {}", totalPolls.get());
      logger.debug("-----END OF STATS-----");
    }
  }

  public boolean isAcceptingRequests() {
    return acceptingRequests;
  }

  public void acceptRequests(boolean acceptRequests) {
    this.acceptingRequests = acceptRequests;
  }
}
