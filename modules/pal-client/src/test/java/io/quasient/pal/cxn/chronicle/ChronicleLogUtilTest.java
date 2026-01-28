/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.chronicle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.cxn.chronicle.ChronicleLogUtil.QueueIndexInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
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
}
