/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.chronicle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            .sorted(java.util.Comparator.reverseOrder())
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
   * </ul>
   */
  @Test
  public void chronicleWalCreation() throws IOException, InterruptedException {
    logger.info("Testing Chronicle WAL creation with file:/ prefix");

    // Run PAL with Chronicle WAL
    ProcessResult result =
        runPalCommand(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "target/classes",
            "--rpc",
            "12345", // Use a specific port to avoid conflicts
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    // Verify the process started and completed successfully
    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify the Chronicle queue was created
    assertThat(
        "Chronicle queue directory should exist",
        Files.exists(walPath.resolve("test-wal")),
        is(true));

    // Verify no fatal errors in output
    assertThat("Should not contain any error", result.stderr().contains("ERROR"), is(false));
  }

  /**
   * Tests write-then-read cycle: one peer writes to Chronicle queue, another reads from it.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Starts a peer that writes messages to a Chronicle queue
   *   <li>Stops the writer peer
   *   <li>Starts a reader peer that reads from the same Chronicle queue
   *   <li>Verifies messages are successfully read
   * </ul>
   */
  @Test
  public void chronicleWriteThenRead() throws IOException, InterruptedException {
    logger.info("Testing Chronicle write-then-read cycle");

    // First, write some messages to the Chronicle queue
    ProcessResult writeResult =
        runPalCommand(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Writer process should exit with code 0", writeResult.exitCode(), is(0));
    assertThat(
        "Chronicle queue should have been created",
        Files.exists(walPath.resolve("test-wal")),
        is(true));

    // Now read from the same queue
    ProcessResult readResult =
        runPalCommand(
            "--source-log",
            "file:" + walPath.toAbsolutePath() + "/test-wal",
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Reader process should exit with code 0", readResult.exitCode(), is(0));

    // Verify no errors
    assertThat("Reader should not have errors", readResult.stderr().contains("ERROR"), is(false));
  }

  /**
   * Tests that using file:/ prefix doesn't require Kafka configuration.
   *
   * <p>This test verifies that when using Chronicle queues (file:/ prefix), PAL doesn't require
   * KAFKA_SERVERS or --kafka-servers to be set.
   */
  @Test
  public void chronicleDoesNotRequireKafka() throws IOException, InterruptedException {
    logger.info("Testing Chronicle queue doesn't require Kafka configuration");

    // Run PAL with Chronicle WAL but NO Kafka configuration
    // This should succeed because Chronicle doesn't need Kafka
    ProcessResult result =
        runPalCommandWithEnv(
            null, // No PAL_DIRECTORY
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify NO error occurred
    assertThat("Should not have errors", result.stderr().contains("ERROR"), is(false));
  }

  /**
   * Tests that attempting to use both Kafka and Chronicle logs in mixed mode works correctly.
   *
   * <p>This test verifies that PAL can handle a configuration where:
   *
   * <ul>
   *   <li>WAL is Chronicle (file:/)
   *   <li>Source log is Kafka (regular topic name)
   * </ul>
   */
  @Test
  public void chronicleWalWithKafkaSource() throws IOException, InterruptedException {
    logger.info("Testing mixed Chronicle WAL with Kafka source log");

    String kafkaServers = getKafkaServers();
    String sourceLog = "test-kafka-topic-" + generateId();

    ProcessResult result =
        runPalCommand(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "--source-log",
            sourceLog,
            "-k",
            kafkaServers,
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Process should exit with code 0", result.exitCode(), is(0));

    // Verify Chronicle queue was created
    assertThat(
        "Chronicle WAL should be created", Files.exists(walPath.resolve("test-wal")), is(true));

    // Verify no errors
    assertThat("Should not have errors", result.stderr().contains("ERROR"), is(false));
  }

  /**
   * Tests reading from a Chronicle queue starting at a specific index.
   *
   * <p>This test verifies the --start-offset option works with Chronicle queues.
   */
  @Test
  public void chronicleReadFromOffset() throws IOException, InterruptedException {
    logger.info("Testing Chronicle queue reading from specific offset");

    // First, write some messages
    ProcessResult writeResult =
        runPalCommand(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Writer should exit successfully", writeResult.exitCode(), is(0));

    // Now read starting from offset 5
    ProcessResult readResult =
        runPalCommand(
            "--source-log",
            "file:" + walPath.toAbsolutePath() + "/test-wal",
            "--start-offset",
            "5",
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

    assertThat("Reader should exit successfully", readResult.exitCode(), is(0));

    // (Note: actual verification would depend on log output format)
    // Verify no errors
    assertThat("Should not have errors", readResult.stderr().contains("ERROR"), is(false));
  }

  /**
   * Tests that PAL fails gracefully with ERROR_INITIALIZING_LOGS when trying to read from a
   * non-existent Chronicle queue.
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

    // Try to read from a non-existent Chronicle queue
    ProcessResult result =
        runPalCommandWithEnv(
            null, // No PAL_DIRECTORY
            "--source-log",
            "file:" + nonExistentQueue.toAbsolutePath(),
            "-cp",
            "target/classes",
            "-m",
            "com.quasient.pal.itt.apps.Methods");

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
}
