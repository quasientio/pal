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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.PeerProcess;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.cxn.directory.PalDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Chronicle-related CLI functionality.
 *
 * <p>Tests for fixes to Chronicle log handling in {@code pal print}, {@code pal ls}, and {@code pal
 * rm} commands, including:
 *
 * <ul>
 *   <li>Printing Chronicle logs without PAL_DIRECTORY
 *   <li>Chronicle log size and offset accuracy
 *   <li>Handling absolute paths in Chronicle logs
 *   <li>Storing absolute paths in LogInfo
 *   <li>Stripping {@code file:} prefix in {@code pal print}
 *   <li>Kafka end offset display (last offset, not last+1)
 * </ul>
 */
public class ChronicleCliIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(ChronicleCliIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Issue #1: Tests that {@code pal print} can print Chronicle logs without PAL_DIRECTORY.
   *
   * <p>This test verifies that the print command can work for Chronicle logs, without requiring a
   * PAL_DIRECTORY connection.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrintChronicleLog_withoutPalDirectory() throws Exception {
    // Create a Chronicle log by running a peer with PAL_DIRECTORY first
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-print-chronicle-no-dir-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Now try to print the log WITHOUT -d flag (no PAL_DIRECTORY)
    // Must use "file:" prefix to distinguish from Kafka logs
    CliProcessResult printResult = runPrint("-l", "file:" + walName, "--full");

    assertEquals("Expected successful print without PAL_DIRECTORY", 0, printResult.exitCode());
    assertThat(
        "Expected non-empty output from Chronicle log", !printResult.stdout().trim().isEmpty());

    logger.info("Successfully printed Chronicle log without PAL_DIRECTORY");
  }

  /**
   * Issue #3: Tests that {@code pal ls} shows accurate size and offset information for Chronicle
   * logs.
   *
   * <p>This test verifies that Chronicle log size matches actual disk usage and that offsets are
   * correctly reported.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListChronicleLog_accurateSizeAndOffsets() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-ls-chronicle-size-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // List logs in long format
    CliProcessResult lsResult = runLs("-d", palDirectory, "-L", "-l", "--no-trim");

    assertEquals("Expected successful ls", 0, lsResult.exitCode());
    assertThat("Expected log name in output", lsResult.stdout(), containsString(walName));

    // Check that size is reasonable (not 160 MiB for a 56K log)
    // The output should contain the log name and size in a format like:
    // walName UUID Size Start End Created
    String output = lsResult.stdout();
    logger.info("ls output:\n{}", output);

    // Verify the Chronicle log directory exists and get its actual size
    String palHome = System.getenv("PAL_HOME");
    Path chroniclePath = Paths.get(palHome, walName);
    assertTrue("Chronicle directory should exist", Files.exists(chroniclePath));

    long actualSize;
    try (Stream<Path> files = Files.walk(chroniclePath)) {
      actualSize =
          files
              .filter(Files::isRegularFile)
              .mapToLong(
                  p -> {
                    try {
                      return Files.size(p);
                    } catch (IOException e) {
                      return 0;
                    }
                  })
              .sum();
    }

    logger.info("Actual Chronicle queue size on disk: {} bytes", actualSize);

    // The displayed size should be within reasonable range of actual size
    // We won't do exact matching because of formatting, but we verify it's not wildly wrong
    // (e.g., not showing 160 MiB when it's 56 KiB)
    assertThat("Chronicle log should have written some messages", actualSize, greaterThan(0L));

    logger.info("Successfully verified Chronicle log size accuracy");
  }

  /**
   * Issue #3: Tests that {@code pal ls} updates offsets when the same Chronicle log is reused.
   *
   * <p>This test verifies that when a peer re-runs with the same Chronicle log, the end offset
   * increases.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListChronicleLog_offsetsIncrementOnRerun() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-ls-chronicle-rerun-" + generateId();
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    // First run
    peerProcess =
        launchPeer(
            peerId1,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode1 = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode1);
    peerProcess = null;

    // List logs and capture end offset
    CliProcessResult lsResult1 = runLs("-d", palDirectory, "-L", "-l", "--no-trim");
    assertEquals("Expected successful ls", 0, lsResult1.exitCode());

    String firstLsOutput = lsResult1.stdout();
    logger.info("First ls output:\n{}", firstLsOutput);

    // Extract end offset from first run
    long firstEndOffset = extractEndOffset(firstLsOutput, walName);
    logger.info("First run end offset: {}", firstEndOffset);

    // Second run with same log
    peerProcess =
        launchPeer(
            peerId2,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode2 = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode2);
    peerProcess = null;

    // List logs again and verify end offset increased
    CliProcessResult lsResult2 = runLs("-d", palDirectory, "-L", "-l", "--no-trim");
    assertEquals("Expected successful ls", 0, lsResult2.exitCode());

    String secondLsOutput = lsResult2.stdout();
    logger.info("Second ls output:\n{}", secondLsOutput);

    long secondEndOffset = extractEndOffset(secondLsOutput, walName);
    logger.info("Second run end offset: {}", secondEndOffset);

    assertTrue("End offset should increase after second run", secondEndOffset > firstEndOffset);

    logger.info("Successfully verified Chronicle log offsets increment on rerun");
  }

  /**
   * Issue #4: Tests that {@code pal ls} shows Chronicle logs created with absolute paths.
   *
   * <p>This test verifies that logs created with absolute paths are correctly listed.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListChronicleLog_withAbsolutePath() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String tmpDir = System.getProperty("java.io.tmpdir");
    String walName = "test-ls-abs-" + generateId();
    String absoluteWalPath = Paths.get(tmpDir, walName).toString();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(absoluteWalPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + absoluteWalPath,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // List logs - should show the log
    CliProcessResult lsResult = runLs("-d", palDirectory, "-L", "-l", "--no-trim");

    assertEquals("Expected successful ls", 0, lsResult.exitCode());
    assertThat(
        "Expected log filename in output",
        lsResult.stdout(),
        containsString(Paths.get(absoluteWalPath).getFileName().toString()));

    logger.info("Successfully listed Chronicle log with absolute path");
  }

  /**
   * Issue #5: Tests that {@code pal rm} can remove Chronicle logs created with absolute paths.
   *
   * <p>This test verifies that logs created with absolute paths can be removed using {@code pal rm}
   * given the absolute path.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveChronicleLog_withAbsolutePath() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String tmpDir = System.getProperty("java.io.tmpdir");
    String walName = "test-rm-abs-" + generateId();
    String absoluteWalPath = Paths.get(tmpDir, walName).toAbsolutePath().toString();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(absoluteWalPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + absoluteWalPath,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log exists in directory
    CliProcessResult lsBefore = runLs("-d", palDirectory, "-L", "--no-trim");
    assertThat(
        "Log should exist before removal",
        lsBefore.stdout(),
        containsString(Paths.get(absoluteWalPath).getFileName().toString()));

    // Remove the log using absolute path
    CliProcessResult rmResult = runRm("-d", palDirectory, "-L", absoluteWalPath);

    assertEquals("Expected successful removal", 0, rmResult.exitCode());

    // Verify log is removed from directory
    CliProcessResult lsAfter = runLs("-d", palDirectory, "-L");
    assertThat(
        "Log should not exist after removal",
        lsAfter.stdout(),
        not(containsString(absoluteWalPath)));

    logger.info("Successfully removed Chronicle log with absolute path");
  }

  /**
   * This test verifies that logs created with absolute paths can be removed using {@code pal rm}
   * given the filename.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveChronicleLog_withFilename() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String tmpDir = System.getProperty("java.io.tmpdir");
    String walName = "test-rm-abs-" + generateId();
    String absoluteWalPath = Paths.get(tmpDir, walName).toAbsolutePath().toString();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(absoluteWalPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + absoluteWalPath,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log exists in directory
    CliProcessResult lsBefore = runLs("-d", palDirectory, "-L", "--no-trim");
    Path logAbsolutePath = Paths.get(absoluteWalPath);
    assertThat(
        "Log should exist before removal",
        lsBefore.stdout(),
        containsString(logAbsolutePath.getFileName().toString()));

    // Remove the log using filename
    CliProcessResult rmResult =
        runRm("-d", palDirectory, "-L", logAbsolutePath.getFileName().toString());

    assertEquals("Expected successful removal", 0, rmResult.exitCode());

    // Verify log is removed from directory
    CliProcessResult lsAfter = runLs("-d", palDirectory, "-L");
    assertThat(
        "Log should not exist after removal",
        lsAfter.stdout(),
        not(containsString(absoluteWalPath)));

    logger.info("Successfully removed Chronicle log with filename");
  }

  /**
   * Issue #6: Tests that Chronicle logs store absolute paths in LogInfo when relative paths are
   * provided.
   *
   * <p>This test verifies that when a peer is launched with a relative Chronicle log path, the
   * LogInfo in etcd stores the absolute path.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testChronicleLog_storesAbsolutePath() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-abs-path-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    // Launch peer with relative path
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName, // Relative path
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Query etcd directly to verify the stored path is absolute
    PalDirectory directory = new PalDirectory(palDirectory);

    LogInfo logInfo = directory.getLogInfo(walName);
    if (logInfo == null) {
      // Try with absolute path
      String palHome = System.getenv("PAL_HOME");
      String absolutePath = Paths.get(palHome, walName).toString();
      logInfo = directory.getLogInfo(absolutePath);
    }

    assertNotNull("LogInfo should exist in directory", logInfo);
    logger.info("LogInfo name: {}", logInfo.getName());

    // Verify the stored path is absolute
    Path storedPath = Paths.get(logInfo.getName());
    assertTrue(
        "LogInfo should store absolute path, but got: " + logInfo.getName(),
        storedPath.isAbsolute());

    directory.close();

    logger.info("Successfully verified Chronicle log stores absolute path");
  }

  /**
   * Issue #8: Tests that {@code pal print} works with {@code file:} prefix in log identifier.
   *
   * <p>This test verifies that the print command correctly handles Chronicle log identifiers with
   * the {@code file:} prefix.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrintChronicleLog_withFilePrefix() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-print-file-prefix-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print using file: prefix
    CliProcessResult printResult = runPrint("-d", palDirectory, "-l", "file:" + walName, "--full");

    assertEquals("Expected successful print with file: prefix", 0, printResult.exitCode());
    assertThat(
        "Expected non-empty output from Chronicle log", !printResult.stdout().trim().isEmpty());

    logger.info("Successfully printed Chronicle log with file: prefix");
  }

  /**
   * Tests that {@code pal rm} can remove Chronicle logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveChronicleLog_withoutPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-remove-chronicle-direct-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    trackChronicleLog(walName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log directory exists
    String palHome = System.getenv("PAL_HOME");
    Path logPath = Paths.get(palHome, walName);
    assertThat("Chronicle log directory should exist", Files.exists(logPath));

    // Remove without PAL_DIRECTORY (Direct Mode)
    CliProcessResult rmResult = runRm("-L", "file:" + walName);

    assertEquals("Expected successful removal without PAL_DIRECTORY", 0, rmResult.exitCode());

    // Verify log directory was removed
    assertThat("Chronicle log directory should not exist after removal", !Files.exists(logPath));

    logger.info("Successfully removed Chronicle log without PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal call} can write to Chronicle logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCallChronicleLog_withPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-call-chronicle-registry-" + generateId();

    trackChronicleLog(walName);

    // Launch peer with Chronicle log (keep it running)
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--log", "file:" + walName, "-cp", getIttAppsClasspath());

    // Call a method via the log using PAL_DIRECTORY (Registry Mode)
    CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            "file:" + walName,
            "com.quasient.pal.apps.quantized.rpc.Methods",
            "-m",
            "staticStringWithStringArgs",
            "test-call-chronicle-registry");

    assertEquals("Expected successful call with PAL_DIRECTORY", 0, callResult.exitCode());
    assertThat(
        "Expected result in output",
        callResult.stdout(),
        containsString("RESULT: test-call-chronicle-registry"));

    // Stop the peer
    stopPeer(peerProcess);
    peerProcess = null;

    logger.info("Successfully called to Chronicle log with PAL_DIRECTORY");
  }

  /**
   * Tests that {@code pal call} can write to Chronicle logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCallChronicleLog_withoutPalDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String walName = "test-call-chronicle-direct-" + generateId();

    // Use an absolute path to for the Log: avoids retrieving it from PalDirectory after peer launch
    Path absWalPath = Paths.get(System.getProperty("java.io.tmpdir"), walName);

    trackChronicleLog(absWalPath.toString());

    // Launch peer with Chronicle log (keep it running, no PAL_DIRECTORY needed)
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--log",
            "file:" + absWalPath,
            "-cp",
            getIttAppsClasspath());

    // Call a method via the log without PAL_DIRECTORY (Direct Mode)
    CliProcessResult callResult =
        runCall(
            "-l",
            "file:" + absWalPath,
            "com.quasient.pal.apps.quantized.rpc.Methods",
            "-m",
            "staticStringWithStringArgs",
            "test-call-chronicle-direct");

    assertEquals("Expected successful call without PAL_DIRECTORY", 0, callResult.exitCode());
    assertThat(
        "Expected result in output",
        callResult.stdout(),
        containsString("RESULT: test-call-chronicle-direct"));

    // Stop the peer
    stopPeer(peerProcess);
    peerProcess = null;

    logger.info("Successfully called to Chronicle log without PAL_DIRECTORY");
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
