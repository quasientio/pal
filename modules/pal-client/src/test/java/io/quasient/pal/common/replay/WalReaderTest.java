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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link WalReader} — the utility that reads all {@code ExecMessage} entries from a
 * WAL (Chronicle Queue or Kafka topic) and returns a {@code List<WalEntry>}.
 *
 * <p>Chronicle tests use real Chronicle queues in temporary directories to validate the full
 * deserialization pipeline: Chronicle queue &rarr; {@link OutboundMsg#readNext} &rarr; {@link
 * Message#unmarshal(byte[], int)} &rarr; {@link ExecMessage} &rarr; {@link
 * WalEntry#fromExecMessage(long, ExecMessage)}.
 *
 * <p>Kafka tests use a mock {@link Consumer} with pre-built {@link ConsumerRecord} instances to
 * validate the Kafka deserialization pipeline without requiring a running Kafka broker.
 *
 * <p>Test infrastructure follows the pattern from {@code ChronicleLogUtilTest}: create a Chronicle
 * queue with {@link SingleChronicleQueueBuilder}, write {@link OutboundMsg} instances via {@link
 * ExcerptAppender}, then read back with {@link WalReader#readChronicleWal(Path)}.
 */
public class WalReaderTest {

  /** Temporary directory for test queues. */
  private Path tempDir;

  /** Sets up a temporary directory for each test. */
  @Before
  public void setUp() throws IOException {
    tempDir = Files.createTempDirectory("wal-reader-test-");
  }

  /** Cleans up the temporary directory after each test. */
  @After
  public void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> paths = Files.walk(tempDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  /**
   * Verifies that reading an empty Chronicle queue returns an empty list.
   *
   * <p>This is the base case for the reader — a valid queue with zero appended messages should
   * produce zero {@code WalEntry} instances.
   */
  @Test
  public void readsEmptyQueue() {
    // Given: A Chronicle queue with zero messages
    Path queuePath = tempDir.resolve("empty-queue");
    createEmptyQueue(queuePath);

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Returns an empty list
    assertThat("Empty queue should produce empty list", entries.size(), is(0));
  }

  /**
   * Verifies that non-EXEC messages (e.g., CONTROL) are filtered out and only EXEC messages are
   * returned as {@code WalEntry} instances.
   *
   * <p>The queue contains 2 EXEC messages and 1 CONTROL message. The reader should return a list of
   * size 2, having filtered out the CONTROL message.
   */
  @Test
  public void readsExecMessagesOnly() {
    // Given: A Chronicle queue with 2 EXEC messages and 1 CONTROL message
    Path queuePath = tempDir.resolve("mixed-messages");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      // Write EXEC instance method message
      appendExecInstanceMethod(appender, "self-caller", 1, "com.example.Foo", "bar");

      // Write CONTROL message
      appendControlMessage(appender);

      // Write EXEC return value message
      appendExecReturnValue(appender, "self-caller", 2);
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Returns a list of size 2 (CONTROL message filtered out)
    assertThat("Should filter out non-EXEC messages", entries.size(), is(2));
  }

  /**
   * Verifies that multiple EXEC messages (mix of OPERATION and COMPLETION types) are all read
   * correctly and returned in offset order.
   *
   * <p>The queue contains 5 EXEC messages — a combination of operation types (e.g.,
   * EXEC_INSTANCE_METHOD, EXEC_CONSTRUCTOR) and completion types (e.g., EXEC_RETURN_VALUE). All 5
   * should appear in the result list, in the same order they were appended.
   */
  @Test
  public void readsMultipleExecMessages() {
    // Given: A Chronicle queue with 5 EXEC messages (mix of OPERATION and COMPLETION types)
    Path queuePath = tempDir.resolve("multiple-messages");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      appendExecConstructor(appender, "self-caller", 1, "com.example.Widget");
      appendExecReturnValue(appender, "self-caller", 2);
      appendExecInstanceMethod(appender, "self-caller", 3, "com.example.Widget", "process");
      appendExecInstanceMethod(appender, "self-caller", 4, "com.example.Widget", "compute");
      appendExecReturnValue(appender, "self-caller", 5);
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Returns a list of size 5, entries in offset order
    assertThat("Should read all 5 EXEC messages", entries.size(), is(5));
    assertThat(entries.get(0).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(1).getKind(), is(WalEntryKind.COMPLETION));
    assertThat(entries.get(2).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(3).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(4).getKind(), is(WalEntryKind.COMPLETION));
  }

  /**
   * Verifies that the offsets of returned {@code WalEntry} instances are monotonically increasing.
   *
   * <p>Chronicle queue assigns increasing indices to appended messages. The reader must preserve
   * this ordering so that downstream consumers (e.g., {@code WalIndex} pairing) see entries in
   * correct sequence.
   */
  @Test
  public void preservesOffsetOrder() {
    // Given: A Chronicle queue with known messages appended in sequence
    Path queuePath = tempDir.resolve("offset-order");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      for (int i = 0; i < 5; i++) {
        appendExecInstanceMethod(appender, "self-caller", i, "com.example.Test", "method" + i);
      }
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: WalEntry offsets are monotonically increasing (each offset > previous offset)
    assertThat("Should have 5 entries", entries.size(), is(5));
    for (int i = 1; i < entries.size(); i++) {
      assertTrue(
          "Offset at index "
              + i
              + " ("
              + entries.get(i).getOffset()
              + ") should be > offset at index "
              + (i - 1)
              + " ("
              + entries.get(i - 1).getOffset()
              + ")",
          entries.get(i).getOffset() > entries.get(i - 1).getOffset());
    }
  }

  /**
   * Verifies that the fields extracted into {@code WalEntry} match the values written to the queue.
   *
   * <p>A single EXEC_INSTANCE_METHOD message is written with known threadName, builderSeq,
   * className, and methodName. The returned {@code WalEntry} must have matching field values,
   * confirming the full deserialization pipeline preserves data fidelity.
   */
  @Test
  public void extractsCorrectFields() {
    // Given: A Chronicle queue with one EXEC_INSTANCE_METHOD message with known fields
    Path queuePath = tempDir.resolve("field-extraction");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      appendExecInstanceMethod(appender, "self-caller", 42, "com.example.Calculator", "add");
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Returned WalEntry has matching threadName, builderSeq, className, executableName
    assertThat("Should have exactly 1 entry", entries.size(), is(1));
    WalEntry entry = entries.get(0);
    assertThat(entry.getThreadName(), is("self-caller"));
    assertThat(entry.getBuilderSeq(), is(42));
    assertThat(entry.getClassName(), is("com.example.Calculator"));
    assertThat(entry.getExecutableName(), is("add"));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_INSTANCE_METHOD));
    assertThat(entry.getKind(), is(WalEntryKind.OPERATION));
  }

  /**
   * Verifies that the bootstrap {@code main()} completion at the end of the WAL is filtered out
   * when no matching {@code main()} operation exists.
   *
   * <p>SelfBootstrapInvoker invokes {@code main()} directly (outside AspectJ weaving), so the
   * operation is never written to the WAL. The completion IS written, leaving an orphaned trailing
   * entry. {@link WalReader} must filter this known artifact.
   */
  @Test
  public void filtersBootstrapMainCompletion() {
    // Given: A Chronicle queue with an OPERATION + COMPLETION pair,
    //        followed by an orphaned main() COMPLETION (bootstrap artifact)
    Path queuePath = tempDir.resolve("bootstrap-filter");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      appendExecInstanceMethod(appender, "self-caller", 1, "com.example.Foo", "bar");
      appendExecReturnValue(appender, "self-caller", 2);
      appendExecReturnValueFromMain(appender, "self-caller", 3);
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Bootstrap main() completion is filtered out, only 2 entries remain
    assertThat("Bootstrap main() completion should be filtered", entries.size(), is(2));
    assertThat(entries.get(0).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(1).getKind(), is(WalEntryKind.COMPLETION));
  }

  /**
   * Verifies that a {@code main()} completion is NOT filtered when a matching {@code main()}
   * operation exists in the WAL (i.e., main() was invoked via AspectJ dispatch, not via
   * SelfBootstrapInvoker).
   */
  @Test
  public void preservesMainCompletionWhenMatchingOperationExists() {
    // Given: A Chronicle queue with a main() OPERATION and main() COMPLETION (not orphaned)
    Path queuePath = tempDir.resolve("main-with-op");
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      appendExecInstanceMethod(appender, "self-caller", 1, "com.example.App", "main");
      appendExecReturnValueFromMain(appender, "self-caller", 2);
    }

    // When: WalReader.readChronicleWal(path) is called
    List<WalEntry> entries = WalReader.readChronicleWal(queuePath);

    // Then: Both entries preserved (main() completion is NOT filtered because operation exists)
    assertThat("main() completion should be kept when operation exists", entries.size(), is(2));
  }

  // ============================================================================
  // Kafka WAL reading tests
  // ============================================================================

  /**
   * Verifies that reading an empty Kafka topic returns an empty list.
   *
   * <p>This mirrors {@link #readsEmptyQueue()} but for the Kafka reading path, using a mock {@code
   * Consumer} that returns empty records on poll with an end offset of 0.
   */
  @Test
  public void readsEmptyKafkaTopic() {
    // Given: A mock Consumer that returns empty records on poll, with end offset 0
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);
    Consumer<String, byte[]> consumer = mockConsumer(partition0, 0L, Collections.emptyList());

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: Returns empty list
    assertThat("Empty topic should produce empty list", entries.size(), is(0));
  }

  /**
   * Verifies that non-EXEC messages are filtered out from Kafka records.
   *
   * <p>This mirrors {@link #readsExecMessagesOnly()} but for the Kafka reading path. A mock {@code
   * Consumer} returns 3 records: 2 with EXEC family types (EXEC_INSTANCE_METHOD, EXEC_RETURN_VALUE)
   * and 1 with CONTROL family type. Only the 2 EXEC messages should be returned.
   */
  @Test
  public void readsExecMessagesOnlyFromKafka() {
    // Given: A mock Consumer returning 3 records: 2 EXEC family and 1 CONTROL family
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);

    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    records.add(
        createKafkaExecInstanceMethodRecord(topic, 0, "self-caller", 1, "com.example.Foo", "bar"));
    records.add(createKafkaControlRecord(topic, 1));
    records.add(createKafkaExecReturnValueRecord(topic, 2, "self-caller", 2));

    Consumer<String, byte[]> consumer = mockConsumer(partition0, 3L, records);

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: Returns list of size 2, only EXEC messages
    assertThat("Should filter out non-EXEC messages", entries.size(), is(2));
  }

  /**
   * Verifies that multiple EXEC messages from Kafka are read in correct order.
   *
   * <p>This mirrors {@link #readsMultipleExecMessages()} but for the Kafka reading path. A mock
   * {@code Consumer} returns 5 EXEC records (mix of OPERATION and COMPLETION types). All 5 should
   * appear in the result list in the same order.
   */
  @Test
  public void readsMultipleExecMessagesFromKafka() {
    // Given: A mock Consumer returning 5 EXEC records (mix of OPERATION and COMPLETION types)
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);

    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    records.add(createKafkaExecConstructorRecord(topic, 0, "self-caller", 1, "com.example.Widget"));
    records.add(createKafkaExecReturnValueRecord(topic, 1, "self-caller", 2));
    records.add(
        createKafkaExecInstanceMethodRecord(
            topic, 2, "self-caller", 3, "com.example.Widget", "process"));
    records.add(
        createKafkaExecInstanceMethodRecord(
            topic, 3, "self-caller", 4, "com.example.Widget", "compute"));
    records.add(createKafkaExecReturnValueRecord(topic, 4, "self-caller", 5));

    Consumer<String, byte[]> consumer = mockConsumer(partition0, 5L, records);

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: Returns list of size 5 in correct order
    assertThat("Should read all 5 EXEC messages", entries.size(), is(5));
    assertThat(entries.get(0).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(1).getKind(), is(WalEntryKind.COMPLETION));
    assertThat(entries.get(2).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(3).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(4).getKind(), is(WalEntryKind.COMPLETION));
  }

  /**
   * Verifies that Kafka record offsets are correctly mapped to {@link WalEntry} offsets.
   *
   * <p>This mirrors {@link #preservesOffsetOrder()} but for the Kafka reading path. Kafka record
   * offsets (0, 1, 2, 3) should be preserved as the corresponding {@code WalEntry} offsets.
   */
  @Test
  public void preservesKafkaOffsetOrder() {
    // Given: A mock Consumer returning records with Kafka offsets 0, 1, 2, 3
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);

    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      records.add(
          createKafkaExecInstanceMethodRecord(
              topic, i, "self-caller", i, "com.example.Test", "method" + i));
    }

    Consumer<String, byte[]> consumer = mockConsumer(partition0, 4L, records);

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: WalEntry offsets match Kafka record offsets (0, 1, 2, 3)
    assertThat("Should have 4 entries", entries.size(), is(4));
    for (int i = 0; i < 4; i++) {
      assertThat(
          "Offset at index " + i + " should match Kafka offset",
          entries.get(i).getOffset(),
          is((long) i));
    }
  }

  /**
   * Verifies that fields are correctly deserialized from a Kafka record into a {@link WalEntry}.
   *
   * <p>This mirrors {@link #extractsCorrectFields()} but for the Kafka reading path. A single
   * EXEC_INSTANCE_METHOD record with known className, methodName, threadName, and builderSeq is
   * consumed. The resulting {@code WalEntry} must have matching field values.
   */
  @Test
  public void extractsCorrectFieldsFromKafka() {
    // Given: A mock Consumer returning a single EXEC_INSTANCE_METHOD record with known fields
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);

    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    records.add(
        createKafkaExecInstanceMethodRecord(
            topic, 0, "self-caller", 42, "com.example.Calculator", "add"));

    Consumer<String, byte[]> consumer = mockConsumer(partition0, 1L, records);

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: WalEntry has correct fields
    assertThat("Should have exactly 1 entry", entries.size(), is(1));
    WalEntry entry = entries.get(0);
    assertThat(entry.getThreadName(), is("self-caller"));
    assertThat(entry.getBuilderSeq(), is(42));
    assertThat(entry.getClassName(), is("com.example.Calculator"));
    assertThat(entry.getExecutableName(), is("add"));
    assertThat(entry.getMessageType(), is(MessageType.EXEC_INSTANCE_METHOD));
    assertThat(entry.getKind(), is(WalEntryKind.OPERATION));
  }

  /**
   * Verifies that the orphaned bootstrap {@code main()} completion is filtered from Kafka WAL
   * entries.
   *
   * <p>This mirrors {@link #filtersBootstrapMainCompletion()} but for the Kafka reading path. A
   * mock {@code Consumer} returns records where the last entry is an orphaned main() COMPLETION
   * (EXEC_RETURN_VALUE with executableName="main" and no matching OPERATION). The orphaned
   * completion should be removed from the results.
   */
  @Test
  public void filtersBootstrapMainCompletionFromKafka() {
    // Given: A mock Consumer returning records where last entry is an orphaned main() COMPLETION
    String topic = "test-topic";
    TopicPartition partition0 = new TopicPartition(topic, 0);

    List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
    records.add(
        createKafkaExecInstanceMethodRecord(topic, 0, "self-caller", 1, "com.example.Foo", "bar"));
    records.add(createKafkaExecReturnValueRecord(topic, 1, "self-caller", 2));
    records.add(createKafkaExecReturnValueFromMainRecord(topic, 2, "self-caller", 3));

    Consumer<String, byte[]> consumer = mockConsumer(partition0, 3L, records);

    // When: readKafkaWal(consumer, topic) is called
    List<WalEntry> entries = WalReader.readKafkaWal(consumer, topic);

    // Then: Bootstrap main() completion is filtered out, only 2 entries remain
    assertThat("Bootstrap main() completion should be filtered", entries.size(), is(2));
    assertThat(entries.get(0).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entries.get(1).getKind(), is(WalEntryKind.COMPLETION));
  }

  // ============================================================================
  // isChronicleLog tests
  // ============================================================================

  /**
   * Verifies that {@code isChronicleLog} returns {@code true} for a log spec with the {@code file:}
   * prefix.
   */
  @Test
  public void isChronicleLogDetectsFilePrefix() {
    assertTrue(
        "file: prefix should be detected as Chronicle log",
        WalReader.isChronicleLog("file:/tmp/my-wal"));
  }

  /** Verifies that {@code isChronicleLog} returns {@code false} for a plain Kafka topic name. */
  @Test
  public void isChronicleLogRejectsPlainTopicName() {
    assertFalse(
        "Plain topic name should not be detected as Chronicle log",
        WalReader.isChronicleLog("my-kafka-topic"));
  }

  /** Verifies that {@code isChronicleLog} returns {@code false} for a {@code null} log spec. */
  @Test
  public void isChronicleLogHandlesNull() {
    assertFalse("null should not be detected as Chronicle log", WalReader.isChronicleLog(null));
  }

  // ============================================================================
  // Helper methods for creating test messages
  // ============================================================================

  /**
   * Creates an empty Chronicle queue at the specified path.
   *
   * @param queuePath the path where the Chronicle queue should be created
   */
  private void createEmptyQueue(Path queuePath) {
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      queue.createAppender();
    }
  }

  /**
   * Appends an EXEC_INSTANCE_METHOD message to the queue.
   *
   * @param appender the queue appender
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param methodName the method name
   */
  private void appendExecInstanceMethod(
      ExcerptAppender appender,
      String threadName,
      int builderSeq,
      String className,
      String methodName) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName(methodName);
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    execMsg.setInstanceMethodCall(imc);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_INSTANCE_METHOD.getId())
            .withExecMessage(execMsg);
    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.EXEC_INSTANCE_METHOD,
            ExecPhase.BEFORE,
            null,
            execMsg.getMessageId(),
            null,
            wrapper);
    outboundMsg.appendTo(appender);
  }

  /**
   * Appends an EXEC_CONSTRUCTOR message to the queue.
   *
   * @param appender the queue appender
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   */
  private void appendExecConstructor(
      ExcerptAppender appender, String threadName, int builderSeq, String className) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ConstructorCall cc = new ConstructorCall();
    Class clazz = new Class();
    clazz.setName(className);
    cc.setClazz(clazz);
    execMsg.setConstructorCall(cc);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_CONSTRUCTOR.getId())
            .withExecMessage(execMsg);
    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            null,
            execMsg.getMessageId(),
            null,
            wrapper);
    outboundMsg.appendTo(appender);
  }

  /**
   * Appends an EXEC_RETURN_VALUE message to the queue.
   *
   * @param appender the queue appender
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   */
  private void appendExecReturnValue(ExcerptAppender appender, String threadName, int builderSeq) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    execMsg.setReturnValue(rv);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_RETURN_VALUE.getId())
            .withExecMessage(execMsg);
    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.EXEC_RETURN_VALUE,
            ExecPhase.AFTER,
            null,
            execMsg.getMessageId(),
            null,
            wrapper);
    outboundMsg.appendTo(appender);
  }

  /**
   * Appends an EXEC_RETURN_VALUE message for {@code main()} to the queue (bootstrap artifact).
   *
   * @param appender the queue appender
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   */
  private void appendExecReturnValueFromMain(
      ExcerptAppender appender, String threadName, int builderSeq) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    Method mainMethod = new Method();
    mainMethod.setName("main");
    Reflectable from = new Reflectable();
    from.setMethod(mainMethod);
    rv.setFrom(from);
    execMsg.setReturnValue(rv);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_RETURN_VALUE.getId())
            .withExecMessage(execMsg);
    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.EXEC_RETURN_VALUE,
            ExecPhase.AFTER,
            null,
            execMsg.getMessageId(),
            null,
            wrapper);
    outboundMsg.appendTo(appender);
  }

  /**
   * Appends a CONTROL_MESSAGE_REQUEST message to the queue.
   *
   * @param appender the queue appender
   */
  private void appendControlMessage(ExcerptAppender appender) {
    ControlMessage ctrl = new ControlMessage();
    ctrl.setFromPeer(UUID.randomUUID().toString());
    ctrl.setMessageId(UUID.randomUUID().toString());
    ctrl.setBody("test");

    Message wrapper =
        new Message()
            .withMessageType(MessageType.CONTROL_MESSAGE_REQUEST.getId())
            .withControlMessage(ctrl);
    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.CONTROL_MESSAGE_REQUEST,
            ExecPhase.UNDEFINED,
            null,
            ctrl.getMessageId(),
            null,
            wrapper);
    outboundMsg.appendTo(appender);
  }

  // ============================================================================
  // Kafka test helper methods
  // ============================================================================

  /**
   * Creates a mock {@link Consumer} that returns the given records on the first poll and simulates
   * position tracking.
   *
   * @param partition the topic partition to assign
   * @param endOffset the end offset to report
   * @param records the records to return on poll
   * @return a mock consumer configured for a single poll cycle
   */
  @SuppressWarnings("unchecked")
  private Consumer<String, byte[]> mockConsumer(
      TopicPartition partition, long endOffset, List<ConsumerRecord<String, byte[]>> records) {
    Consumer<String, byte[]> consumer = Mockito.mock(Consumer.class);

    // endOffsets returns the end offset for the partition
    Map<TopicPartition, Long> endOffsets = new HashMap<>();
    endOffsets.put(partition, endOffset);
    Mockito.when(consumer.endOffsets(Collections.singletonList(partition))).thenReturn(endOffsets);

    // Build ConsumerRecords from the list
    Map<TopicPartition, List<ConsumerRecord<String, byte[]>>> recordMap = new HashMap<>();
    recordMap.put(partition, records);
    ConsumerRecords<String, byte[]> consumerRecords = new ConsumerRecords<>(recordMap);

    ConsumerRecords<String, byte[]> emptyRecords = new ConsumerRecords<>(Collections.emptyMap());

    // First poll returns all records, subsequent polls return empty
    Mockito.when(consumer.poll(Mockito.any(Duration.class)))
        .thenReturn(consumerRecords)
        .thenReturn(emptyRecords);

    // Position: returns 0 before poll, then endOffset after poll
    Mockito.when(consumer.position(partition)).thenReturn(0L).thenReturn(endOffset);

    return consumer;
  }

  /**
   * Serializes a Colfer {@link Message} to a byte array.
   *
   * @param message the message to serialize
   * @return the serialized bytes
   */
  private byte[] serializeMessage(Message message) {
    byte[] buf = new byte[message.marshalFit()];
    int end = message.marshal(buf, 0);
    byte[] result = new byte[end];
    System.arraycopy(buf, 0, result, 0, end);
    return result;
  }

  /**
   * Creates a Kafka {@link ConsumerRecord} for an EXEC_INSTANCE_METHOD message.
   *
   * @param topic the topic name
   * @param offset the record offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param methodName the method name
   * @return a consumer record with the serialized message and message-type header
   */
  private ConsumerRecord<String, byte[]> createKafkaExecInstanceMethodRecord(
      String topic,
      long offset,
      String threadName,
      int builderSeq,
      String className,
      String methodName) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName(methodName);
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    execMsg.setInstanceMethodCall(imc);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_INSTANCE_METHOD.getId())
            .withExecMessage(execMsg);

    RecordHeaders headers = new RecordHeaders();
    headers.add("message-type", new byte[] {MessageType.EXEC_INSTANCE_METHOD.getId()});

    return new ConsumerRecord<>(
        topic,
        0,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        -1,
        -1,
        null,
        serializeMessage(wrapper),
        headers,
        java.util.Optional.empty());
  }

  /**
   * Creates a Kafka {@link ConsumerRecord} for an EXEC_CONSTRUCTOR message.
   *
   * @param topic the topic name
   * @param offset the record offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @return a consumer record with the serialized message and message-type header
   */
  private ConsumerRecord<String, byte[]> createKafkaExecConstructorRecord(
      String topic, long offset, String threadName, int builderSeq, String className) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ConstructorCall cc = new ConstructorCall();
    Class clazz = new Class();
    clazz.setName(className);
    cc.setClazz(clazz);
    execMsg.setConstructorCall(cc);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_CONSTRUCTOR.getId())
            .withExecMessage(execMsg);

    RecordHeaders headers = new RecordHeaders();
    headers.add("message-type", new byte[] {MessageType.EXEC_CONSTRUCTOR.getId()});

    return new ConsumerRecord<>(
        topic,
        0,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        -1,
        -1,
        null,
        serializeMessage(wrapper),
        headers,
        java.util.Optional.empty());
  }

  /**
   * Creates a Kafka {@link ConsumerRecord} for an EXEC_RETURN_VALUE message.
   *
   * @param topic the topic name
   * @param offset the record offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a consumer record with the serialized message and message-type header
   */
  private ConsumerRecord<String, byte[]> createKafkaExecReturnValueRecord(
      String topic, long offset, String threadName, int builderSeq) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    execMsg.setReturnValue(rv);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_RETURN_VALUE.getId())
            .withExecMessage(execMsg);

    RecordHeaders headers = new RecordHeaders();
    headers.add("message-type", new byte[] {MessageType.EXEC_RETURN_VALUE.getId()});

    return new ConsumerRecord<>(
        topic,
        0,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        -1,
        -1,
        null,
        serializeMessage(wrapper),
        headers,
        java.util.Optional.empty());
  }

  /**
   * Creates a Kafka {@link ConsumerRecord} for an EXEC_RETURN_VALUE message from {@code main()}
   * (bootstrap artifact).
   *
   * @param topic the topic name
   * @param offset the record offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a consumer record with the serialized message and message-type header
   */
  private ConsumerRecord<String, byte[]> createKafkaExecReturnValueFromMainRecord(
      String topic, long offset, String threadName, int builderSeq) {
    ExecMessage execMsg = new ExecMessage();
    execMsg.setThreadName(threadName);
    execMsg.setBuilderSeq(builderSeq);
    execMsg.setPeerUuid(UUID.randomUUID().toString());
    execMsg.setMessageId(UUID.randomUUID().toString());
    execMsg.setCurrentTime(String.valueOf(System.currentTimeMillis()));

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    Method mainMethod = new Method();
    mainMethod.setName("main");
    Reflectable from = new Reflectable();
    from.setMethod(mainMethod);
    rv.setFrom(from);
    execMsg.setReturnValue(rv);

    Message wrapper =
        new Message()
            .withMessageType(MessageType.EXEC_RETURN_VALUE.getId())
            .withExecMessage(execMsg);

    RecordHeaders headers = new RecordHeaders();
    headers.add("message-type", new byte[] {MessageType.EXEC_RETURN_VALUE.getId()});

    return new ConsumerRecord<>(
        topic,
        0,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        -1,
        -1,
        null,
        serializeMessage(wrapper),
        headers,
        java.util.Optional.empty());
  }

  /**
   * Creates a Kafka {@link ConsumerRecord} for a CONTROL_MESSAGE_REQUEST message.
   *
   * @param topic the topic name
   * @param offset the record offset
   * @return a consumer record with the serialized message and message-type header
   */
  private ConsumerRecord<String, byte[]> createKafkaControlRecord(String topic, long offset) {
    ControlMessage ctrl = new ControlMessage();
    ctrl.setFromPeer(UUID.randomUUID().toString());
    ctrl.setMessageId(UUID.randomUUID().toString());
    ctrl.setBody("test");

    Message wrapper =
        new Message()
            .withMessageType(MessageType.CONTROL_MESSAGE_REQUEST.getId())
            .withControlMessage(ctrl);

    RecordHeaders headers = new RecordHeaders();
    headers.add("message-type", new byte[] {MessageType.CONTROL_MESSAGE_REQUEST.getId()});

    return new ConsumerRecord<>(
        topic,
        0,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        -1,
        -1,
        null,
        serializeMessage(wrapper),
        headers,
        java.util.Optional.empty());
  }
}
