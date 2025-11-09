/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Kafka-related CLI functionality.
 *
 * <p>Tests for Kafka log operations in {@code pal print}, {@code pal ls}, {@code pal rm}, and
 * {@code pal call} commands, including:
 *
 * <ul>
 *   <li>Printing Kafka logs with and without PAL_DIRECTORY
 *   <li>Kafka end offset display (last offset, not last+1)
 *   <li>Removing Kafka logs with and without PAL_DIRECTORY
 *   <li>Calling methods that write to Kafka logs
 * </ul>
 */
public class KafkaCliIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(KafkaCliIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private Process peerProcess;

  @After
  public void tearDown() throws Exception {
    if (peerProcess != null && peerProcess.isAlive()) {
      logger.warn("Peer process still running, destroying it");
      peerProcess.destroy();
      peerProcess = null;
    }
  }

  /**
   * Issue #1: Tests that {@code pal print} can print Kafka logs without PAL_DIRECTORY when
   * KAFKA_SERVERS env var is set.
   *
   * <p>This test verifies that the print command can work with just KAFKA_SERVERS for Kafka logs,
   * without requiring a PAL_DIRECTORY connection.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrintKafkaLog_withoutPalDirectory_withKafkaServers() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a Kafka log by running a peer
    String walName = "test-print-kafka-no-dir-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Now try to print the log WITHOUT -d flag (no PAL_DIRECTORY)
    // Pass -k explicitly to provide Kafka servers in direct mode
    CliProcessResult printResult = runPrint("-k", kafkaServers, "-l", walName, "--full");

    assertEquals("Expected successful print without PAL_DIRECTORY", 0, printResult.exitCode());
    assertThat("Expected non-empty output from Kafka log", !printResult.stdout().trim().isEmpty());

    logger.info("Successfully printed Kafka log without PAL_DIRECTORY using -k option");
  }

  /**
   * Issue #7: Tests that {@code pal ls} shows Kafka end offset as the last message offset (not
   * last+1).
   *
   * <p>This test verifies that Kafka logs display the last message offset, not the Kafka-internal
   * "end offset" which is last+1.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListKafkaLog_endOffsetIsLastMessageOffset() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-kafka-offset-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // List logs
    CliProcessResult lsResult = runLs("-d", palDirectory, "-L", "-l", "--no-trim");

    assertEquals("Expected successful ls", 0, lsResult.exitCode());
    assertThat("Expected Kafka log in output", lsResult.stdout(), containsString(walName));

    String output = lsResult.stdout();
    logger.info("Kafka log ls output:\n{}", output);

    // Print the log to count actual messages
    CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName, "--compact");
    assertEquals("Expected successful print", 0, printResult.exitCode());

    long messageCount = printResult.stdout().lines().filter(line -> !line.trim().isEmpty()).count();
    logger.info("Kafka log has {} messages", messageCount);

    // Extract end offset from ls output
    long endOffset = extractEndOffset(output, walName);
    logger.info("Displayed end offset: {}", endOffset);

    // The end offset should be the last message index (0-based)
    // So if there are N messages, the last message is at index N-1
    // The displayed end offset should be N-1, not N
    if (messageCount > 0) {
      assertEquals(
          "End offset should be last message index (not last+1)", messageCount - 1, endOffset);
    }

    logger.info("Successfully verified Kafka end offset is last message offset");
  }

  /**
   * Tests that {@code pal print} can print Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrintKafkaLog_withPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-kafka-registry-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print using PAL_DIRECTORY (Registry Mode)
    CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName, "--full");

    assertEquals("Expected successful print with PAL_DIRECTORY", 0, printResult.exitCode());
    assertThat("Expected non-empty output from Kafka log", !printResult.stdout().trim().isEmpty());

    logger.info("Successfully printed Kafka log with PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal rm} can remove Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveKafkaLog_withPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-remove-kafka-registry-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log exists
    CliProcessResult lsResult = runLs("-d", palDirectory, "-L", "-l", "--no-trim");
    assertThat("Log should exist before removal", lsResult.stdout(), containsString(walName));

    // Remove using PAL_DIRECTORY (Registry Mode)
    CliProcessResult rmResult = runRm("-d", palDirectory, "-L", walName);

    assertEquals("Expected successful removal with PAL_DIRECTORY", 0, rmResult.exitCode());

    // Verify log was removed
    lsResult = runLs("-d", palDirectory, "-L", "-l");
    assertThat(
        "Log should not exist after removal", lsResult.stdout(), not(containsString(walName)));

    logger.info("Successfully removed Kafka log with PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal rm} can remove Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveKafkaLog_withoutPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-remove-kafka-direct-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log exists using PAL_DIRECTORY
    CliProcessResult lsResult = runLs("-d", palDirectory, "-L", "-l", "--no-trim");
    assertThat("Log should exist before removal", lsResult.stdout(), containsString(walName));

    // Remove without PAL_DIRECTORY (Direct Mode)
    CliProcessResult rmResult = runRm("-k", kafkaServers, "-L", walName);

    assertEquals("Expected successful removal without PAL_DIRECTORY", 0, rmResult.exitCode());

    // Note: For Kafka, rm only deletes from registry in direct mode
    // The Kafka topic itself still exists but is unregistered
    logger.info("Successfully removed Kafka log without PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal call} can write to Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCallKafkaLog_withPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-call-kafka-registry-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Launch peer with Kafka log (keep it running)
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            walName,
            "-cp",
            getIttAppsClasspath());

    // Call a method via the log using PAL_DIRECTORY (Registry Mode)
    CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            walName,
            "com.quasient.pal.apps.rpc.Methods",
            "-m",
            "staticStringWithStringArgs",
            "test-call-kafka-registry");

    assertEquals("Expected successful call with PAL_DIRECTORY", 0, callResult.exitCode());
    assertThat(
        "Expected result in output",
        callResult.stdout(),
        containsString("RESULT: test-call-kafka-registry"));

    // Stop the peer
    stopPeer(peerProcess);
    peerProcess = null;

    logger.info("Successfully called to Kafka log with PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal call} can write to Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCallKafkaLog_withoutPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-call-kafka-direct-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Launch peer with Kafka log (keep it running, no PAL_DIRECTORY needed for call)
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            walName,
            "-cp",
            getIttAppsClasspath());

    // Call a method via the log without PAL_DIRECTORY (Direct Mode)
    CliProcessResult callResult =
        runCall(
            "-k",
            kafkaServers,
            "-l",
            walName,
            "com.quasient.pal.apps.rpc.Methods",
            "-m",
            "staticStringWithStringArgs",
            "test-call-kafka-direct");

    assertEquals("Expected successful call without PAL_DIRECTORY", 0, callResult.exitCode());
    assertThat(
        "Expected result in output",
        callResult.stdout(),
        containsString("RESULT: test-call-kafka-direct"));

    // Stop the peer
    stopPeer(peerProcess);
    peerProcess = null;

    logger.info("Successfully called to Kafka log without PAL_DIRECTORY");
  }

  /**
   * Helper method to extract end offset from {@code pal ls -L -l} output.
   *
   * <p>Parses output line containing log name and extracts the end offset value.
   *
   * @param lsOutput the output from {@code pal ls -L -l}
   * @param logName the log name to find
   * @return the end offset
   */
  private long extractEndOffset(String lsOutput, String logName) {
    // Expected format: logName UUID Size Start --> End Created
    // Example: test-log-123 uuid 10.1 KiB 0 --> 209 Nov 05 15:40
    Pattern pattern =
        Pattern.compile(
            Pattern.quote(logName)
                + "\\s+[a-f0-9-]+\\s+[\\d.]+\\s*\\w+\\s+\\d+\\.?\\.?\\s*-->\\s*(\\d+)");
    Matcher matcher = pattern.matcher(lsOutput);
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException(
        "Could not extract end offset from ls output for log: " + logName);
  }
}
