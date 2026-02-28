/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageFamily;
import io.quasient.pal.messages.types.MessageType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads all {@link ExecMessage} entries from a WAL (Write-Ahead Log) and returns them as a list of
 * {@link WalEntry} instances. Supports both Chronicle Queue and Kafka backends.
 *
 * <p>This utility is the I/O bridge between the persisted WAL and the in-memory {@link WalIndex}.
 * For Chronicle queues, it reuses the mature {@link OutboundMsg#readNext(ExcerptTailer)}
 * deserialization pipeline already used by {@code pal print} and {@code ChronicleSourceLogReader}.
 * For Kafka topics, it reads raw bytes and extracts the message type from Kafka headers.
 *
 * <p>The Chronicle deserialization pipeline is:
 *
 * <ol>
 *   <li>{@link ChronicleQueue} &rarr; {@link ExcerptTailer}
 *   <li>{@link OutboundMsg#readNext(ExcerptTailer)} reads binary format (type byte + bodyLen int +
 *       body bytes)
 *   <li>{@link Message#unmarshal(byte[], int)} &rarr; {@link ExecMessage}
 *   <li>{@link WalEntry#fromExecMessage(long, ExecMessage)}
 * </ol>
 *
 * <p>The Kafka deserialization pipeline is:
 *
 * <ol>
 *   <li>{@link KafkaConsumer} with {@link ByteArrayDeserializer} for raw bytes
 *   <li>Extract {@code "message-type"} header &rarr; {@link MessageType#fromId(byte)}
 *   <li>Filter for {@link MessageFamily#EXEC} family
 *   <li>{@link Message#unmarshal(byte[], int)} &rarr; {@link ExecMessage}
 *   <li>{@link WalEntry#fromExecMessage(long, ExecMessage)}
 * </ol>
 *
 * <p>Only messages belonging to the {@link MessageFamily#EXEC} family are included; all other
 * message types (CONTROL, META, INTERCEPT) are filtered out.
 */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Utility class - private constructor throws to prevent instantiation")
public final class WalReader {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(WalReader.class);

  /** Private constructor to prevent instantiation of utility class. */
  private WalReader() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Reads all EXEC-family messages from a Chronicle queue and returns them as {@link WalEntry}
   * instances.
   *
   * <p>The queue is opened in read-only mode. An {@link ExcerptTailer} is created and positioned at
   * the start. Each message is read via {@link OutboundMsg#readNext(ExcerptTailer)}, filtered to
   * the {@link MessageFamily#EXEC} family, deserialized into an {@link ExecMessage}, and wrapped in
   * a {@link WalEntry} with the Chronicle queue index as its offset.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a list of {@link WalEntry} instances in offset order, or an empty list if the queue
   *     contains no EXEC messages
   */
  public static List<WalEntry> readChronicleWal(Path queuePath) {
    List<WalEntry> entries = new ArrayList<>();

    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      OutboundMsg outboundMsg;
      while (true) {
        long index = tailer.index();
        outboundMsg = OutboundMsg.readNext(tailer);
        if (outboundMsg == null) {
          break;
        }

        if (outboundMsg.getMessageType().getFamily() != MessageFamily.EXEC) {
          logger.debug(
              "Skipping non-EXEC message at index {}: {}", index, outboundMsg.getMessageType());
          continue;
        }

        Message message = new Message();
        message.unmarshal(outboundMsg.getBody(), 0);
        ExecMessage execMessage = message.getExecMessage();

        entries.add(WalEntry.fromExecMessage(index, execMessage));
      }
    }

    return entries;
  }

  /**
   * Reads all EXEC-family messages from a Chronicle queue and builds a {@link WalIndex}.
   *
   * <p>This is a convenience method that calls {@link #readChronicleWal(Path)} and then passes the
   * resulting entries to {@link WalIndex#build(List)}.
   *
   * @param queuePath the path to the Chronicle queue directory
   * @return a fully indexed {@link WalIndex} built from the Chronicle queue entries
   */
  public static WalIndex readAndIndexChronicleWal(Path queuePath) {
    return WalIndex.build(readChronicleWal(queuePath));
  }

  /**
   * Reads all EXEC-family messages from a Kafka topic and returns them as {@link WalEntry}
   * instances.
   *
   * <p>Creates a {@link KafkaConsumer} with {@link ByteArrayDeserializer} for values, assigns
   * partition 0, and reads from the beginning until the end offset is reached. For each record, the
   * {@code "message-type"} header is extracted to determine the {@link MessageType}. Only messages
   * in the {@link MessageFamily#EXEC} family are included.
   *
   * @param bootstrapServers the Kafka bootstrap servers (e.g., {@code "localhost:29092"})
   * @param topic the Kafka topic name
   * @return a list of {@link WalEntry} instances in offset order, or an empty list if the topic
   *     contains no EXEC messages
   */
  public static List<WalEntry> readKafkaWal(String bootstrapServers, String topic) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "pal-wal-reader-" + UUID.randomUUID());

    try (Consumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
      return readKafkaWal(consumer, topic);
    }
  }

  /**
   * Reads all EXEC-family messages from a Kafka topic using the provided consumer.
   *
   * <p>This package-private overload accepts an injected {@link Consumer} for testability. It
   * assigns partition 0, determines the end offset via {@link Consumer#endOffsets}, seeks to the
   * beginning, and polls until the consumer position reaches the end offset.
   *
   * <p>For each {@link ConsumerRecord}, the {@code "message-type"} Kafka header (a single byte) is
   * extracted and converted to a {@link MessageType} via {@link MessageType#fromId(byte)}. Records
   * whose message type does not belong to the {@link MessageFamily#EXEC} family are skipped.
   * Records with missing or malformed headers are also skipped with a warning.
   *
   * @param consumer the Kafka consumer to use for reading
   * @param topic the Kafka topic name
   * @return a list of {@link WalEntry} instances in offset order
   */
  static List<WalEntry> readKafkaWal(Consumer<String, byte[]> consumer, String topic) {
    TopicPartition partition0 = new TopicPartition(topic, 0);
    consumer.assign(Collections.singletonList(partition0));

    Map<TopicPartition, Long> endOffsets =
        consumer.endOffsets(Collections.singletonList(partition0));
    long endOffset = endOffsets.getOrDefault(partition0, 0L);

    if (endOffset == 0) {
      logger.debug("Kafka topic {} is empty, no messages to read", topic);
      return new ArrayList<>();
    }

    consumer.seekToBeginning(Collections.singletonList(partition0));

    List<WalEntry> entries = new ArrayList<>();

    while (consumer.position(partition0) < endOffset) {
      ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
      for (ConsumerRecord<String, byte[]> record : records) {
        if (record.offset() >= endOffset) {
          break;
        }

        MessageType messageType = getMessageTypeFromHeader(record);
        if (messageType == null) {
          logger.warn(
              "Missing or malformed message-type header at offset {}, skipping", record.offset());
          continue;
        }

        if (messageType.getFamily() != MessageFamily.EXEC) {
          logger.debug("Skipping non-EXEC message at offset {}: {}", record.offset(), messageType);
          continue;
        }

        Message message = new Message();
        message.unmarshal(record.value(), 0);
        ExecMessage execMessage = message.getExecMessage();

        entries.add(WalEntry.fromExecMessage(record.offset(), execMessage));
      }
    }

    return entries;
  }

  /**
   * Reads all EXEC-family messages from a Kafka topic and builds a {@link WalIndex}.
   *
   * <p>This is a convenience method that calls {@link #readKafkaWal(String, String)} and then
   * passes the resulting entries to {@link WalIndex#build(List)}.
   *
   * @param bootstrapServers the Kafka bootstrap servers (e.g., {@code "localhost:29092"})
   * @param topic the Kafka topic name
   * @return a fully indexed {@link WalIndex} built from the Kafka topic entries
   */
  public static WalIndex readAndIndexKafkaWal(String bootstrapServers, String topic) {
    return WalIndex.build(readKafkaWal(bootstrapServers, topic));
  }

  /**
   * Determines whether a log specification refers to a Chronicle Queue (local) log.
   *
   * <p>Chronicle logs are identified by the {@code "file:"} prefix in their specification string.
   * Plain topic names (without the prefix) are assumed to be Kafka topics.
   *
   * @param logSpec the log specification string (e.g., {@code "file:/tmp/my-wal"} or {@code
   *     "my-kafka-topic"})
   * @return {@code true} if the log spec starts with {@code "file:"}, {@code false} otherwise
   *     (including for {@code null})
   */
  public static boolean isChronicleLog(String logSpec) {
    return logSpec != null && logSpec.startsWith("file:");
  }

  /**
   * Extracts the {@link MessageType} from a Kafka record's {@code "message-type"} header.
   *
   * <p>The header value is a single byte representing the message type ID. This follows the same
   * pattern used by {@code KafkaLogMessageSerializer} for writing and {@code LogMessage} for
   * reading.
   *
   * @param record the Kafka consumer record
   * @return the {@link MessageType}, or {@code null} if the header is missing
   */
  private static MessageType getMessageTypeFromHeader(ConsumerRecord<String, byte[]> record) {
    for (Header header : record.headers().headers("message-type")) {
      int typeId = header.value()[0] & 0xFF;
      return MessageType.fromId((byte) typeId);
    }
    return null;
  }
}
