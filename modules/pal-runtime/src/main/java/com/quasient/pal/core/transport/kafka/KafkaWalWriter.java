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
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.transport.AbstractWalWriter;
import com.quasient.pal.messages.LogMessageHeader;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;

/**
 * <b>KafkaWalWriter</b> consumes {@link OutboundMsg} objects from a single-consumer queue and
 * appends them to a Kafka log.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Drain a high-watermark MPSC queue using a non-blocking wait strategy.
 *   <li>Convert optional internal headers into a compact persisted flag.
 *   <li>Send the payload+metadata to a Kafka log.
 *   <li>Optionally publish the resulting offset via a ZeroMQ PUB socket.
 *   <li>Expose live stats via {@link #getLiveStats()}.
 *   <li>Fail fast on unrecoverable Kafka errors, signalling producers via {@code walFailed} and a
 *       poison pill.
 * </ul>
 */
@Singleton
public class KafkaWalWriter extends AbstractWalWriter {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(KafkaWalWriter.class);

  /** Default value for Kafka Producer's {@code linger.ms} value. */
  private static final long DEF_LINGER_MS = 25;

  /** Default value for Kafka Producer's {@code batch.size} value. */
  private static final int DEF_BATCH_SIZE = 128000;

  /** Default value for Kafka Producer's {@code compression.type} value. */
  private static final String DEF_COMPRESSION_TYPE = "zstd";

  /** Default value for Kafka Producer's {@code buffer.memory} value. */
  private static final long DEF_BUFFER_MEMORY = 128_000_000;

  /** Kafka Producer metrics we care about */
  private static final Set<String> PRODUCER_KEY_METRICS =
      Set.of(
          "outgoing-byte-total",
          "outgoing-byte-rate",
          "record-send-rate",
          "request-latency-avg",
          "produce-throttle-time-avg",
          "record-error-rate",
          "record-retry-rate",
          "batch-size-avg",
          "compression-rate-avg",
          "buffer-available-bytes",
          "requests-in-flight");

  /** Factory that returns a {@link Producer} provided its properties. */
  private final ProducerFactory producerFactory;

  /**
   * Kafka producer used to send Log messages to a Kafka topic. Built lazily in {@link #writeToLog}
   * via the producer factory.
   */
  private Producer<String, byte[]> producer;

  /** Properties used to configure the Kafka producer. */
  private final Properties producerProperties = new Properties();

  /** List of immutable headers to attach to every message. */
  private final List<Header> logHeaders;

  /**
   * Constructs a new KafkaWalWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this WAL writer service.
   * @param walQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param walFailed global flag that used to indicate failure when writing to Kafka so producers
   *     halt enqueuing.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   * @param lingerMs value for Kafka Producer's {@code linger.ms}
   * @param batchSize value for Kafka Producer's {@code batch.size}
   * @param compressionType value for Kafka Producer's {@code compression.type}
   * @param bufferMemory value for Kafka Producer's {@code buffer.memory}
   * @param producerFactory the factory used to get an initialized Producer
   */
  @Inject
  public KafkaWalWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("WalWriter.service") String serviceName,
      @Named("wal_queue") HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      @Named("wal.kafka.linger_ms") @Nullable String lingerMs,
      @Named("wal.kafka.batch_size") @Nullable String batchSize,
      @Named("wal.kafka.compression_type") @Nullable String compressionType,
      @Named("wal.kafka.buffer_memory") @Nullable String bufferMemory,
      ProducerFactory producerFactory) {
    super(
        peerUuid,
        context,
        syncSocketAddress,
        serviceThreadGroup,
        serviceName,
        walQueue,
        walFailed,
        offsetPubAddress);
    this.producerFactory = producerFactory;

    // create and store immutable headers
    logHeaders = List.of(new LogMessageHeader("producer-id", UuidUtils.toBytes(peerUuid)));

    // set Kafka's Producer properties
    setProducerProperties(lingerMs, batchSize, compressionType, bufferMemory);
  }

  /** Optionally opens ZeroMQ connection for the offset publisher. */
  @Override
  protected void openConnections() {

    // start offsets publisher
    if (publishOffsets) {
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);
    }
    logger.info("connections open - except kafka producer");
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
  @Override
  public void writeToLog(LogInfo writeAheadLog, boolean publishOffsets) {
    this.writeAheadLog = writeAheadLog;
    this.publishOffsets = publishOffsets;

    // bootstrap servers are found in the LogInfo
    producerProperties.put(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, writeAheadLog.getBootstrapServers());

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
   * Set Kafka's Producer properties from constructor parameters. If these are null, then the
   * default values defined in this class are used.
   *
   * @param lingerMs value for Kafka Producer's {@code linger.ms}
   * @param batchSize value for Kafka Producer's {@code batch.size}
   * @param compressionType value for Kafka Producer's {@code compression.type}
   * @param bufferMemory value for Kafka Producer's {@code buffer.memory}
   */
  private void setProducerProperties(
      @Nullable String lingerMs,
      @Nullable String batchSize,
      @Nullable String compressionType,
      @Nullable String bufferMemory) {

    // Producer constants (hard-coded properties)
    String KEY_SERIALIZER = "com.quasient.pal.serdes.kafka.KafkaKeySerializer";
    String VALUE_SERIALIZER = "com.quasient.pal.serdes.kafka.KafkaMessageSerializer";

    producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KEY_SERIALIZER);
    producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, VALUE_SERIALIZER);
    producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProperties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    // Producer props from CLI args / ENV vars
    producerProperties.put(
        ProducerConfig.LINGER_MS_CONFIG,
        lingerMs != null ? Long.parseLong(lingerMs) : DEF_LINGER_MS);

    producerProperties.put(
        ProducerConfig.BATCH_SIZE_CONFIG,
        batchSize != null ? Integer.parseInt(batchSize) : DEF_BATCH_SIZE);

    producerProperties.put(
        ProducerConfig.COMPRESSION_TYPE_CONFIG,
        compressionType != null ? compressionType : DEF_COMPRESSION_TYPE);

    producerProperties.put(
        ProducerConfig.BUFFER_MEMORY_CONFIG,
        bufferMemory != null ? Long.parseLong(bufferMemory) : DEF_BUFFER_MEMORY);
  }

  /**
   * Continuously receives messages from the WAL Queue and dispatches them to Kafka. The method
   * processes messages until shutdown requested / the thread is interrupted.
   */
  @Override
  public void run() {

    walQueue.drain(
        this::handleOutboundMessage,
        ADAPTIVE_100_MICROSECONDS,
        () -> !(shutdownRequested || Thread.currentThread().isInterrupted()));

    logger.debug(
        "Thread interrupted or shutdown requested. Processing messages remaining in queue...");

    // after shutdown request, drain queue until empty
    OutboundMsg msg;
    while ((msg = walQueue.poll()) != null) {
      handleOutboundMessage(msg);
    }

    logger.debug("Wal queue empty, shutting down...");
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

      sendToKafka(
          MessageFormatType.BINARY,
          msg.getMessageType(),
          msg.getBody(),
          msg.getMessageId(),
          msg.getResponseToId(),
          peerUuid,
          logHeaders);

    } catch (Exception ex) { // fatal path

      messagesDroppedError.getAndIncrement();
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

    if (logger.isTraceEnabled()) {
      logger.trace("sending new message to kafka log with id: {}", messageId);
    }
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(writeAheadLog.getName(), 0, fromPeer.toString(), message, headers);

    // add message description headers
    newRecord.headers().add("message-format", new byte[] {messageFormat.toByte()});
    newRecord.headers().add("message-type", new byte[] {messageType.getId()});

    final String mid = messageId;
    final String rid = responseId;
    final int payloadSize = message.length;

    // compose callback
    Callback baseCb =
        (metadata, exception) -> {
          try {
            if (exception != null) {
              messagesDroppedError.incrementAndGet();
              logger.error("Error sending message to log", exception);
              return;
            }
            messagesWritten.incrementAndGet();
            if (logger.isTraceEnabled()) {
              logger.trace(
                  "New message written to log at offset: {}, w/id: {}, in response to message w/id: {} ({} bytes)",
                  metadata.offset(),
                  mid,
                  rid,
                  payloadSize);
            }
          } finally {
            messagesInFlight.decrementAndGet();
          }
        };

    Callback callback = baseCb;

    if (publishOffsets) {
      Callback informer = new MessageOffsetInformer(mid, offsetPublisherSocket);
      callback =
          (md, ex) -> {
            try {
              informer.onCompletion(md, ex);
            } finally {
              baseCb.onCompletion(md, ex);
            }
          };
    }

    messagesInFlight.incrementAndGet();
    Future<RecordMetadata> unused = producer.send(newRecord, callback);
  }

  /** Closes the Kafka producer with no timeout, allowing for all sent requests to finish. */
  private void closeProducer() {
    if (producer != null) {
      try {
        producer.close();
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
  }

  /**
   * Closes all open connections and resources used by the KafkaWalWriter. This includes shutting
   * down the executor service, closing the Kafka producer, and closing any ZeroMQ sockets.
   */
  @Override
  protected void closeConnections() {

    // make producer flush all outstanding sends (blocks until callbacks run)
    try {
      if (producer != null) {
        logger.debug("Flusing producer...");
        producer.flush();
        logger.debug("Producer flushed");
      }
    } catch (Exception e) {
      logger.warn("flush failed during close", e);
    }

    // wait a little for any callbacks still running (defensive)
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
    if (messagesInFlight.get() > 0) {
      logger.debug("Waiting a little for callbacks still running");
      while (messagesInFlight.get() > 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
    }

    if (messagesInFlight.get() > 0) {
      logger.warn("{} in-flight messages remained after shut down", messagesInFlight.get());
    }

    // log Producer metrics
    if (logger.isDebugEnabled()) {
      logger.debug("PRODUCER METRICS:");
      producer
          .metrics()
          .forEach(
              (name, metric) -> {
                if ("producer-metrics".equals(name.group())
                    && PRODUCER_KEY_METRICS.contains(name.name())) {
                  Object v = metric.metricValue(); // returns Double for built‑ins
                  logger.debug("metric={} value={} tags={}", name.name(), v, name.tags());
                }
              });
    }

    // close the producer
    logger.debug("Closing producer");
    closeProducer();

    // close the offset publisher socket
    if (offsetPublisherSocket != null) {
      logger.debug("Closing PUB socket");
      closeConnection(offsetPublisherSocket, "Error closing offset publisher");
    }

    logger.info("Closed connections");
  }
}
