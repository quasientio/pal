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
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.tools.stats.Counters;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link MessageStreamStats}.
 *
 * <p>Tests updateCounters(), increment methods, and getShortClassname() helper methods using
 * reflection to access private methods.
 */
public class MessageStreamStatsTest {

  // ==================== updateCounters() Tests ====================

  /**
   * Tests that updateCounters increments the total message count.
   *
   * <p>Verifies that calling updateCounters with a valid message increments the
   * counters.getNumberOfMessages() value by 1.
   */
  @Test
  public void testUpdateCounters_incrementsMessageCount() throws Exception {
    // Given: MessageStreamStats instance; Message with valid content
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);

    ExecMessage execMessage = builder.buildEmptyConstructor(peerId, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called via reflection
    invokeUpdateCounters(stats, message);

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
  public void testUpdateCounters_tracksMessageTypes() throws Exception {
    // Given: Message of type INSTANCE_METHOD
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);

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
    invokeUpdateCounters(stats, message);

    // Then: counters.getMessagesByType() contains EXEC_INSTANCE_METHOD entry
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
  public void testUpdateCounters_tracksMessagesFromPeer() throws Exception {
    // Given: Message with specific peer UUID
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);

    ExecMessage execMessage = builder.buildEmptyConstructor(peerId, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    invokeUpdateCounters(stats, message);

    // Then: counters.getMessagesFromPeer() contains peer UUID entry
    Counters counters = stats.getCounters();
    assertNotNull(counters.getMessagesFromPeer().get(peerId.toString()));
    assertThat(counters.getMessagesFromPeer().get(peerId.toString()).get(), is(1L));
  }

  /**
   * Tests that updateCounters handles null execMessage gracefully.
   *
   * <p>Verifies that when a message has a null execMessage, updateCounters returns early without
   * incrementing detailed counters (objects created, methods called, field accesses) but still
   * increments the basic message count.
   */
  @Test
  public void testUpdateCounters_handlesNullExecMessage() throws Exception {
    // Given: Message with null execMessage (using a ControlMessage instead)
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);

    // Create a ControlMessage which results in a Message with null execMessage
    ControlMessage controlMessage =
        builder.buildControlCommandMessage(peerId, ControlCommandType.GC);
    Message message = builder.wrap(controlMessage);

    // Verify that execMessage is null
    assertNull(message.getExecMessage());

    // When: updateCounters(message) called
    invokeUpdateCounters(stats, message);

    // Then: Returns early without incrementing detailed counters
    // Basic counters are incremented
    Counters counters = stats.getCounters();
    assertThat(counters.getNumberOfMessages().get(), is(1L));
    assertNotNull(counters.getMessagesByType().get("CONTROL_MESSAGE_REQUEST"));

    // Detailed counters remain empty because execMessage is null
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
  public void testIncrementObjectsCreated_updatesCounter() throws Exception {
    // Given: MessageStreamStats instance
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String className = "com.example.MyClass";

    // When: incrementObjectsCreated(className) called via reflection
    invokeIncrementObjectsCreated(stats, className);

    // Then: counters.getObjectsCreated() contains className
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
  public void testIncrementMethodCalls_updatesCounter() throws Exception {
    // Given: MessageStreamStats instance
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String methodKey = "MyClass.myMethod()";

    // When: incrementMethodCalls(methodKey) called via reflection
    invokeIncrementMethodCalls(stats, methodKey);

    // Then: counters.getMethodsCalled() contains methodKey
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
  public void testIncrementFieldReads_updatesCounter() throws Exception {
    // Given: MessageStreamStats instance
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String fieldKey = "MyClass.myField";

    // When: incrementFieldReads(fieldKey) called via reflection
    invokeIncrementFieldReads(stats, fieldKey);

    // Then: counters.getFieldReads() contains fieldKey
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
  public void testIncrementFieldWrites_updatesCounter() throws Exception {
    // Given: MessageStreamStats instance
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String fieldKey = "MyClass.myField";

    // When: incrementFieldWrites(fieldKey) called via reflection
    invokeIncrementFieldWrites(stats, fieldKey);

    // Then: counters.getFieldWrites() contains fieldKey
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
  public void testGetShortClassname_extractsSimpleName() throws Exception {
    // Given: Full class name "com.example.MyClass"
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String fullClassName = "com.example.MyClass";

    // When: getShortClassname(fullName) called via reflection
    String result = invokeGetShortClassname(stats, fullClassName);

    // Then: Returns "MyClass"
    assertThat(result, is("MyClass"));
  }

  /**
   * Tests that getShortClassname handles class names without package prefixes.
   *
   * <p>Verifies that "MyClass" (no package) returns "MyClass".
   */
  @Test
  public void testGetShortClassname_handlesNoPackage() throws Exception {
    // Given: Class name "MyClass" (no package)
    MessageStreamStats stats =
        new MessageStreamStats("localhost:9092", "test-log", null, null, null);
    String className = "MyClass";

    // When: getShortClassname(name) called
    String result = invokeGetShortClassname(stats, className);

    // Then: Returns "MyClass"
    assertThat(result, is("MyClass"));
  }

  // ==================== Helper Methods ====================
  // These methods are provided for use when test implementations are added in #365.
  // They are intentionally unused in the specification stubs.

  /**
   * Invokes the private updateCounters method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param message the message to process
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static void invokeUpdateCounters(MessageStreamStats stats, Message message)
      throws Exception {
    Method method = MessageStreamStats.class.getDeclaredMethod("updateCounters", Message.class);
    method.setAccessible(true);
    method.invoke(stats, message);
  }

  /**
   * Invokes the private incrementObjectsCreated method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param key the class name key
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static void invokeIncrementObjectsCreated(MessageStreamStats stats, String key)
      throws Exception {
    Method method =
        MessageStreamStats.class.getDeclaredMethod("incrementObjectsCreated", String.class);
    method.setAccessible(true);
    method.invoke(stats, key);
  }

  /**
   * Invokes the private incrementMethodCalls method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param key the method key
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static void invokeIncrementMethodCalls(MessageStreamStats stats, String key)
      throws Exception {
    Method method =
        MessageStreamStats.class.getDeclaredMethod("incrementMethodCalls", String.class);
    method.setAccessible(true);
    method.invoke(stats, key);
  }

  /**
   * Invokes the private incrementFieldReads method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param key the field key
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static void invokeIncrementFieldReads(MessageStreamStats stats, String key)
      throws Exception {
    Method method = MessageStreamStats.class.getDeclaredMethod("incrementFieldReads", String.class);
    method.setAccessible(true);
    method.invoke(stats, key);
  }

  /**
   * Invokes the private incrementFieldWrites method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param key the field key
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static void invokeIncrementFieldWrites(MessageStreamStats stats, String key)
      throws Exception {
    Method method =
        MessageStreamStats.class.getDeclaredMethod("incrementFieldWrites", String.class);
    method.setAccessible(true);
    method.invoke(stats, key);
  }

  /**
   * Invokes the private getShortClassname method on a MessageStreamStats instance.
   *
   * @param stats the MessageStreamStats instance
   * @param classname the full class name
   * @return the short class name
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod")
  private static String invokeGetShortClassname(MessageStreamStats stats, String classname)
      throws Exception {
    Method method = MessageStreamStats.class.getDeclaredMethod("getShortClassname", String.class);
    method.setAccessible(true);
    return (String) method.invoke(stats, classname);
  }

  // ==================== performKafkaShutdown() Tests ====================
  // Issue #372 - Awaiting implementation in #373

  /**
   * Tests that performKafkaShutdown closes the Kafka streams.
   *
   * <p>Verifies that calling performKafkaShutdown() invokes close() on the KafkaStreams instance.
   */
  @Test
  @Ignore("Awaiting implementation in #373")
  public void testPerformKafkaShutdown_closesStreams() {
    // Given: Mock KafkaStreams instance set on MessageStreamStats
    // When: performKafkaShutdown() called
    // Then: streams.close() invoked

    // TODO(#373): Implement test
    // Create MessageStreamStats instance
    // Create mock KafkaStreams using Mockito
    // Set the kafkaStreams field via reflection or package-private access
    // Set up shutdownLatch
    // Call performKafkaShutdown()
    // Verify that kafkaStreams.close() was called
    fail("Not yet implemented");
  }

  /**
   * Tests that performKafkaShutdown stops the continuous printer when present.
   *
   * <p>Verifies that calling performKafkaShutdown() invokes setDone(true) on the continuousPrinter
   * if it is not null.
   */
  @Test
  @Ignore("Awaiting implementation in #373")
  public void testPerformKafkaShutdown_stopsContinuousPrinter() {
    // Given: continuousPrinter is not null
    // When: performKafkaShutdown() called
    // Then: continuousPrinter.setDone(true) invoked

    // TODO(#373): Implement test
    // Create MessageStreamStats instance
    // Create mock KafkaStreams and ContinuousPrinter using Mockito
    // Set kafkaStreams and continuousPrinter fields via reflection
    // Set up shutdownLatch
    // Call performKafkaShutdown()
    // Verify that continuousPrinter.setDone(true) was called
    fail("Not yet implemented");
  }

  /**
   * Tests that performKafkaShutdown counts down the shutdown latch.
   *
   * <p>Verifies that calling performKafkaShutdown() decrements the shutdownLatch count to 0.
   */
  @Test
  @Ignore("Awaiting implementation in #373")
  public void testPerformKafkaShutdown_countsDownLatch() {
    // Given: shutdownLatch with count of 1
    // When: performKafkaShutdown() called
    // Then: shutdownLatch.getCount() returns 0

    // TODO(#373): Implement test
    // Create MessageStreamStats instance
    // Create mock KafkaStreams using Mockito
    // Set kafkaStreams field via reflection or package-private access
    // Assert shutdownLatch.getCount() == 1 before call
    // Call performKafkaShutdown()
    // Assert shutdownLatch.getCount() == 0 after call
    fail("Not yet implemented");
  }

  // ==================== performSocketShutdown() Tests ====================
  // Issue #372 - Awaiting implementation in #373

  /**
   * Tests that performSocketShutdown counts down the socket shutdown latch.
   *
   * <p>Verifies that calling performSocketShutdown() decrements the socketShutdownLatch count to 0.
   */
  @Test
  @Ignore("Awaiting implementation in #373")
  public void testPerformSocketShutdown_countsDownLatch() {
    // Given: Socket streaming latch with count of 1
    // When: performSocketShutdown() called
    // Then: latch count decremented to 0

    // TODO(#373): Implement test
    // Create MessageStreamStats instance
    // Create and set socketShutdownLatch field to new CountDownLatch(1)
    // Assert socketShutdownLatch.getCount() == 1 before call
    // Call performSocketShutdown()
    // Assert socketShutdownLatch.getCount() == 0 after call
    fail("Not yet implemented");
  }
}
