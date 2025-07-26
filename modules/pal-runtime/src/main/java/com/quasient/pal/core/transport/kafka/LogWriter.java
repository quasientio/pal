/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.kafka;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.core.internal.concurrent.AdaptiveSpinParkWaitStrategy;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.service.ConnectedService;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.LogMessageHeader;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ConstructorCall;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.types.InternalHeaderType;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * LogWriter is responsible for retrieving messages from a MPSC queue, transforming header
 * information if needed, and publishing the messages to a Kafka Log. It also optionally publishes
 * message offset information via a ZeroMQ publisher socket.
 *
 * <p>The class manages its own connections to ZeroMQ and Kafka, performing asynchronous checks on
 * message delivery and maintaining internal counters for sent messages.
 */
@Singleton
public class LogWriter extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

  /** Factory that returns a {@link Producer} provided its properties. */
  private final ProducerFactory producerFactory;

  /**
   * Kafka producer used to send Log messages to a Kafka topic. Built lazily in {@link #writeToLog}
   * via the producer factory.
   */
  private Producer<String, byte[]> producer;

  /** Properties used to configure the Kafka producer. */
  private final Properties producerProperties = new Properties();

  /** Kafka key serializer for Wal messages. */
  private final String KEY_SERIALIZER = "com.quasient.pal.serdes.kafka.KafkaKeySerializer";

  /** Kafka value serializer for Wal messages. */
  private final String VALUE_SERIALIZER = "com.quasient.pal.serdes.kafka.KafkaMessageSerializer";

  /** Timeout duration for closing the Kafka producer. */
  private static final Duration PRODUCER_CLOSE_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);

  /**
   * Executor service used to asynchronously verify that messages are successfully sent to Kafka.
   */
  private final ExecutorService producerCheckExecutorService = Executors.newSingleThreadExecutor();

  /**
   * A MPSC (Multiple Producer Single Consumer) queue holding the OutboundMessage's to be written.
   */
  private final MessagePassingQueue<OutboundMsg> walQueue;

  /** Adaptive dynamic backoff strategy used by the queue drain operation. */
  private static final MessagePassingQueue.WaitStrategy ADAPTIVE_100_MICROSECONDS =
      new AdaptiveSpinParkWaitStrategy();

  /** ZeroMQ publisher socket used to publish message offsets when enabled. */
  private Socket offsetPublisherSocket;

  /** ZeroMQ address to bind the offset publisher socket for delivering offset updates. */
  private final String offsetPubAddress;

  /** Flag indicating whether message offsets should be published. */
  private boolean publishOffsets;

  /** Information describing the Log to which messages are written. */
  private LogInfo writeAheadLog;

  /** Poison pill to enqueue for graceful self-shutdown, when writing to Kafka fails. */
  private static final OutboundMsg POISON_PILL =
      new OutboundMsg(
          MessageType.UNKNOWN, ExecPhase.UNDEFINED, null, "POISON", null, new ConstructorCall());

  /** Atomic guard to prevent to ensure the poison pill is just sent once. */
  private final AtomicBoolean pillSent = new AtomicBoolean(false);

  /**
   * Global failure flag, used to let producer threads in {@link OutboundMessageGateway} know WAL is
   * down.
   */
  private final AtomicBoolean walFailed;

  /** Counter tracking total of messages received from the {@code walQueue} */
  private long messagesReceived;

  /** Counter tracking the number of messages successfully written to the Log. (acks received) */
  private final AtomicInteger messagesWritten = new AtomicInteger(0);

  /** total of messages dropped due to Kafka error */
  private final AtomicInteger messagesDroppedKafkaError = new AtomicInteger(0);

  /** Immutable headers used for marking messages as self-produced or dispatched. */
  private static final Map<String, Header> SELF_HEADERS = new HashMap<>();

  /**
   * Constructs a new LogWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this Log writer service.
   * @param walQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param walFailed global flag that used to indicate failure when writing to Kafka so producers
   *     halt enqueuing.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   * @param producerFactory the factory used to get an initialized Producer
   */
  @Inject
  public LogWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("LogWriter.service") String serviceName,
      @Named("wal_queue") HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      ProducerFactory producerFactory) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.walQueue = walQueue;
    this.walFailed = walFailed;
    this.offsetPubAddress = offsetPubAddress;
    this.producerFactory = producerFactory; // store for later
  }

  /**
   * Opens ZeroMQ connections for the subscriber and (optionally) for the offset publisher.
   * Additionally, initializes immutable header entries used for identifying message origins.
   */
  @Override
  protected void openConnections() {

    // start offsets publisher
    if (publishOffsets) {
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);
    }
    logger.info("connections open - except kafka producer");

    // create and store immutable headers (instead of creating with every send)
    SELF_HEADERS.put(
        "SELF_PRODUCED_HEADER", new LogMessageHeader("producer-id", UuidUtils.toBytes(peerUuid)));
    SELF_HEADERS.put(
        "SELF_DISPATCHING_HEADER",
        new LogMessageHeader("dispatcher-id", UuidUtils.toBytes(peerUuid)));
  }

  /**
   * Configures the Log writer with the designated Log information and offset publishing preference.
   * Sets Kafka producer properties based on the provided Log details and creates the producer if
   * necessary.
   *
   * @param writeAheadLog log information containing details such as the Log name and bootstrap
   *     servers.
   * @param publishOffsets flag indicating whether message offsets should be published via ZeroMQ.
   */
  public void writeToLog(LogInfo writeAheadLog, boolean publishOffsets) {
    this.writeAheadLog = writeAheadLog;
    this.publishOffsets = publishOffsets;

    producerProperties.put(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, writeAheadLog.getBootstrapServers());
    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KEY_SERIALIZER);
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER);
    producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, "0");
    producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");

    // build the producer exactly once
    if (producer == null) {
      producer = producerFactory.create(producerProperties);
    }
    logger.info(
        "Writing to log: {}, w/ bootstrapServers: {}",
        writeAheadLog.getName(),
        writeAheadLog.getBootstrapServers());
  }

  /**
   * Continuously receives messages from the WAL Queue and dispatches them to Kafka. The method
   * processes messages until the thread is interrupted.
   */
  @Override
  public void run() {

    walQueue.drain(
        this::handleOutboundMessage,
        ADAPTIVE_100_MICROSECONDS,
        () -> !(shutdownRequested || Thread.currentThread().isInterrupted()));

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Thread interrupted or shutdown requested, will process all remaining in queue...");
    }

    OutboundMsg msg;
    // after shutdown request, drain queue until empty
    while ((msg = walQueue.poll()) != null) {
      handleOutboundMessage(msg);
    }
  }

  /**
   * Handles a single {@link OutboundMsg} taken from the WAL queue and—under normal
   * circumstances—publishes it to Kafka.
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
   *       &nbsp;&nbsp;• Converts the optional internal headers to Kafka headers via {@link
   *       #fromInternalToLog(List)}.<br>
   *       &nbsp;&nbsp;• Delegates to {@link #sendToKafka(MessageFormatType, MessageType, byte[],
   *       String, String, UUID, Iterable)}.<br>
   *       Any exception thrown by the Kafka client is caught by the <em>fatal&nbsp;path</em> below.
   *   <li><b>Fatal path&nbsp;⟶&nbsp;Kafka error</b><br>
   *       When <code>sendToKafka</code> throws, we:
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
  private void handleOutboundMessage(OutboundMsg msg) {
    if (POISON_PILL.equals(msg)) { // graceful exit branch
      return;
    }

    messagesReceived++;

    try {
      // normal path
      final List<Header> logHeaders = fromInternalToLog(msg.getHeaders());
      sendToKafka(
          MessageFormatType.BINARY,
          msg.getMessageType(),
          msg.getBody(),
          msg.getMessageId(),
          msg.getResponseToId(),
          peerUuid,
          logHeaders);

    } catch (Exception ex) { // fatal path

      messagesDroppedKafkaError.getAndIncrement();
      logger.error("Kafka failed sending message w/id {} → halting WAL", msg.getMessageId(), ex);

      if (walFailed.compareAndSet(false, true)) { // publish failure
        walQueue.clear(); // free memory

        if (pillSent.compareAndSet(false, true)) {
          while (!walQueue.offer(POISON_PILL)) {
            Thread.onSpinWait();
          }
        }
        Thread.currentThread().interrupt(); // <─ stop after cleanup
      }
    }
  }

  /**
   * Converts internal header representations into headers suitable for the Log message (i.e. Kafka
   * record). The conversion checks if any header indicates a write-ahead and accordingly selects
   * the corresponding self-header.
   *
   * @param internalHeaders list of internal headers to be converted; may be null.
   * @return a list of headers appropriate for the Kafka Log message.
   */
  private List<Header> fromInternalToLog(@Nullable List<InternalHeader> internalHeaders) {
    if (logger.isDebugEnabled()) {
      StringBuilder logHeadersStr = new StringBuilder();
      if (internalHeaders != null) {
        for (InternalHeader ih : internalHeaders) {
          logHeadersStr
              .append("InternalHeader [type = ")
              .append(InternalHeaderType.fromByte(ih.getHeaderType()).name())
              .append(", value = ")
              .append(ih.getValue())
              .append("]")
              .append("\n");
        }
      }
      // remove the last \n
      if (!logHeadersStr.isEmpty()) {
        logHeadersStr.setLength(logHeadersStr.length() - 1);
      }
      logger.debug("Converting internal headers to log headers: {}", logHeadersStr);
    }
    List<Header> logHeaders = new ArrayList<>();
    boolean isWriteAhead = false;
    if (internalHeaders != null) {
      for (InternalHeader ih : internalHeaders) {
        if (ih.getHeaderType() == InternalHeaderType.WRITE_AHEAD.toByte()) {
          isWriteAhead = true;
          logHeaders.add(SELF_HEADERS.get("SELF_DISPATCHING_HEADER"));
          break;
        }
      }
    }
    if (!isWriteAhead) { // if not write-ahead, we assume it's self-produced
      logHeaders.add(SELF_HEADERS.get("SELF_PRODUCED_HEADER"));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Returning log headers: {}", logHeaders);
    }
    return logHeaders;
  }

  /**
   * Sends a message to the Kafka Log. Constructs a ProducerRecord with the provided message data,
   * appends necessary headers, and dispatches it asynchronously using the Kafka producer. An
   * executor service monitors the send operation and logs confirmation or errors.
   *
   * @param messageFormat the format of the message payload.
   * @param messageType the type identifier for the message.
   * @param message the byte array payload of the message.
   * @param messageId unique identifier for the message.
   * @param responseId identifier of the message to which this is a response.
   * @param fromPeer UUID of this peer.
   * @param headers optional iterable of additional headers; may be null.
   */
  private void sendToKafka(
      MessageFormatType messageFormat,
      MessageType messageType,
      byte[] message,
      String messageId,
      String responseId,
      UUID fromPeer,
      @Nullable Iterable<Header> headers) {
    if (logger.isDebugEnabled()) {
      logger.debug("sending new message to kafka log with id: {}", messageId);
    }
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(writeAheadLog.getName(), 0, fromPeer.toString(), message, headers);

    // add message description headers
    newRecord.headers().add("message-format", new byte[] {messageFormat.toByte()});
    newRecord.headers().add("message-type", new byte[] {messageType.getId()});

    // send the message
    Future<RecordMetadata> sendFuture;
    if (publishOffsets) {
      sendFuture =
          producer.send(newRecord, new MessageOffsetInformer(messageId, offsetPublisherSocket));
    } else {
      sendFuture = producer.send(newRecord);
    }

    producerCheckExecutorService.execute(
        () -> {
          try {
            RecordMetadata sentRecordMetadata = sendFuture.get();
            messagesWritten.getAndIncrement();
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "new message written to log at offset: {}, w/id: {},"
                      + " in response to message w/id: {} ({} bytes)",
                  sentRecordMetadata.offset(),
                  messageId,
                  responseId,
                  message.length);
            }
          } catch (Exception e) {
            messagesDroppedKafkaError.getAndIncrement();
            logger.error("Error sending message to log", e);
          }
        });
  }

  /**
   * Closes the Kafka producer using a predefined timeout to ensure graceful shutdown. Any exception
   * encountered during closure is logged as a warning.
   */
  private void closeProducer() {
    if (producer != null) {
      try {
        producer.close(PRODUCER_CLOSE_TIMEOUT);
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
  }

  /**
   * Returns a consistent, side-effect-free view of the counters.
   *
   * @return snapshot of live stats
   */
  public LogWriterStats getLiveStats() {
    return new LogWriterStats(
        messagesReceived, messagesWritten.get(), messagesDroppedKafkaError.get());
  }

  /**
   * Triggers a shutdown sequence for the service.
   *
   * <p>Override superclass because we don't want to interrupt the thread, causing in-flight sends
   * to kafka to fail.
   */
  @Override
  protected void triggerStop() {
    shutdownRequested = true;
  }

  /**
   * Closes all open connections and resources used by the LogWriter. This includes shutting down
   * the executor service, closing the Kafka producer, and closing any ZeroMQ sockets.
   */
  @Override
  protected void closeConnections() {
    // stop the producer async executor
    producerCheckExecutorService.shutdown();
    try {
      if (!producerCheckExecutorService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        producerCheckExecutorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      producerCheckExecutorService.shutdownNow();
    }
    // close the producer
    closeProducer();

    // close the offset publisher socket
    closeConnection(offsetPublisherSocket, "Error closing offset publisher");

    logger.info("Closed connections");
  }
}
