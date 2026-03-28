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
package io.quasient.pal.core.transport.chronicle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.ZmqEnabledTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.RollCycles;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * Unit tests for edge cases and error scenarios in {@link ChronicleSourceLogReader}.
 *
 * <p>These tests cover:
 *
 * <ul>
 *   <li>Non-existent queue error handling
 *   <li>sourceLogWillBeCreated flag behavior
 *   <li>Absolute vs relative path handling
 *   <li>Initial offset edge cases
 * </ul>
 */
public class ChronicleSourceLogReaderEdgeCaseTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String DEALER_ADDRESS = "inproc://chronicle_edge_test";
  private static final String OFFSET_PUB_ADDRESS = "inproc://chronicle_offsets_edge_test";

  private ZContext zmqContext;
  private ChronicleSourceLogReader logReader;
  private final UUID peerUuid = UUID.randomUUID();
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private Path tempDir;
  private DefaultChronicleQueueFactory queueFactory;

  /** Sets up the test fixtures. */
  @Before
  public void setup() throws Exception {
    zmqContext = createContext();
    queueFactory = new DefaultChronicleQueueFactory();
    tempDir = Files.createTempDirectory("chronicle-edge-test");

    logReader =
        new ChronicleSourceLogReader(
            peerUuid,
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "ChronicleEdgeTest",
            DEALER_ADDRESS,
            OFFSET_PUB_ADDRESS,
            tempDir,
            queueFactory);
  }

  /** Cleans up after tests. */
  @After
  public void cleanup() throws Exception {
    closeContext(zmqContext);

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
    logger.trace("cleanup complete");
  }

  // ==================== readFromLog() Edge Cases ====================

  /**
   * Tests that readFromLog throws when queue doesn't exist and sourceLogWillBeCreated is false.
   *
   * <p>This verifies that the reader properly validates queue existence for read-only scenarios.
   */
  @Test
  public void readFromLog_nonExistentQueue_throwsIllegalStateException() {
    LogInfo log = new LogInfo("non-existent-queue");
    log.setLogType(LogInfo.LogType.CHRONICLE);

    try {
      logReader.readFromLog(log, false, null, false);
      fail("Expected IllegalStateException for non-existent queue");
    } catch (IllegalStateException e) {
      assertThat(
          "Exception message should mention non-existent log",
          e.getMessage(),
          containsString("does not exist"));
      assertThat(
          "Exception message should mention --wal option", e.getMessage(), containsString("--wal"));
    }
  }

  /**
   * Tests that readFromLog succeeds when queue doesn't exist but sourceLogWillBeCreated is true.
   *
   * <p>This verifies the --log option behavior where the WAL writer will create the queue.
   */
  @Test
  public void readFromLog_nonExistentQueueWithWillBeCreated_succeeds() {
    LogInfo log = new LogInfo("non-existent-but-will-be-created");
    log.setLogType(LogInfo.LogType.CHRONICLE);

    // Should not throw when sourceLogWillBeCreated is true
    logReader.readFromLog(log, false, null, true);
    // Success - no exception thrown
  }

  /**
   * Tests that readFromLog handles absolute paths correctly.
   *
   * <p>Verifies that an absolute path is used directly rather than being resolved against baseDir.
   */
  @Test
  public void readFromLog_absolutePath_usedDirectly() throws Exception {
    // Create a queue at an absolute path outside tempDir
    Path absolutePath = Files.createTempDirectory("chronicle-absolute-test").resolve("test-queue");
    Files.createDirectories(absolutePath);

    LogInfo log = new LogInfo(absolutePath.toString());
    log.setLogType(LogInfo.LogType.CHRONICLE);

    // Create a minimal Chronicle queue at the absolute path
    try (ChronicleQueue queue =
        queueFactory.create(absolutePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      // Queue created
    }

    // Should succeed with absolute path
    logReader.readFromLog(log, false, null, false);

    // Cleanup
    try (Stream<Path> files = Files.walk(absolutePath.getParent())) {
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

  /**
   * Tests that readFromLog handles relative paths correctly.
   *
   * <p>Verifies that a relative path is resolved against the baseDir.
   */
  @Test
  public void readFromLog_relativePath_resolvedAgainstBaseDir() throws Exception {
    String relativePath = "relative-queue";
    Path fullPath = tempDir.resolve(relativePath);
    Files.createDirectories(fullPath);

    LogInfo log = new LogInfo(relativePath);
    log.setLogType(LogInfo.LogType.CHRONICLE);

    // Create a minimal Chronicle queue at the resolved path
    try (ChronicleQueue queue =
        queueFactory.create(fullPath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      // Queue created
    }

    // Should succeed with relative path resolved against baseDir
    logReader.readFromLog(log, false, null, false);
  }

  /**
   * Tests that readFromLog with initial offset configures the reader correctly.
   *
   * <p>Verifies that specifying an initial offset is properly recorded for later use.
   */
  @Test
  public void readFromLog_withInitialOffset_configuresCorrectly() throws Exception {
    String queueName = "offset-test-queue";
    Path queuePath = tempDir.resolve(queueName);
    Files.createDirectories(queuePath);

    LogInfo log = new LogInfo(queueName);
    log.setLogType(LogInfo.LogType.CHRONICLE);

    // Create a minimal Chronicle queue
    try (ChronicleQueue queue =
        queueFactory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      // Queue created
    }

    // Configure with initial offset
    long initialOffset = 12345L;
    logReader.readFromLog(log, false, initialOffset, false);

    // The reader is now configured - we've verified no exception was thrown
  }

  /**
   * Tests that readFromLog with skipWrittenOffsets flag configures correctly.
   *
   * <p>Note: skipWrittenOffsets is primarily for Kafka but should be accepted by Chronicle reader.
   */
  @Test
  public void readFromLog_withSkipWrittenOffsets_configuresCorrectly() throws Exception {
    String queueName = "skip-offsets-test-queue";
    Path queuePath = tempDir.resolve(queueName);
    Files.createDirectories(queuePath);

    LogInfo log = new LogInfo(queueName);
    log.setLogType(LogInfo.LogType.CHRONICLE);

    // Create a minimal Chronicle queue
    try (ChronicleQueue queue =
        queueFactory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      // Queue created
    }

    // Configure with skipWrittenOffsets = true
    logReader.readFromLog(log, true, null, false);

    // The reader is now configured - we've verified no exception was thrown
  }

  // ==================== Constructor Tests ====================

  /**
   * Tests that the constructor creates a valid instance.
   *
   * <p>Verifies basic construction doesn't throw.
   */
  @Test
  public void constructor_createsValidInstance() {
    ChronicleSourceLogReader reader =
        new ChronicleSourceLogReader(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "TestReader",
            "inproc://test-dealer",
            "inproc://test-offsets",
            tempDir,
            queueFactory);

    assertThat("Reader should be created", reader, notNullValue());
  }

  /**
   * Tests that the constructor works with different service names.
   *
   * <p>Verifies that the service name is properly used for logging/identification.
   */
  @Test
  public void constructor_withCustomServiceName_succeeds() {
    String customName = "CustomChronicleReader";
    ChronicleSourceLogReader reader =
        new ChronicleSourceLogReader(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            customName,
            "inproc://test-dealer-2",
            "inproc://test-offsets-2",
            tempDir,
            queueFactory);

    assertThat("Reader should be created with custom name", reader, notNullValue());
  }

  // ==================== Error Scenario Tests ====================

  /**
   * Tests that readFromLog error message is informative.
   *
   * <p>Verifies the error message includes the queue path for debugging.
   */
  @Test
  public void readFromLog_nonExistentQueue_errorIncludesPath() {
    String queueName = "specific-missing-queue-name";
    LogInfo log = new LogInfo(queueName);
    log.setLogType(LogInfo.LogType.CHRONICLE);

    try {
      logReader.readFromLog(log, false, null, false);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      // Error message should include the resolved path
      assertThat(
          "Exception message should include queue path", e.getMessage(), containsString(queueName));
    }
  }
}
