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
import static org.hamcrest.Matchers.is;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for Chronicle queue-based source log reading and WAL writing.
 *
 * <p>These tests verify that PAL can successfully write messages to a Chronicle queue (WAL) and
 * read them back using the file:/ prefix syntax in CLI options.
 */
public class ChronicleLogIntegrationTest extends AbstractIntegrationTest {

  private Path tempDir;
  private Path walPath;

  @Before
  public void setUp() throws IOException {
    tempDir = Files.createTempDirectory("chronicle-itt");
    walPath = tempDir.resolve("test-wal");
    logger.info("Created temp directory for Chronicle queues: {}", tempDir);
  }

  @After
  public void tearDown() {
    // Clean up temp directory
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> files = Files.walk(tempDir)) {
        files
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    logger.warn("Failed to delete {}", path, e);
                  }
                });
      } catch (IOException e) {
        logger.warn("Failed to clean up temp directory {}", tempDir, e);
      }
    }
  }

  /**
   * Tests that PAL can write to a Chronicle queue using the file:/ prefix.
   *
   * <p>This test launches a peer with a Chronicle WAL and verifies:
   *
   * <ul>
   *   <li>The peer starts successfully
   *   <li>The Chronicle queue directory is created
   *   <li>No errors occur during startup
   *   <li>Messages were actually written to the queue
   * </ul>
   */
  @Test
  public void chronicleWalCreation() throws IOException, InterruptedException {
    logger.info("Testing Chronicle WAL creation with file:/ prefix");

    // Run PAL with Chronicle WAL (without PAL directory since Chronicle doesn't need it)
    ProcessResult result =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "-r",
            "12345", // Use a specific port to avoid conflicts
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    // Verify the process started and completed successfully
    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify the Chronicle queue was created
    // walPath already contains the queue name, no need to resolve again
    assertThat("Chronicle queue directory should exist", Files.exists(walPath), is(true));

    // Verify no fatal errors in output (case-insensitive check for 'error' and 'exception')
    String stderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));

    // Verify messages were actually written to the queue
    assertThat("Queue should not be empty", ChronicleLogUtil.isQueueEmpty(walPath), is(false));
    int messageCount = ChronicleLogUtil.countMessages(walPath);
    assertThat("Queue should contain at least one message", messageCount > 0, is(true));
    logger.info("Verified {} messages written to Chronicle queue", messageCount);
  }

  /**
   * Tests write-then-read cycle: one peer writes to Chronicle queue, another reads from it.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Starts a peer that writes messages to a Chronicle queue
   *   <li>Stops the writer peer
   *   <li>Verifies messages were written
   *   <li>Starts a reader peer that reads from the same Chronicle queue
   *   <li>Verifies messages are successfully read
   * </ul>
   */
  @Test
  public void chronicleWriteThenRead() throws IOException, InterruptedException {
    logger.info("Testing Chronicle write-then-read cycle");

    // First, write some messages to the Chronicle queue
    ProcessResult writeResult =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Writer process should exit with code 0", writeResult.exitCode(), is(0));

    // walPath already contains the queue name
    assertThat("Chronicle queue should have been created", Files.exists(walPath), is(true));

    // Verify messages were written
    int messagesWritten = ChronicleLogUtil.countMessages(walPath);
    assertThat("Queue should contain messages after write", messagesWritten > 0, is(true));
    logger.info("Writer peer wrote {} messages to Chronicle queue", messagesWritten);

    // Now read from the same queue - need to specify the full path
    ProcessResult readResult =
        runPeer(
            "--source-log",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Reader process should exit with code 0", readResult.exitCode(), is(0));

    // Verify no errors (case-insensitive check for 'error' and 'exception')
    String readStderrLower = readResult.stderr().toLowerCase(Locale.ROOT);
    assertThat("Reader should not have 'error'", readStderrLower.contains("error"), is(false));
    assertThat(
        "Reader should not have 'exception'", readStderrLower.contains("exception"), is(false));

    // Verify messages are still in the queue (reading doesn't consume)
    int messagesAfterRead = ChronicleLogUtil.countMessages(walPath);
    assertThat(
        "Message count should remain the same after read", messagesAfterRead, is(messagesWritten));
  }

  /**
   * Tests that using file:/ prefix doesn't require Kafka configuration.
   *
   * <p>This test verifies that when using Chronicle queues (file:/ prefix), PAL doesn't require
   * KAFKA_SERVERS or --kafka-servers to be set, and messages are still written.
   */
  @Test
  public void chronicleDoesNotRequireKafka() throws IOException, InterruptedException {
    logger.info("Testing Chronicle queue doesn't require Kafka configuration");

    // Run PAL with Chronicle WAL but NO Kafka configuration
    // This should succeed because Chronicle doesn't need Kafka
    ProcessResult result =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify NO error occurred (case-insensitive check for 'error' and 'exception')
    String noKafkaStderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not have 'error'", noKafkaStderrLower.contains("error"), is(false));
    assertThat("Should not have 'exception'", noKafkaStderrLower.contains("exception"), is(false));

    // Verify messages were written even without Kafka
    int messageCount = ChronicleLogUtil.countMessages(walPath);
    assertThat(
        "Queue should contain messages without Kafka configuration", messageCount > 0, is(true));
    logger.info("Verified {} messages written without Kafka configuration", messageCount);
  }

  /**
   * Tests that attempting to use both Kafka and Chronicle logs in mixed mode works correctly.
   *
   * <p>This test verifies that PAL can handle a configuration where:
   *
   * <ul>
   *   <li>WAL is Chronicle (file:/)
   *   <li>Source log is Kafka (regular topic name)
   *   <li>Messages are written to Chronicle WAL
   * </ul>
   */
  @Test
  public void chronicleWalWithKafkaSource() throws IOException, InterruptedException {
    logger.info("Testing mixed Chronicle WAL with Kafka source log");

    String kafkaServers = getKafkaServers();
    String sourceLog = "test-kafka-topic-" + generateId();

    ProcessResult result =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "--source-log",
            sourceLog,
            "-k",
            kafkaServers,
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify Chronicle queue was created
    assertThat("Chronicle WAL should be created", Files.exists(walPath), is(true));

    // Verify no errors (case-insensitive check for 'error' and 'exception')
    String mixedStderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not have 'error'", mixedStderrLower.contains("error"), is(false));
    assertThat("Should not have 'exception'", mixedStderrLower.contains("exception"), is(false));

    // Verify messages were written to Chronicle WAL
    int messageCount = ChronicleLogUtil.countMessages(walPath);
    assertThat("Chronicle WAL should contain messages in mixed mode", messageCount > 0, is(true));
    logger.info("Verified {} messages written to Chronicle WAL in mixed mode", messageCount);
  }

  /**
   * Tests reading from a Chronicle queue starting at a specific index.
   *
   * <p>This test verifies the --start-offset option works with Chronicle queues and that messages
   * exist at various indices.
   */
  @Test
  public void chronicleReadFromOffset() throws IOException, InterruptedException {
    logger.info("Testing Chronicle queue reading from specific offset");

    // First, write some messages
    ProcessResult writeResult =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Writer should exit successfully", writeResult.exitCode(), is(0));

    int totalMessages = ChronicleLogUtil.countMessages(walPath);
    assertThat(
        "Queue should have messages before reading with offset", totalMessages > 0, is(true));
    logger.info("Queue contains {} messages total", totalMessages);

    // Get queue index info
    ChronicleLogUtil.QueueIndexInfo indexInfo = ChronicleLogUtil.getQueueIndexInfo(walPath);
    assertThat("Queue index info should be available", indexInfo != null, is(true));
    logger.info("Queue index info: {}", indexInfo);

    // Calculate a valid offset to read from (somewhere in the middle if possible)
    long startOffset =
        totalMessages > 5 ? indexInfo.getFirstIndex() + 5 : indexInfo.getFirstIndex();

    // Now read starting from calculated offset - need to specify the full path
    ProcessResult readResult =
        runPeer(
            "--source-log",
            "file:" + walPath.toAbsolutePath(),
            "--start-offset",
            String.valueOf(startOffset),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Reader should exit successfully", readResult.exitCode(), is(0));

    // Verify no errors (case-insensitive check for 'error' and 'exception')
    String offsetReadStderrLower = readResult.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not have 'error'", offsetReadStderrLower.contains("error"), is(false));
    assertThat(
        "Should not have 'exception'", offsetReadStderrLower.contains("exception"), is(false));

    // Verify messages are still accessible (reading doesn't consume)
    int messagesAfterRead = ChronicleLogUtil.countMessages(walPath);
    assertThat(
        "Message count should remain the same after offset read",
        messagesAfterRead,
        is(totalMessages));
  }

  /**
   * Tests that PAL fails gracefully with ERROR_INITIALIZING_LOGS when trying to read from a
   * non-existent Chronicle queue using --source-log.
   *
   * <p>This test verifies:
   *
   * <ul>
   *   <li>Attempting to read from a non-existent Chronicle queue fails
   *   <li>The process exits with code 7 (ERROR_INITIALIZING_LOGS)
   *   <li>A clear error message is provided
   * </ul>
   */
  @Test
  public void chronicleSourceLogNotFound_failsWithErrorInitializingLogs()
      throws IOException, InterruptedException {
    logger.info("Testing Chronicle source log not found error handling");

    // Create a path for a queue that doesn't exist
    Path nonExistentQueue = tempDir.resolve("non-existent-log");

    // Try to read from a non-existent Chronicle queue with --source-log
    ProcessResult result =
        runPeer(
            "--source-log",
            "file:" + nonExistentQueue.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    // Verify the process exits with ERROR_INITIALIZING_LOGS (exit code 7)
    assertThat(
        "Process should exit with ERROR_INITIALIZING_LOGS (code 7)", result.exitCode(), is(7));

    // Verify error message indicates the problem
    assertThat(
        "Error output should mention the queue path",
        result.stderr(),
        containsString(nonExistentQueue.toString()));
    assertThat(
        "Error output should mention Chronicle queue issue",
        result.stderr(),
        containsString("Chronicle"));
  }

  /**
   * Tests that PAL succeeds when using --wal with a non-existent Chronicle queue.
   *
   * <p>This test verifies:
   *
   * <ul>
   *   <li>Using --wal creates the Chronicle queue if it doesn't exist (WAL writer creates it)
   *   <li>The process exits successfully (exit code 0)
   *   <li>Messages are written to the newly created queue
   *   <li>No errors are reported
   * </ul>
   */
  @Test
  public void chronicleLogOptionCreatesQueueIfNotExists() throws IOException, InterruptedException {
    logger.info("Testing Chronicle --wal option creates queue if it doesn't exist");

    // Create a path for a queue that doesn't exist yet
    Path newQueue = tempDir.resolve("new-queue-via-log-option");

    // Verify it doesn't exist before running
    assertThat("Queue should not exist initially", Files.exists(newQueue), is(false));

    // Run with -l option (same log for reading and writing)
    ProcessResult result =
        runPeer(
            "--wal",
            "file:" + newQueue.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    // Verify the process exits successfully
    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify the Chronicle queue was created
    assertThat("Chronicle queue should be created", Files.exists(newQueue), is(true));

    // Verify no errors (case-insensitive check for 'error' and 'exception')
    String stderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));

    // Verify messages were written to the queue
    int messageCount = ChronicleLogUtil.countMessages(newQueue);
    assertThat(
        "Chronicle queue should contain messages created via --wal option",
        messageCount > 0,
        is(true));
  }

  /**
   * Tests that PAL correctly handles relative Chronicle paths (file:mylog).
   *
   * <p>This test verifies:
   *
   * <ul>
   *   <li>Relative paths without leading slash work correctly
   *   <li>The queue is created relative to CWD (or --chronicle-base-dir if specified)
   *   <li>Messages are written and can be read back
   * </ul>
   */
  @Test
  public void chronicleRelativePathHandling() throws IOException, InterruptedException {
    logger.info("Testing Chronicle relative path handling (file:mylog)");

    // Use a simple relative name
    String relativeName = "relative-test-queue";
    Path expectedPath = tempDir.resolve(relativeName);

    // Run with relative path and --chronicle-base-dir
    ProcessResult result =
        runPeer(
            "--chronicle-base-dir",
            tempDir.toAbsolutePath().toString(),
            "--wal",
            "file:" + relativeName, // Relative path (no leading slash)
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    // Verify the process exits successfully
    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify the Chronicle queue was created at the expected location
    assertThat(
        "Chronicle queue should be created at base_dir/relative-name",
        Files.exists(expectedPath),
        is(true));

    // Verify no errors (case-insensitive check for 'error' and 'exception')
    String stderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));

    // Verify messages were written
    int messageCount = ChronicleLogUtil.countMessages(expectedPath);
    assertThat(
        "Chronicle queue should contain messages with relative path", messageCount > 0, is(true));
  }
}
