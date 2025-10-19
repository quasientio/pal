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

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.messages.LogMessageHeader;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import zmq.ZMQ;

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
public class KafkaWalWriter extends WalWriter {

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

  /** Constant byte[] for the MessageFormat byte */
  private static final byte[] FORMAT_BINARY = {MessageFormatType.BINARY.toByte()};

  /** Thread-local reusable byte buffer for writing the index during offset publishing */
  private static final ThreadLocal<byte[]> INDEX_BUF = ThreadLocal.withInitial(() -> new byte[8]);

  /** Reusable pre-built headers for message type */
  private static final Header[] MESSAGE_TYPE_HEADERS = new Header[256];

  static {
    for (int i = 0; i < 256; i++) {
      MESSAGE_TYPE_HEADERS[i] = new RecordHeader("message-type", new byte[] {(byte) i});
    }
  }

  /** Prebuilt immutable headers we always attach */
  private final List<Header> baseHeaders; // producer-id + message-format

  /** Factory that returns a {@link Producer} provided its properties. */
  private final ProducerFactory producerFactory;

  /**
   * Kafka producer used to send Log messages to a Kafka topic. Built lazily in {@link #writeToLog}
   * via the producer factory.
   */
  private Producer<String, byte[]> producer;

  /** Properties used to configure the Kafka producer. */
  private final Properties producerProperties = new Properties();

  /** Kafka topic corresponding to WAL */
  private String topic;

  /** Cached copy of peerUuid.toString() */
  private final String producerKeyStr;

  /** Per-thread Callback pool to avoid allocation of new Callback objects per each message */
  private final ThreadLocal<ArrayDeque<SendCb>> cbPool = ThreadLocal.withInitial(ArrayDeque::new);

  /**
   * Constructs a new KafkaWalWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this WAL writer service.
   * @param walQueue initialized {@link OutboundMsg} queue instance from which to consume.
   * @param walFailed global flag used to indicate failure when writing to Kafka so producers halt
   *     enqueuing.
   * @param flushOnClose flag used to indicate whether we should flush on close, waiting for queued
   *     and in-flight messages to be ack-ed, or if we shut down immediately.
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
      @Named("wal_queue") @Nullable HwmMessageQueue<OutboundMsg> walQueue,
      @Named("walFailed") AtomicBoolean walFailed,
      @Named("offset.pub") String offsetPubAddress,
      @Named("wal.flush_on_close") @Nullable String flushOnClose,
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
        offsetPubAddress,
        flushOnClose);

    this.producerFactory = producerFactory;
    this.producerKeyStr = peerUuid.toString();

    // create and store immutable headers
    this.baseHeaders =
        List.of(
            new LogMessageHeader("producer-id", UuidUtils.toBytes(peerUuid)),
            new RecordHeader("message-format", FORMAT_BINARY));

    // set Kafka's Producer properties
    setProducerProperties(lingerMs, batchSize, compressionType, bufferMemory);

    logger.debug(
        "new KafkaLogWriter initialized w/offsetPubAddress={}, flushOnClose={}",
        offsetPubAddress,
        flushOnClose);
  }

  /** Optionally opens ZeroMQ connection for the offset publisher. */
  @Override
  protected void openConnections() {

    // start offsets publisher
    if (publishOffsets) {
      // PUB socket is created and used ONLY by this publisher thread
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);

      // create and start disruptor thread
      offsetsDisruptor =
          new Disruptor<>(
              OffsetEvent::new,
              OFFSETS_RING_SIZE,
              r -> {
                Thread t = new Thread(threadGroup, r, serviceName + "-offset-publisher");
                t.setDaemon(true);
                return t;
              },
              ProducerType.MULTI,
              new BusySpinWaitStrategy());

      // single owner thread of the PUB socket here => thread-safe
      offsetsDisruptor.handleEventsWith(
          (e, seq, end) -> {
            // two frames: index (as 8 bytes), msgId (String)
            byte[] b = INDEX_BUF.get();
            putLongBE(b, e.index);
            offsetPublisherSocket.sendMore(b);
            offsetPublisherSocket.send(e.msgId.getBytes(ZMQ.CHARSET));
            e.clear();
          });

      offsetsDisruptor.start();
      offsetsRing = offsetsDisruptor.getRingBuffer();
    }
    logger.info("connections open - except kafka producer");
  }

  /**
   * Configures the Kafka producer with the designated Log information and offset publishing
   * preference. This method is intended to be called just once before starting the service.
   *
   * @param writeAheadLog log information containing details such as the Log name and bootstrap
   *     servers.
   * @param publishOffsets flag indicating whether message offsets should be published via ZeroMQ.
   * @throws IllegalStateException if the method is called more than once
   */
  @Override
  public void writeToLog(LogInfo writeAheadLog, boolean publishOffsets) {

    if (this.writeAheadLog != null) {
      throw new IllegalStateException(
          "writeAheadLog already set. This method can only be called once!");
    }

    this.writeAheadLog = writeAheadLog;
    this.topic = writeAheadLog.getName();
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
   * Continuously receives messages from the WAL Queue and writes them to Kafka using the configured
   * producer. The method processes messages until shutdown is requested / the thread is
   * interrupted, and may or not flush the queue before shutdown depending on the value of {@link
   * #isFlushOnClose}.
   */
  @Override
  public void run() {

    if (!queueless) {
      walQueue.drain(
          this::writeMessage,
          ADAPTIVE_100_MICROSECONDS,
          () -> !(shutdownRequested || Thread.currentThread().isInterrupted()));

      logger.debug("Thread interrupted or shutdown requested.");

      if (!isFlushOnClose) {
        logger.debug("Shutting down immediately...");
        return;
      }

      // after shutdown request, drain queue until empty
      logger.debug("Processing messages remaining in queue...");
      OutboundMsg msg;
      while ((msg = walQueue.poll()) != null) {
        writeMessage(msg);
      }

      logger.debug("Wal queue empty, shutting down...");
      return;
    }

    // ───────────── Direct-write mode: don't drain, just wait for shutdown ─────────────
    logger.info("Direct-write mode enabled: queue draining is disabled; waiting for shutdown...");
    synchronized (shutdownMonitor) {
      while (!(shutdownRequested || Thread.currentThread().isInterrupted())) {
        try {
          shutdownMonitor.wait(0L); // wait indefinitely; we'll be notified on stop()
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    logger.debug("Thread interrupted or shutdown requested.");
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
        lingerMs != null && !lingerMs.isBlank() ? Long.parseLong(lingerMs) : DEF_LINGER_MS);

    producerProperties.put(
        ProducerConfig.BATCH_SIZE_CONFIG,
        batchSize != null && !batchSize.isBlank() ? Integer.parseInt(batchSize) : DEF_BATCH_SIZE);

    producerProperties.put(
        ProducerConfig.COMPRESSION_TYPE_CONFIG,
        compressionType != null && !compressionType.isBlank()
            ? compressionType
            : DEF_COMPRESSION_TYPE);

    producerProperties.put(
        ProducerConfig.BUFFER_MEMORY_CONFIG,
        bufferMemory != null && !bufferMemory.isBlank()
            ? Long.parseLong(bufferMemory)
            : DEF_BUFFER_MEMORY);
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
   *       &nbsp;&nbsp;• Delegates to {@link #sendToKafka(MessageType, byte[], String)}.<br>
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
  @Override
  public void writeMessage(OutboundMsg msg) {
    if (POISON_PILL.equals(msg)) { // graceful exit branch
      return;
    }

    messagesReceived.getAndIncrement();

    try {
      // normal path
      sendToKafka(msg.getMessageType(), msg.getBody(), msg.getMessageId());

    } catch (Exception ex) { // fatal path

      messagesDroppedError.getAndIncrement();
      logger.error("Kafka failed sending message w/id {} → halting WAL", msg.getMessageId(), ex);

      if (walFailed.compareAndSet(false, true)) { // publish failure
        if (!queueless) {
          walQueue.clear(); // free memory

          if (pillSent.compareAndSet(false, true)) {
            while (!walQueue.offer(POISON_PILL)) {
              Thread.onSpinWait();
            }
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
   * @param messageType the type identifier for the message.
   * @param message the byte array payload of the message.
   * @param messageId unique identifier for the message.
   */
  private void sendToKafka(MessageType messageType, byte[] message, String messageId) {

    // Build record with immutable base headers; Kafka will copy them into a RecordHeaders
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(topic, 0, producerKeyStr, message, baseHeaders);

    // Add the changing header using a cached one-byte array
    int typeId = messageType.getId() & 0xFF;
    newRecord.headers().add(MESSAGE_TYPE_HEADERS[typeId]);

    // Borrow a pooled callback (carries mid & size)
    SendCb cb = borrowCb(messageId);

    messagesInFlight.incrementAndGet();
    @SuppressWarnings("unused")
    var unused = producer.send(newRecord, cb);
  }

  /** Helper for writing a long to a reusable byte buffer, for offset publishing */
  private static void putLongBE(byte[] a, long v) {
    a[0] = (byte) (v >>> 56);
    a[1] = (byte) (v >>> 48);
    a[2] = (byte) (v >>> 40);
    a[3] = (byte) (v >>> 32);
    a[4] = (byte) (v >>> 24);
    a[5] = (byte) (v >>> 16);
    a[6] = (byte) (v >>> 8);
    a[7] = (byte) v;
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

    if (isFlushOnClose) {
      // make producer flush all outstanding sends (blocks until callbacks run)
      try {
        if (producer != null) {
          logger.debug("Flushing producer...");
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
    }

    if (publishOffsets && offsetsDisruptor != null) {
      offsetsDisruptor.shutdown(); // waits for consumer to drain
    }

    if (walQueue != null && !walQueue.isEmpty()) {
      logger.warn("{} enqueued messages remained after shut down", walQueue.size());
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

  /** Gets a reusable callback from the pool */
  private SendCb borrowCb(String mid) {
    var q = cbPool.get();
    var cb = q.pollFirst();
    if (cb == null) {
      cb = new SendCb();
    }
    cb.mid = mid;
    return cb;
  }

  /** Resets a callback buffer for future reuse */
  private void recycleCb(SendCb cb) {
    cb.mid = null;
    cbPool.get().offerFirst(cb);
  }

  /** Implements a Kafka producer callback which publishes the returned offset */
  private final class SendCb implements Callback {

    /** Message ID field */
    String mid;

    /** {@inheritDoc} */
    @Override
    public void onCompletion(RecordMetadata md, Exception ex) {
      try {
        if (ex != null) {
          messagesDroppedError.incrementAndGet();
          logger.error("Error sending message to log", ex);
          return;
        }
        messagesWritten.incrementAndGet();
        if (logger.isTraceEnabled()) {
          logger.trace("New message at offset {} (id={})", md.offset(), mid);
        }

        if (publishOffsets && offsetsRing != null) {
          // Never drop: block here until a slot is available
          long seq = offsetsRing.next();
          try {
            OffsetEvent e = offsetsRing.get(seq);
            e.msgId = mid;
            e.index = md.offset();
          } finally {
            offsetsRing.publish(seq);
          }
        }
      } finally {
        messagesInFlight.decrementAndGet();
        recycleCb(this);
      }
    }
  }
}
