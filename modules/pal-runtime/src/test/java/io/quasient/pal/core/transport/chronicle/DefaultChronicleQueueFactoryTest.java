/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.chronicle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link DefaultChronicleQueueFactory}.
 *
 * <p>These tests verify that the factory creates queues with correct read/write permissions.
 */
public class DefaultChronicleQueueFactoryTest {

  private DefaultChronicleQueueFactory factory;
  private Path tempDir;

  @Before
  public void setUp() throws IOException {
    factory = new DefaultChronicleQueueFactory();
    tempDir = Files.createTempDirectory("chronicle-factory-test");
  }

  @After
  public void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> files = Files.walk(tempDir)) {
        files
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    // Ignore cleanup errors
                  }
                });
      }
    }
  }

  /**
   * Tests that create() produces a writable Chronicle queue.
   *
   * <p>This test verifies that queues created with the standard create() method can have appenders
   * created and can write data.
   */
  @Test
  public void create_shouldProduceWritableQueue() throws IOException {
    Path queuePath = tempDir.resolve("writable-queue");

    // Create a queue using the factory
    ChronicleQueue queue =
        factory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024);

    assertNotNull("Queue should not be null", queue);

    // Try to create an appender - this should succeed for writable queues
    ExcerptAppender appender = queue.createAppender();
    assertNotNull("Appender should not be null", appender);

    // Verify we can write to it
    appender.writeText("test message");

    // Clean up
    appender.close();
    queue.close();
  }

  /**
   * Tests that createReadOnly() produces a read-only Chronicle queue.
   *
   * <p>This test verifies that queues created with createReadOnly() cannot have appenders created.
   */
  @Test(expected = IllegalStateException.class)
  public void createReadOnly_shouldProduceReadOnlyQueue() throws IOException {
    Path queuePath = tempDir.resolve("readonly-queue");

    // First create a writable queue and write some data
    try (ChronicleQueue writeQueue =
        factory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      ExcerptAppender appender = writeQueue.createAppender();
      appender.writeText("initial data");
      appender.close();
    }

    // Now open it as read-only
    ChronicleQueue readOnlyQueue = factory.createReadOnly(queuePath);
    assertNotNull("Read-only queue should not be null", readOnlyQueue);

    // This should throw IllegalStateException: "Can't append to a read-only chronicle"
    readOnlyQueue.createAppender();
  }

  /**
   * Tests that a newly created writable queue has correct file permissions.
   *
   * <p>Verifies that the queue directory is created and is writable.
   */
  @Test
  public void create_shouldCreateWritableDirectory() throws IOException {
    Path queuePath = tempDir.resolve("new-queue");
    assertFalse("Queue path should not exist initially", Files.exists(queuePath));

    // Create queue
    try (ChronicleQueue queue =
        factory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      assertNotNull("Queue should not be null", queue);
    }

    // Verify directory was created
    assertTrue("Queue directory should exist", Files.exists(queuePath));
    assertTrue("Queue path should be a directory", Files.isDirectory(queuePath));
    assertTrue("Queue directory should be writable", Files.isWritable(queuePath));
  }

  /**
   * Tests that we can write and then read from the same queue path.
   *
   * <p>This test creates a writable queue, writes data, closes it, then reopens as writable again
   * and appends more data.
   */
  @Test
  public void create_shouldAllowMultipleWriteSessions() throws IOException {
    Path queuePath = tempDir.resolve("multi-write-queue");

    // First write session
    try (ChronicleQueue queue =
        factory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      ExcerptAppender appender = queue.createAppender();
      appender.writeText("first write");
      appender.close();
    }

    // Second write session on same queue
    try (ChronicleQueue queue =
        factory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      ExcerptAppender appender = queue.createAppender();
      appender.writeText("second write");
      appender.close();
    }

    // If we got here without exceptions, the test passes
  }
}
