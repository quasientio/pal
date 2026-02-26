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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link WalReader} — the utility that reads all {@code ExecMessage} entries from a
 * Chronicle queue and returns a {@code List<WalEntry>}.
 *
 * <p>Tests use real Chronicle queues in temporary directories to validate the full deserialization
 * pipeline: Chronicle queue &rarr; {@link OutboundMsg#readNext} &rarr; {@link
 * Message#unmarshal(byte[], int)} &rarr; {@link ExecMessage} &rarr; {@link
 * WalEntry#fromExecMessage(long, ExecMessage)}.
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
  // Kafka WAL reading tests (awaiting implementation in #846)
  // ============================================================================

  /**
   * Verifies that reading an empty Kafka topic returns an empty list.
   *
   * <p>This mirrors {@link #readsEmptyQueue()} but for the Kafka reading path, using a mock {@code
   * Consumer} that returns empty records on poll with an end offset of 0.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void readsEmptyKafkaTopic() {
    // Given: A mock Consumer that returns empty records on poll, with end offset 0
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: Returns empty list

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that non-EXEC messages are filtered out from Kafka records.
   *
   * <p>This mirrors {@link #readsExecMessagesOnly()} but for the Kafka reading path. A mock {@code
   * Consumer} returns 3 records: 2 with EXEC family types (EXEC_INSTANCE_METHOD, EXEC_RETURN_VALUE)
   * and 1 with CONTROL family type. Only the 2 EXEC messages should be returned.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void readsExecMessagesOnlyFromKafka() {
    // Given: A mock Consumer returning 3 records: 2 EXEC family (EXEC_INSTANCE_METHOD,
    //        EXEC_RETURN_VALUE) and 1 CONTROL family (CONTROL_MESSAGE_REQUEST)
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: Returns list of size 2, only EXEC messages

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that multiple EXEC messages from Kafka are read in correct order.
   *
   * <p>This mirrors {@link #readsMultipleExecMessages()} but for the Kafka reading path. A mock
   * {@code Consumer} returns 5 EXEC records (mix of OPERATION and COMPLETION types). All 5 should
   * appear in the result list in the same order.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void readsMultipleExecMessagesFromKafka() {
    // Given: A mock Consumer returning 5 EXEC records (mix of OPERATION and COMPLETION types)
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: Returns list of size 5 in correct order

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that Kafka record offsets are correctly mapped to {@link WalEntry} offsets.
   *
   * <p>This mirrors {@link #preservesOffsetOrder()} but for the Kafka reading path. Kafka record
   * offsets (0, 1, 2, 3) should be preserved as the corresponding {@code WalEntry} offsets.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void preservesKafkaOffsetOrder() {
    // Given: A mock Consumer returning records with Kafka offsets 0, 1, 2, 3
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: WalEntry offsets match Kafka record offsets (0, 1, 2, 3)

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that fields are correctly deserialized from a Kafka record into a {@link WalEntry}.
   *
   * <p>This mirrors {@link #extractsCorrectFields()} but for the Kafka reading path. A single
   * EXEC_INSTANCE_METHOD record with known className, methodName, threadName, and builderSeq is
   * consumed. The resulting {@code WalEntry} must have matching field values.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void extractsCorrectFieldsFromKafka() {
    // Given: A mock Consumer returning a single EXEC_INSTANCE_METHOD record with known
    //        className, methodName, threadName, builderSeq
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: WalEntry has correct className, executableName, threadName, builderSeq,
    //       messageType, kind

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #846")
  public void filtersBootstrapMainCompletionFromKafka() {
    // Given: A mock Consumer returning records where last entry is an orphaned main()
    //        COMPLETION (EXEC_RETURN_VALUE with executableName="main" and no matching OPERATION)
    // When: readKafkaWal(consumer, "test-topic") is called
    // Then: The orphaned main() completion is removed from results

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  // ============================================================================
  // isChronicleLog tests (awaiting implementation in #846)
  // ============================================================================

  /**
   * Verifies that {@code isChronicleLog} returns {@code true} for a log spec with the {@code file:}
   * prefix.
   */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void isChronicleLogDetectsFilePrefix() {
    // Given: logSpec = "file:/tmp/my-wal"
    // When: WalReader.isChronicleLog(logSpec) is called
    // Then: Returns true

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that {@code isChronicleLog} returns {@code false} for a plain Kafka topic name. */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void isChronicleLogRejectsPlainTopicName() {
    // Given: logSpec = "my-kafka-topic"
    // When: WalReader.isChronicleLog(logSpec) is called
    // Then: Returns false

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that {@code isChronicleLog} returns {@code false} for a {@code null} log spec. */
  @Test
  @Ignore("Awaiting implementation in #846")
  public void isChronicleLogHandlesNull() {
    // Given: logSpec = null
    // When: WalReader.isChronicleLog(logSpec) is called
    // Then: Returns false

    // TODO(#846): Implement test logic
    fail("Not yet implemented");
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
}
