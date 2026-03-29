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
package io.quasient.pal.cxn.chronicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil.QueueIndexInfo;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ChronicleLogUtil}.
 *
 * <p>Tests the utility methods for Chronicle queue operations including existence checks, size
 * calculation, and queue deletion. Tests that require creating OutboundMsg objects are in the
 * messages package where package-private constructors are accessible.
 */
public class ChronicleLogUtilTest {

  /** Temporary directory for test queues. */
  private Path tempDir;

  /** Sets up a temporary directory for each test. */
  @Before
  public void setUp() throws IOException {
    tempDir = Files.createTempDirectory("chronicle-test-");
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

  // ==================== queueExists tests ====================

  /** Tests that queueExists returns false for null path. */
  @Test
  public void testQueueExists_nullPath_returnsFalse() {
    assertFalse("Null path should not exist", ChronicleLogUtil.queueExists(null));
  }

  /** Tests that queueExists returns false for non-existent path. */
  @Test
  public void testQueueExists_nonExistentPath_returnsFalse() {
    Path nonExistent = tempDir.resolve("non-existent-queue");
    assertFalse("Non-existent path should not exist", ChronicleLogUtil.queueExists(nonExistent));
  }

  /** Tests that queueExists returns false for a file (not directory). */
  @Test
  public void testQueueExists_fileNotDirectory_returnsFalse() throws IOException {
    Path file = tempDir.resolve("not-a-directory.txt");
    Files.createFile(file);
    assertFalse("File should not be considered a queue", ChronicleLogUtil.queueExists(file));
  }

  /** Tests that queueExists returns false for empty directory. */
  @Test
  public void testQueueExists_emptyDirectory_returnsFalse() throws IOException {
    Path emptyDir = tempDir.resolve("empty-queue");
    Files.createDirectory(emptyDir);
    assertFalse(
        "Empty directory should not be considered a queue", ChronicleLogUtil.queueExists(emptyDir));
  }

  // ==================== countMessages tests ====================

  /** Tests that countMessages returns 0 for non-existent queue. */
  @Test
  public void testCountMessages_nonExistentQueue_returnsZero() {
    Path nonExistent = tempDir.resolve("non-existent");
    assertEquals(
        "Non-existent queue should have 0 messages",
        0,
        ChronicleLogUtil.countMessages(nonExistent));
  }

  // ==================== isQueueEmpty tests ====================

  /** Tests that isQueueEmpty returns true for non-existent queue. */
  @Test
  public void testIsQueueEmpty_nonExistentQueue_returnsTrue() {
    Path nonExistent = tempDir.resolve("non-existent");
    assertTrue(
        "Non-existent queue should be considered empty",
        ChronicleLogUtil.isQueueEmpty(nonExistent));
  }

  // ==================== getQueueSizeInBytes tests ====================

  /** Tests that getQueueSizeInBytes returns 0 for null path. */
  @Test
  public void testGetQueueSizeInBytes_nullPath_returnsZero() {
    assertEquals("Null path should return 0 bytes", 0L, ChronicleLogUtil.getQueueSizeInBytes(null));
  }

  /** Tests that getQueueSizeInBytes returns 0 for non-existent path. */
  @Test
  public void testGetQueueSizeInBytes_nonExistentPath_returnsZero() {
    Path nonExistent = tempDir.resolve("non-existent");
    assertEquals(
        "Non-existent path should return 0 bytes",
        0L,
        ChronicleLogUtil.getQueueSizeInBytes(nonExistent));
  }

  // ==================== deleteQueue tests ====================

  /** Tests that deleteQueue returns false for null path. */
  @Test
  public void testDeleteQueue_nullPath_returnsFalse() {
    assertFalse("Deleting null path should return false", ChronicleLogUtil.deleteQueue(null));
  }

  /** Tests that deleteQueue returns false for non-existent path. */
  @Test
  public void testDeleteQueue_nonExistentPath_returnsFalse() {
    Path nonExistent = tempDir.resolve("non-existent");
    assertFalse(
        "Deleting non-existent path should return false",
        ChronicleLogUtil.deleteQueue(nonExistent));
  }

  /** Tests that deleteQueue successfully deletes an empty directory. */
  @Test
  public void testDeleteQueue_emptyDirectory_deletesSuccessfully() throws IOException {
    Path emptyDir = tempDir.resolve("to-delete-empty");
    Files.createDirectory(emptyDir);

    assertTrue("Directory should exist before deletion", Files.exists(emptyDir));
    assertTrue("Delete should succeed", ChronicleLogUtil.deleteQueue(emptyDir));
    assertFalse("Directory should not exist after deletion", Files.exists(emptyDir));
  }

  /** Tests that deleteQueue successfully deletes a directory with files. */
  @Test
  public void testDeleteQueue_directoryWithFiles_deletesSuccessfully() throws IOException {
    Path dirWithFiles = tempDir.resolve("to-delete-with-files");
    Files.createDirectory(dirWithFiles);
    Files.createFile(dirWithFiles.resolve("file1.txt"));
    Files.createFile(dirWithFiles.resolve("file2.txt"));

    assertTrue("Directory should exist before deletion", Files.exists(dirWithFiles));
    assertTrue("Delete should succeed", ChronicleLogUtil.deleteQueue(dirWithFiles));
    assertFalse("Directory should not exist after deletion", Files.exists(dirWithFiles));
  }

  // ==================== QueueIndexInfo tests ====================

  /** Tests QueueIndexInfo constructor and getters. */
  @Test
  public void testQueueIndexInfo_constructorAndGetters() {
    QueueIndexInfo info = new QueueIndexInfo(5, 10);
    assertEquals("First index should match", 5, info.getFirstIndex());
    assertEquals("Last index should match", 10, info.getLastIndex());
    assertEquals("Message count should be last - first + 1", 6, info.getMessageCount());
  }

  /** Tests QueueIndexInfo with zero-based indices. */
  @Test
  public void testQueueIndexInfo_zeroBased() {
    QueueIndexInfo info = new QueueIndexInfo(0, 0);
    assertEquals("First index should be 0", 0, info.getFirstIndex());
    assertEquals("Last index should be 0", 0, info.getLastIndex());
    assertEquals("Single message count", 1, info.getMessageCount());
  }

  /** Tests QueueIndexInfo with negative indices (empty queue). */
  @Test
  public void testQueueIndexInfo_negativeIndices_zeroMessageCount() {
    QueueIndexInfo info = new QueueIndexInfo(-1, -1);
    assertEquals("Negative indices should yield 0 message count", 0, info.getMessageCount());
  }

  /** Tests QueueIndexInfo with only first negative. */
  @Test
  public void testQueueIndexInfo_firstNegative_zeroMessageCount() {
    QueueIndexInfo info = new QueueIndexInfo(-1, 5);
    assertEquals("Negative first index should yield 0 message count", 0, info.getMessageCount());
  }

  /** Tests QueueIndexInfo with only last negative. */
  @Test
  public void testQueueIndexInfo_lastNegative_zeroMessageCount() {
    QueueIndexInfo info = new QueueIndexInfo(0, -1);
    assertEquals("Negative last index should yield 0 message count", 0, info.getMessageCount());
  }

  /** Tests QueueIndexInfo toString method. */
  @Test
  public void testQueueIndexInfo_toString() {
    QueueIndexInfo info = new QueueIndexInfo(0, 4);
    String str = info.toString();
    assertTrue("toString should contain firstIndex", str.contains("firstIndex=0"));
    assertTrue("toString should contain lastIndex", str.contains("lastIndex=4"));
    assertTrue("toString should contain messageCount", str.contains("messageCount=5"));
  }

  /** Tests QueueIndexInfo toString with large indices. */
  @Test
  public void testQueueIndexInfo_toString_largeIndices() {
    QueueIndexInfo info = new QueueIndexInfo(1000000, 2000000);
    String str = info.toString();
    assertTrue("toString should contain large firstIndex", str.contains("firstIndex=1000000"));
    assertTrue("toString should contain large lastIndex", str.contains("lastIndex=2000000"));
  }

  // ============================================================================
  // Tests for ChronicleLogUtil with real queues
  // These tests use real Chronicle queues (not mocks) to verify actual behavior.
  // ============================================================================

  /**
   * Verifies that queueExists returns true for a directory containing actual .cq4 Chronicle files.
   *
   * <p>This test creates a real Chronicle queue by appending messages, then verifies that
   * queueExists correctly detects the queue based on the presence of .cq4 files.
   */
  @Test
  public void queueExists_directoryWithCq4Files_true() {
    // Given: Directory with actual .cq4 Chronicle files (created by populating queue)
    Path queuePath = tempDir.resolve("queue-with-cq4-files");
    populateQueueWithMessages(queuePath, 1);

    // When: queueExists(path) called
    boolean exists = ChronicleLogUtil.queueExists(queuePath);

    // Then: Returns true
    assertTrue("Queue with .cq4 files should exist", exists);
  }

  /**
   * Verifies that countMessages returns 0 for a newly created empty Chronicle queue.
   *
   * <p>This test creates an empty Chronicle queue (with metadata but no messages) and verifies that
   * countMessages correctly returns 0.
   */
  @Test
  public void countMessages_emptyQueue_zero() {
    // Given: Newly created empty Chronicle queue
    Path queuePath = tempDir.resolve("empty-queue-count");
    createEmptyQueue(queuePath);

    // When: countMessages(path) called
    int count = ChronicleLogUtil.countMessages(queuePath);

    // Then: Returns 0
    assertEquals("Empty queue should have 0 messages", 0, count);
  }

  /**
   * Verifies that countMessages returns the exact number of messages in a populated queue.
   *
   * <p>This test creates a Chronicle queue with exactly 42 messages and verifies that countMessages
   * returns the exact count.
   */
  @Test
  public void countMessages_knownMessageCount_exactCount() {
    // Given: Queue populated with exactly 42 messages
    Path queuePath = tempDir.resolve("queue-with-42-messages");
    populateQueueWithMessages(queuePath, 42);

    // When: countMessages(path) called
    int count = ChronicleLogUtil.countMessages(queuePath);

    // Then: Returns 42
    assertEquals("Queue should have exactly 42 messages", 42, count);
  }

  /**
   * Verifies that isQueueEmpty returns true for an existing but empty Chronicle queue.
   *
   * <p>This test creates a Chronicle queue directory structure (by opening and closing a queue)
   * without writing any messages, then verifies isQueueEmpty returns true.
   */
  @Test
  public void isQueueEmpty_emptyExistingQueue_true() {
    // Given: Existing queue directory with no messages
    Path queuePath = tempDir.resolve("empty-queue-exists");
    createEmptyQueue(queuePath);

    // When: isQueueEmpty(path) called
    boolean isEmpty = ChronicleLogUtil.isQueueEmpty(queuePath);

    // Then: Returns true
    assertTrue("Empty existing queue should be considered empty", isEmpty);
  }

  /**
   * Verifies that isQueueEmpty returns false for a Chronicle queue containing messages.
   *
   * <p>This test creates a Chronicle queue with at least one message and verifies that isQueueEmpty
   * correctly returns false.
   */
  @Test
  public void isQueueEmpty_populatedQueue_false() {
    // Given: Queue with at least one message
    Path queuePath = tempDir.resolve("populated-queue");
    populateQueueWithMessages(queuePath, 1);

    // When: isQueueEmpty(path) called
    boolean isEmpty = ChronicleLogUtil.isQueueEmpty(queuePath);

    // Then: Returns false
    assertFalse("Populated queue should not be considered empty", isEmpty);
  }

  /**
   * Verifies that getQueueIndexInfo returns negative indices for an empty Chronicle queue.
   *
   * <p>For an empty queue, the method should return QueueIndexInfo with firstIndex=-1 and
   * lastIndex=-1 to indicate no messages are present.
   */
  @Test
  public void getQueueIndexInfo_emptyQueue_negativeIndices() {
    // Given: Empty Chronicle queue
    Path queuePath = tempDir.resolve("empty-queue-index");
    createEmptyQueue(queuePath);

    // When: getQueueIndexInfo(path) called
    QueueIndexInfo info = ChronicleLogUtil.getQueueIndexInfo(queuePath);

    // Then: Returns QueueIndexInfo with firstIndex=-1, lastIndex=-1
    assertNotNull("QueueIndexInfo should not be null", info);
    assertEquals("Empty queue should have firstIndex=-1", -1, info.getFirstIndex());
    assertEquals("Empty queue should have lastIndex=-1", -1, info.getLastIndex());
    assertEquals("Empty queue should have messageCount=0", 0, info.getMessageCount());
  }

  /**
   * Verifies that getQueueIndexInfo returns 0,0 indices for a queue with exactly one message.
   *
   * <p>For a queue with a single message, the logical indices should be firstIndex=0 and
   * lastIndex=0.
   */
  @Test
  public void getQueueIndexInfo_singleMessage_zeroZero() {
    // Given: Queue with exactly 1 message
    Path queuePath = tempDir.resolve("single-message-queue");
    populateQueueWithMessages(queuePath, 1);

    // When: getQueueIndexInfo(path) called
    QueueIndexInfo info = ChronicleLogUtil.getQueueIndexInfo(queuePath);

    // Then: Returns QueueIndexInfo with firstIndex=0, lastIndex=0
    assertNotNull("QueueIndexInfo should not be null", info);
    assertEquals("Single message queue should have firstIndex=0", 0, info.getFirstIndex());
    assertEquals("Single message queue should have lastIndex=0", 0, info.getLastIndex());
    assertEquals("Single message queue should have messageCount=1", 1, info.getMessageCount());
  }

  /**
   * Verifies that getQueueIndexInfo returns correct index range for multiple messages.
   *
   * <p>For a queue with 10 messages, the logical indices should be firstIndex=0 and lastIndex=9.
   */
  @Test
  public void getQueueIndexInfo_multipleMessages_correctRange() {
    // Given: Queue with exactly 10 messages
    Path queuePath = tempDir.resolve("ten-message-queue");
    populateQueueWithMessages(queuePath, 10);

    // When: getQueueIndexInfo(path) called
    QueueIndexInfo info = ChronicleLogUtil.getQueueIndexInfo(queuePath);

    // Then: Returns QueueIndexInfo with firstIndex=0, lastIndex=9
    assertNotNull("QueueIndexInfo should not be null", info);
    assertEquals("Ten message queue should have firstIndex=0", 0, info.getFirstIndex());
    assertEquals("Ten message queue should have lastIndex=9", 9, info.getLastIndex());
    assertEquals("Ten message queue should have messageCount=10", 10, info.getMessageCount());
  }

  /**
   * Verifies that getQueueSizeInBytes returns 0 for an empty Chronicle queue.
   *
   * <p>An empty queue should report 0 bytes of logical data, even though Chronicle may allocate
   * files for metadata.
   */
  @Test
  public void getQueueSizeInBytes_emptyQueue_zero() {
    // Given: Empty Chronicle queue
    Path queuePath = tempDir.resolve("empty-queue-size");
    createEmptyQueue(queuePath);

    // When: getQueueSizeInBytes(path) called
    long size = ChronicleLogUtil.getQueueSizeInBytes(queuePath);

    // Then: Returns 0
    assertEquals("Empty queue should have 0 bytes", 0L, size);
  }

  /**
   * Verifies that getQueueSizeInBytes returns a non-zero value for a populated queue.
   *
   * <p>A queue with messages should report a positive byte size reflecting the actual data written.
   */
  @Test
  public void getQueueSizeInBytes_populatedQueue_nonZero() {
    // Given: Queue with known messages
    Path queuePath = tempDir.resolve("populated-queue-size");
    populateQueueWithMessages(queuePath, 10);

    // When: getQueueSizeInBytes(path) called
    long size = ChronicleLogUtil.getQueueSizeInBytes(queuePath);

    // Then: Returns value > 0
    assertTrue("Populated queue should have non-zero byte size", size > 0);
  }

  /**
   * Verifies that deleteQueue successfully removes a real Chronicle queue with .cq4 files.
   *
   * <p>This test creates a real Chronicle queue with messages (including .cq4 files and metadata),
   * then verifies that deleteQueue successfully removes all files and the directory.
   */
  @Test
  public void deleteQueue_chronicleQueueWithFiles_deleted() {
    // Given: Queue with .cq4 files and metadata
    Path queuePath = tempDir.resolve("queue-to-delete");
    populateQueueWithMessages(queuePath, 5);
    assertTrue("Queue directory should exist before deletion", Files.exists(queuePath));
    assertTrue("Queue should exist before deletion", ChronicleLogUtil.queueExists(queuePath));

    // When: deleteQueue(path) called
    boolean deleted = ChronicleLogUtil.deleteQueue(queuePath);

    // Then: Returns true; directory no longer exists
    assertTrue("deleteQueue should return true", deleted);
    assertFalse("Queue directory should not exist after deletion", Files.exists(queuePath));
  }

  // ============================================================================
  // Helper method for populating queues with test messages
  // ============================================================================

  /**
   * Creates a Chronicle queue at the specified path and appends the given number of OutboundMsg
   * instances.
   *
   * <p>Each message is created with a unique message ID and minimal body content. The queue is
   * closed after writing, ensuring all data is flushed.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * Path queuePath = tempDir.resolve("test-queue");
   * populateQueueWithMessages(queuePath, 42);
   * // Queue now contains 42 messages
   * }</pre>
   *
   * @param queuePath the path where the Chronicle queue should be created
   * @param count the number of messages to append to the queue
   */
  private void populateQueueWithMessages(Path queuePath, int count) {
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender appender = queue.createAppender();

      for (int i = 0; i < count; i++) {
        // Create an ExecMessage as the Marshallable body
        ExecMessage execMessage = new ExecMessage();
        execMessage.peerUuid = UuidUtils.toBytes(UUID.randomUUID());
        execMessage.messageId = UUID.randomUUID().toString();
        execMessage.threadName = "test-thread-" + i;
        execMessage.currentTime = System.currentTimeMillis() * 1_000_000L;
        execMessage.dispatchSeq = i;
        execMessage.builderSeq = i;

        // Create OutboundMsg using public constructor with Marshallable
        String messageId = UUID.randomUUID().toString();
        OutboundMsg msg =
            new OutboundMsg(
                MessageType.EXEC_INSTANCE_METHOD,
                ExecPhase.BEFORE,
                null, // no headers
                messageId,
                null, // no responseToId
                execMessage);

        // Write to queue
        msg.appendTo(appender);
      }
    }
  }

  /**
   * Creates an empty Chronicle queue at the specified path.
   *
   * <p>The queue is opened and immediately closed, which creates the queue directory and metadata
   * files but no messages.
   *
   * @param queuePath the path where the Chronicle queue should be created
   */
  private void createEmptyQueue(Path queuePath) {
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.single(queuePath.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      // Just create the queue and close it - this creates the queue directory structure
      queue.createAppender();
    }
  }
}
