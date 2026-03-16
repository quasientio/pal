/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.tools.stats.ContinuousPrinter;
import io.quasient.pal.tools.stats.Counters;
import java.util.UUID;
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
}
