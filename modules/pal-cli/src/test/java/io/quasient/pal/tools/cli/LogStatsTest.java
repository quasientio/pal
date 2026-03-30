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
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Method;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.tools.stats.ContinuousPrinter;
import io.quasient.pal.tools.stats.Counters;
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
import org.apache.kafka.streams.KafkaStreams;
import org.junit.Test;

/**
 * Unit tests for {@link LogStats}.
 *
 * <p>LogStats is the log-specific stats command extracted from {@code MessageStreamStats} to follow
 * the entity-operation pattern ({@code pal log stats}). It handles Kafka Streams-based log message
 * statistics collection, counter tracking, and Kafka shutdown lifecycle.
 */
public class LogStatsTest {

  // ==================== runCommand() Tests ====================

  /**
   * Tests that runCommand with a positional log name starts Kafka Streams.
   *
   * <p>Verifies that providing a log name and bootstrap servers creates a LogStats instance that
   * can be configured for Kafka Streams processing. Since actually starting Kafka Streams requires
   * a running broker, this test verifies the construction and configuration path.
   */
  @Test
  public void runCommand_withLogName_startsKafkaStreams() {
    // Given: positional log name argument and -k kafka servers
    LogStats stats = new LogStats("localhost:9092", "test-log");

    // Then: instance is configured and counters are accessible
    assertNotNull(stats.getCounters());
    assertThat(stats.getCounters().getNumberOfMessages().get(), is(0L));
  }

  // ==================== updateCounters() Tests ====================

  /**
   * Tests that updateCounters increments the total message count.
   *
   * <p>Verifies that calling updateCounters with a valid message increments the
   * counters.getNumberOfMessages() value by 1.
   */
  @Test
  public void updateCounters_incrementsMessageCount() {
    // Given: LogStats instance with a valid message
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);

    ExecMessage execMessage = builder.buildEmptyConstructor(peerId, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: counters.getNumberOfMessages() incremented by 1
    Counters counters = stats.getCounters();
    assertThat(counters.getNumberOfMessages().get(), is(1L));
  }

  /**
   * Tests that updateCounters tracks message types correctly.
   *
   * <p>Verifies that processing a message of type INSTANCE_METHOD results in the message type being
   * tracked in counters.getMessagesByType().
   */
  @Test
  public void updateCounters_tracksMessageTypes() {
    // Given: message of type EXEC_INSTANCE_METHOD
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);

    ExecMessage execMessage =
        builder.buildInstanceMethod(
            peerId,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: counters.getMessagesByType() contains EXEC_INSTANCE_METHOD entry with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD"));
    assertThat(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD").get(), is(1L));
  }

  /**
   * Tests that updateCounters tracks messages from specific peers.
   *
   * <p>Verifies that messages with specific peer UUIDs are tracked correctly in
   * counters.getMessagesFromPeer().
   */
  @Test
  public void updateCounters_tracksMessagesFromPeer() {
    // Given: message from peer with UUID X
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);

    ExecMessage execMessage = builder.buildEmptyConstructor(peerId, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: counters.getMessagesFromPeer() contains peer X entry with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getMessagesFromPeer().get(peerId.toString()));
    assertThat(counters.getMessagesFromPeer().get(peerId.toString()).get(), is(1L));
  }

  /**
   * Tests that updateCounters handles null execMessage gracefully.
   *
   * <p>Verifies that when a message has a null execMessage (e.g., a ControlMessage), updateCounters
   * returns early without incrementing detailed counters but still increments the basic message
   * count.
   */
  @Test
  public void updateCounters_handlesNullExecMessage() {
    // Given: message with null execMessage (e.g., a ControlMessage)
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);

    ControlMessage controlMessage =
        builder.buildControlCommandMessage(peerId, ControlCommandType.GC);
    Message message = builder.wrap(controlMessage);

    assertNull(message.getExecMessage());

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: basic message count incremented, but detailed counters remain empty
    Counters counters = stats.getCounters();
    assertThat(counters.getNumberOfMessages().get(), is(1L));
    assertNotNull(counters.getMessagesByType().get("CONTROL_MESSAGE_REQUEST"));

    assertThat(counters.getObjectsCreated().isEmpty(), is(true));
    assertThat(counters.getMethodsCalled().isEmpty(), is(true));
    assertThat(counters.getFieldReads().isEmpty(), is(true));
    assertThat(counters.getFieldWrites().isEmpty(), is(true));
    assertThat(counters.getMessagesByThread().isEmpty(), is(true));
  }

  // ==================== increment*() Helper Methods Tests ====================

  /**
   * Tests that incrementObjectsCreated updates the counter correctly.
   *
   * <p>Verifies that calling incrementObjectsCreated with a class name results in the class being
   * tracked in counters.getObjectsCreated().
   */
  @Test
  public void incrementObjectsCreated_updatesCounter() {
    // Given: LogStats instance
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String className = "com.example.MyClass";

    // When: incrementObjectsCreated(className) called
    stats.incrementObjectsCreated(className);

    // Then: counters.getObjectsCreated() contains "com.example.MyClass" with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getObjectsCreated().get(className));
    assertThat(counters.getObjectsCreated().get(className).get(), is(1L));
  }

  /**
   * Tests that incrementMethodCalls updates the counter correctly.
   *
   * <p>Verifies that calling incrementMethodCalls with a method key results in the method being
   * tracked in counters.getMethodsCalled().
   */
  @Test
  public void incrementMethodCalls_updatesCounter() {
    // Given: LogStats instance
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String methodKey = "MyClass.myMethod()";

    // When: incrementMethodCalls(methodKey) called
    stats.incrementMethodCalls(methodKey);

    // Then: counters.getMethodsCalled() contains "MyClass.myMethod()" with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getMethodsCalled().get(methodKey));
    assertThat(counters.getMethodsCalled().get(methodKey).get(), is(1L));
  }

  /**
   * Tests that incrementFieldReads updates the counter correctly.
   *
   * <p>Verifies that calling incrementFieldReads with a field key results in the field being
   * tracked in counters.getFieldReads().
   */
  @Test
  public void incrementFieldReads_updatesCounter() {
    // Given: LogStats instance
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String fieldKey = "MyClass.myField";

    // When: incrementFieldReads(fieldKey) called
    stats.incrementFieldReads(fieldKey);

    // Then: counters.getFieldReads() contains "MyClass.myField" with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getFieldReads().get(fieldKey));
    assertThat(counters.getFieldReads().get(fieldKey).get(), is(1L));
  }

  /**
   * Tests that incrementFieldWrites updates the counter correctly.
   *
   * <p>Verifies that calling incrementFieldWrites with a field key results in the field being
   * tracked in counters.getFieldWrites().
   */
  @Test
  public void incrementFieldWrites_updatesCounter() {
    // Given: LogStats instance
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String fieldKey = "MyClass.myField";

    // When: incrementFieldWrites(fieldKey) called
    stats.incrementFieldWrites(fieldKey);

    // Then: counters.getFieldWrites() contains "MyClass.myField" with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getFieldWrites().get(fieldKey));
    assertThat(counters.getFieldWrites().get(fieldKey).get(), is(1L));
  }

  // ==================== getShortClassname() Tests ====================

  /**
   * Tests that getShortClassname extracts the simple class name from a fully qualified name.
   *
   * <p>Verifies that "com.example.MyClass" returns "MyClass".
   */
  @Test
  public void getShortClassname_extractsSimpleName() {
    // Given: fully qualified class name "com.example.MyClass"
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String fullClassName = "com.example.MyClass";

    // When: getShortClassname("com.example.MyClass") called
    String result = stats.getShortClassname(fullClassName);

    // Then: returns "MyClass"
    assertThat(result, is("MyClass"));
  }

  /**
   * Tests that getShortClassname handles class names without package prefixes.
   *
   * <p>Verifies that "MyClass" (no package) returns "MyClass" unchanged.
   */
  @Test
  public void getShortClassname_handlesNoPackage() {
    // Given: class name "MyClass" with no package prefix
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    String className = "MyClass";

    // When: getShortClassname("MyClass") called
    String result = stats.getShortClassname(className);

    // Then: returns "MyClass"
    assertThat(result, is("MyClass"));
  }

  // ==================== performKafkaShutdown() Tests ====================

  /**
   * Tests that performKafkaShutdown closes the Kafka streams.
   *
   * <p>Verifies that calling performKafkaShutdown() invokes close() on the KafkaStreams instance.
   */
  @Test
  public void performKafkaShutdown_closesStreams() {
    // Given: LogStats instance with a mock KafkaStreams set
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    KafkaStreams mockStreams = mock(KafkaStreams.class);
    stats.kafkaStreams = mockStreams;

    // When: performKafkaShutdown() called
    stats.performKafkaShutdown();

    // Then: streams.close() is invoked
    verify(mockStreams).close();
  }

  /**
   * Tests that performKafkaShutdown stops the continuous printer when present.
   *
   * <p>Verifies that calling performKafkaShutdown() invokes setDone(true) on the continuousPrinter
   * if it is not null.
   */
  @Test
  public void performKafkaShutdown_stopsContinuousPrinter() {
    // Given: LogStats instance with mock KafkaStreams and mock ContinuousPrinter
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    KafkaStreams mockStreams = mock(KafkaStreams.class);
    stats.kafkaStreams = mockStreams;
    ContinuousPrinter mockPrinter = mock(ContinuousPrinter.class);
    stats.continuousPrinter = mockPrinter;

    // When: performKafkaShutdown() called
    stats.performKafkaShutdown();

    // Then: continuousPrinter.setDone(true) is invoked
    verify(mockPrinter).setDone(true);
  }

  /**
   * Tests that performKafkaShutdown counts down the shutdown latch.
   *
   * <p>Verifies that calling performKafkaShutdown() decrements the shutdownLatch count to 0.
   */
  @Test
  public void performKafkaShutdown_countsDownLatch() {
    // Given: LogStats instance with shutdownLatch count of 1
    LogStats stats = new LogStats("localhost:9092", "test-log", null, null, null);
    KafkaStreams mockStreams = mock(KafkaStreams.class);
    stats.kafkaStreams = mockStreams;

    assertThat(stats.shutdownLatch.getCount(), is(1L));

    // When: performKafkaShutdown() called
    stats.performKafkaShutdown();

    // Then: shutdownLatch.getCount() returns 0
    assertThat(stats.shutdownLatch.getCount(), is(0L));
  }

  // ==================== Chronicle Stats Tests ====================

  /**
   * Tests that runCommand reads messages from a Chronicle Queue and updates counters.
   *
   * <p>Creates a temporary Chronicle queue with a constructor and an instance method message, then
   * verifies that the counters reflect both messages after running stats.
   */
  @Test
  public void runCommand_chronicleLog_updatesCounters() throws Exception {
    // Given: a Chronicle queue with 2 messages
    Path queueDir = Files.createTempDirectory("logstats-chronicle-test");
    try {
      UUID peerId = UUID.randomUUID();
      writeConstructorMessage(queueDir, peerId, "com.example.Foo");
      writeInstanceMethodMessage(queueDir, peerId, "com.example.Bar", "doSomething");

      LogStats stats = new LogStats(null, "file:" + queueDir, null, null, null);

      // When: runCommand() called
      int result = stats.runCommand();

      // Then: exit code 0, counters reflect both messages
      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertThat(counters.getNumberOfMessages().get(), is(2L));
      assertNotNull(counters.getMessagesByType().get("EXEC_CONSTRUCTOR"));
      assertThat(counters.getMessagesByType().get("EXEC_CONSTRUCTOR").get(), is(1L));
      assertNotNull(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD"));
      assertThat(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD").get(), is(1L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  /**
   * Tests that Chronicle stats correctly filters by message type.
   *
   * <p>Writes two different message types and configures the stats to only include constructors.
   * Verifies that only the constructor message is counted.
   */
  @Test
  public void runCommand_chronicleLog_filtersTypes() throws Exception {
    // Given: Chronicle queue with constructor + instance method, filter on EXEC_CONSTRUCTOR only
    Path queueDir = Files.createTempDirectory("logstats-chronicle-filter");
    try {
      UUID peerId = UUID.randomUUID();
      writeConstructorMessage(queueDir, peerId, "com.example.Foo");
      writeInstanceMethodMessage(queueDir, peerId, "com.example.Bar", "doSomething");

      LogStats stats =
          new LogStats(null, "file:" + queueDir, List.of("EXEC_CONSTRUCTOR"), null, null);

      // When: runCommand() called
      int result = stats.runCommand();

      // Then: only the constructor message is counted
      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertThat(counters.getNumberOfMessages().get(), is(1L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  /**
   * Tests that Chronicle stats correctly filters by peer UUID.
   *
   * <p>Writes messages from two different peers and filters for only one. Verifies that only the
   * matching peer's messages are counted.
   */
  @Test
  public void runCommand_chronicleLog_filtersPeer() throws Exception {
    // Given: Chronicle queue with messages from two peers, filter on one
    Path queueDir = Files.createTempDirectory("logstats-chronicle-peer");
    try {
      UUID peer1 = UUID.randomUUID();
      UUID peer2 = UUID.randomUUID();
      writeConstructorMessage(queueDir, peer1, "com.example.Foo");
      writeConstructorMessage(queueDir, peer2, "com.example.Bar");
      writeInstanceMethodMessage(queueDir, peer1, "com.example.Baz", "run");

      LogStats stats = new LogStats(null, "file:" + queueDir, null, peer1.toString(), null);

      // When: runCommand() called
      int result = stats.runCommand();

      // Then: only peer1's 2 messages are counted
      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertThat(counters.getNumberOfMessages().get(), is(2L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  /**
   * Tests that runCommand returns 1 when the Chronicle queue path does not exist.
   *
   * <p>Verifies that a non-existent file: path causes the command to fail gracefully.
   */
  @Test
  public void runCommand_chronicleLog_nonExistentPath_returnsError() {
    // Given: file: path that does not exist
    LogStats stats =
        new LogStats(null, "file:/tmp/does-not-exist-" + UUID.randomUUID(), null, null, null);

    // When: runCommand() called
    int result = stats.runCommand();

    // Then: exit code 1
    assertThat(result, is(1));
  }

  // ==================== Exception Stats Tests ====================

  /**
   * Tests that updateCounters tracks exception types from EXEC_THROWABLE messages.
   *
   * <p>Writes a throwable message to a Chronicle queue and verifies that the exception type is
   * tracked in counters.getExceptionsByType().
   */
  @Test
  public void updateCounters_tracksExceptionsByType() throws Exception {
    Path queueDir = Files.createTempDirectory("logstats-exception-type");
    try {
      UUID peerId = UUID.randomUUID();
      writeThrowableMessage(
          queueDir,
          peerId,
          "java.lang.RuntimeException",
          "something went wrong",
          "com.example.Foo",
          "doStuff");

      LogStats stats = new LogStats(null, "file:" + queueDir, null, null, null);
      int result = stats.runCommand();

      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertNotNull(counters.getExceptionsByType().get("java.lang.RuntimeException"));
      assertThat(counters.getExceptionsByType().get("java.lang.RuntimeException").get(), is(1L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  /**
   * Tests that updateCounters tracks which methods throw exceptions.
   *
   * <p>Writes a throwable message and verifies that the originating method is tracked in
   * counters.getExceptionsPerMethod().
   */
  @Test
  public void updateCounters_tracksExceptionsPerMethod() throws Exception {
    Path queueDir = Files.createTempDirectory("logstats-exception-method");
    try {
      UUID peerId = UUID.randomUUID();
      writeThrowableMessage(
          queueDir,
          peerId,
          "java.lang.IllegalArgumentException",
          "bad arg",
          "com.example.Bar",
          "validate");

      LogStats stats = new LogStats(null, "file:" + queueDir, null, null, null);
      int result = stats.runCommand();

      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertNotNull(counters.getExceptionsPerMethod().get("Bar.validate()"));
      assertThat(counters.getExceptionsPerMethod().get("Bar.validate()").get(), is(1L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  // ==================== Entry Point Tests ====================

  /**
   * Tests that updateCounters counts entry-point messages.
   *
   * <p>Writes a constructor message marked as an entry point and a regular method message. Verifies
   * that the entry-point counter reflects only the entry-point message.
   */
  @Test
  public void updateCounters_countsEntryPoints() throws Exception {
    Path queueDir = Files.createTempDirectory("logstats-entrypoint");
    try {
      UUID peerId = UUID.randomUUID();
      writeEntryPointConstructorMessage(queueDir, peerId, "com.example.Main");
      writeInstanceMethodMessage(queueDir, peerId, "com.example.Foo", "run");

      LogStats stats = new LogStats(null, "file:" + queueDir, null, null, null);
      int result = stats.runCommand();

      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertThat(counters.getNumberOfMessages().get(), is(2L));
      assertThat(counters.getEntryPointCount().get(), is(1L));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  // ==================== Time Span Tests ====================

  /**
   * Tests that updateCounters tracks the time span of processed messages.
   *
   * <p>Writes two messages with different timestamps and verifies that the first and last message
   * timestamps are recorded correctly.
   */
  @Test
  public void updateCounters_tracksTimeSpan() throws Exception {
    Path queueDir = Files.createTempDirectory("logstats-timespan");
    try {
      UUID peerId = UUID.randomUUID();
      long firstTime = 1_000_000_000_000L;
      long secondTime = 2_000_000_000_000L;
      writeConstructorMessageWithTime(queueDir, peerId, "com.example.Foo", firstTime);
      writeConstructorMessageWithTime(queueDir, peerId, "com.example.Bar", secondTime);

      LogStats stats = new LogStats(null, "file:" + queueDir, null, null, null);
      int result = stats.runCommand();

      assertThat(result, is(0));
      Counters counters = stats.getCounters();
      assertThat(counters.getFirstMessageTimeNanos(), is(firstTime));
      assertThat(counters.getLastMessageTimeNanos(), is(secondTime));
    } finally {
      deleteDirectory(queueDir);
    }
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that validation fails when no log name is provided.
   *
   * <p>Verifies that invoking the command without a positional log name argument results in an
   * error.
   */
  @Test(expected = RuntimeException.class)
  public void validateInput_noLogName_throwsError() {
    // Given: no positional log name argument
    LogStats stats = new LogStats();

    // When: validateInput() called
    // Then: error is thrown indicating log name is required
    stats.validateInput();
  }

  // ==================== Test Helpers ====================

  /**
   * Writes an EXEC_CONSTRUCTOR message to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param className the class name for the constructor
   */
  private void writeConstructorMessage(Path queueDir, UUID peerId, String className) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);

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
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Writes an EXEC_INSTANCE_METHOD message to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param className the class name
   * @param methodName the method name
   */
  private void writeInstanceMethodMessage(
      Path queueDir, UUID peerId, String className, String methodName) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);

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
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Writes an EXEC_THROWABLE message to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param exceptionType the fully qualified exception class name
   * @param exceptionMessage the exception message
   * @param fromClassName the class where the exception was thrown
   * @param fromMethodName the method where the exception was thrown
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private void writeThrowableMessage(
      Path queueDir,
      UUID peerId,
      String exceptionType,
      String exceptionMessage,
      String fromClassName,
      String fromMethodName) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);

      io.quasient.pal.messages.colfer.Throwable thrown =
          new io.quasient.pal.messages.colfer.Throwable();
      thrown.setType(exceptionType);
      thrown.setMessage(exceptionMessage);

      Method method = new Method();
      method.setName(fromMethodName);
      Class clazz = new Class();
      clazz.setName(fromClassName);
      method.setClazz(clazz);

      Reflectable from = new Reflectable();
      from.setMethod(method);

      RaisedThrowable raised = new RaisedThrowable();
      raised.setThrowable(thrown);
      raised.setFrom(from);
      execMsg.setRaisedThrowable(raised);

      Message wrapper =
          new Message()
              .withMessageType(MessageType.EXEC_THROWABLE.getId())
              .withExecMessage(execMsg);

      OutboundMsg outboundMsg =
          new OutboundMsg(
              MessageType.EXEC_THROWABLE,
              ExecPhase.BEFORE,
              null,
              execMsg.getMessageId(),
              null,
              wrapper);
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Writes an EXEC_CONSTRUCTOR message marked as an entry point to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param className the class name for the constructor
   */
  private void writeEntryPointConstructorMessage(Path queueDir, UUID peerId, String className) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);
      execMsg.setEntryPoint(true);

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
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Writes an EXEC_CONSTRUCTOR message with a specific timestamp to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param className the class name for the constructor
   * @param timeNanos the message timestamp in nanoseconds
   */
  private void writeConstructorMessageWithTime(
      Path queueDir, UUID peerId, String className, long timeNanos) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(timeNanos);

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
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Recursively deletes a directory and its contents.
   *
   * @param dir the directory to delete
   * @throws IOException if an I/O error occurs
   */
  private static void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (Stream<Path> walk = Files.walk(dir)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    // best-effort cleanup in tests
                  }
                });
      }
    }
  }
}
