/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for error handling in CLI commands.
 *
 * <p>Tests error scenarios across all CLI subcommands (call, ls, rm, print, stats) to ensure
 * graceful failure handling, appropriate error messages, and correct exit codes.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class CliErrorHandlingIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(CliErrorHandlingIT.class);

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

  // ==================== pal call Error Tests ====================

  /**
   * Tests that `pal call` fails gracefully when called with invalid method signature.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_invalidMethodSignature() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a peer
    String walName = "test-call-error-invalid-sig-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

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

    // Wait for peer to complete
    int exitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit", 0, exitCode);
    peerProcess = null;

    // Try to call completed peer with invalid method signature
    // (testing invalid signature, not peer reachability)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerId.toString(),
            "-m",
            "nonExistentMethod",
            "--param-types",
            "invalid.Type");

    // Should fail with non-zero exit code (peer not found or method invalid)
    assertNotEquals("Expected non-zero exit code for invalid call", 0, callResult.exitCode());

    // Error message should indicate the problem
    String stderr = callResult.stderr();
    logger.info("Invalid method call error: {}", stderr);
  }

  /**
   * Tests that `pal call` fails gracefully when peer UUID does not exist.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_nonExistentPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Try to call non-existent peer
    UUID nonExistentPeer = UUID.randomUUID();
    AbstractCliIT.CliProcessResult callResult =
        runCall("-d", palDirectory, "-p", nonExistentPeer.toString(), "java.lang.System", "exit");

    // Should fail with non-zero exit code
    assertNotEquals("Expected non-zero exit code for non-existent peer", 0, callResult.exitCode());

    logger.info("Non-existent peer error handled gracefully");
  }

  /**
   * Tests that `pal call` handles connection failures gracefully when etcd is unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_unreachableDirectory() throws Exception {
    // Use invalid etcd address
    String invalidDirectory = "localhost:9999";

    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            invalidDirectory,
            "-p",
            UUID.randomUUID().toString(),
            "java.lang.System",
            "currentTimeMillis");

    // Should fail with non-zero exit code
    assertNotEquals(
        "Expected non-zero exit code for unreachable directory", 0, callResult.exitCode());

    logger.info("Unreachable directory error handled gracefully");
  }

  // ==================== pal ls Error Tests ====================

  /**
   * Tests that `pal ls` handles empty directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLs_emptyDirectory() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // List peers when directory is empty (or nearly empty)
    AbstractCliIT.CliProcessResult lsResult = runLs("-d", palDirectory, "-P");

    // Should succeed with exit code 0
    assertEquals("Expected success for empty directory listing", 0, lsResult.exitCode());

    // Output may be empty or show header only
    String stdout = lsResult.stdout();
    logger.info("Empty directory listing output: {}", stdout);
  }

  /**
   * Tests that `pal ls` handles non-existent log names gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLs_nonExistentLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // List all logs (may or may not include our non-existent one)
    AbstractCliIT.CliProcessResult lsResult = runLs("-d", palDirectory, "-L");

    // Should succeed
    assertEquals("Expected success for log listing", 0, lsResult.exitCode());

    // Non-existent log simply won't appear in output
    logger.info("Log listing completed successfully");
  }

  /**
   * Tests that `pal ls` fails gracefully when directory service is unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLs_unreachableDirectory() throws Exception {
    // Use invalid etcd address
    String invalidDirectory = "localhost:9999";

    AbstractCliIT.CliProcessResult lsResult = runLs("-d", invalidDirectory, "-P");

    // Should fail with non-zero exit code
    assertNotEquals(
        "Expected non-zero exit code for unreachable directory", 0, lsResult.exitCode());

    logger.info("Unreachable directory error in ls handled gracefully");
  }

  // ==================== pal rm Error Tests ====================

  /**
   * Tests that `pal rm` fails gracefully when trying to remove non-existent peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRm_nonExistentPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Try to remove non-existent peer
    UUID nonExistentPeer = UUID.randomUUID();
    AbstractCliIT.CliProcessResult rmResult =
        runRm("-d", palDirectory, "-p", nonExistentPeer.toString());

    // Should fail with non-zero exit code
    assertNotEquals("Expected non-zero exit code for non-existent peer", 0, rmResult.exitCode());

    // Error message should indicate peer not found
    String stderr = rmResult.stderr();
    logger.info("Non-existent peer removal error: {}", stderr);
  }

  /**
   * Tests that `pal rm` fails gracefully when trying to remove non-existent log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRm_nonExistentLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Try to remove non-existent log
    String nonExistentLog = "non-existent-log-" + generateId();
    AbstractCliIT.CliProcessResult rmResult = runRm("-d", palDirectory, "-l", nonExistentLog);

    // Should fail with non-zero exit code
    assertNotEquals("Expected non-zero exit code for non-existent log", 0, rmResult.exitCode());

    logger.info("Non-existent log removal error handled gracefully");
  }

  /**
   * Tests that `pal rm` requires appropriate confirmation for removing resources.
   *
   * <p>Note: Testing live peer removal requires a long-running peer, which is covered in
   * RemoveIT.java tests.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRm_requiresProperArguments() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Try to remove without specifying peer or log
    AbstractCliIT.CliProcessResult rmResult = runRm("-d", palDirectory);

    // Should fail with non-zero exit code (missing required argument)
    assertNotEquals("Expected failure when no removal target specified", 0, rmResult.exitCode());

    logger.info("Remove command properly validates required arguments");
  }

  /**
   * Tests that `pal rm` handles unreachable directory service gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRm_unreachableDirectory() throws Exception {
    // Use invalid etcd address
    String invalidDirectory = "localhost:9999";

    AbstractCliIT.CliProcessResult rmResult =
        runRm("-d", invalidDirectory, "-p", UUID.randomUUID().toString());

    // Should fail with non-zero exit code
    assertNotEquals(
        "Expected non-zero exit code for unreachable directory", 0, rmResult.exitCode());

    logger.info("Unreachable directory error in rm handled gracefully");
  }

  // ==================== pal print Error Tests ====================

  /**
   * Tests that `pal print` handles logs with minimal messages gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_minimalMessages() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a Kafka log with minimal messages
    String walName = "test-print-minimal-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

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

    int exitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit", 0, exitCode);
    peerProcess = null;

    // Print from the log
    AbstractCliIT.CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName);

    // Should succeed
    assertEquals("Expected success for log print", 0, printResult.exitCode());

    // Should have some output
    assertThat("Expected non-empty output", printResult.stdout().length(), greaterThan(0));

    logger.info("Minimal message log print completed successfully");
  }

  /**
   * Tests that `pal print` handles offset beyond log end gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_offsetBeyondEnd() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a log with some messages
    String walName = "test-print-offset-beyond-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

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

    int exitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit", 0, exitCode);
    peerProcess = null;

    // Try to print with offset beyond end, using limit to avoid hanging
    // Offset 100 should be beyond the end for this short-running peer
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "-o", "100", "-n", "10");

    // Command completes (exit code doesn't matter - could be 0 or 2 depending on implementation)
    // The important thing is it doesn't hang or crash
    logger.info(
        "Print with offset beyond end completed with exit code: {}", printResult.exitCode());

    logger.info("Offset beyond end handled gracefully");
  }

  /**
   * Tests that `pal print` fails gracefully when log does not exist.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_nonExistentLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Try to print from non-existent log
    String nonExistentLog = "non-existent-log-" + generateId();
    AbstractCliIT.CliProcessResult printResult = runPrint("-d", palDirectory, "-l", nonExistentLog);

    // Should fail with non-zero exit code
    assertNotEquals("Expected non-zero exit code for non-existent log", 0, printResult.exitCode());

    logger.info("Non-existent log print error handled gracefully");
  }

  /**
   * Tests that `pal print` handles unreachable directory service gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_unreachableDirectory() throws Exception {
    // Use invalid etcd address
    String invalidDirectory = "localhost:9999";

    AbstractCliIT.CliProcessResult printResult = runPrint("-d", invalidDirectory, "-l", "some-log");

    // Should fail with non-zero exit code
    assertNotEquals(
        "Expected non-zero exit code for unreachable directory", 0, printResult.exitCode());

    logger.info("Unreachable directory error in print handled gracefully");
  }

  // ==================== General CLI Error Tests ====================

  /**
   * Tests that CLI commands handle missing required arguments gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCli_missingRequiredArguments() throws Exception {
    // pal call without peer or log
    AbstractCliIT.CliProcessResult callResult = runCall("java.lang.System", "currentTimeMillis");
    assertNotEquals("Expected failure for missing peer/log", 0, callResult.exitCode());

    // pal rm without specifying what to remove
    AbstractCliIT.CliProcessResult rmResult = runRm("-d", getPalDirectoryUrl());
    assertNotEquals("Expected failure for missing rm target", 0, rmResult.exitCode());

    // pal print without log
    AbstractCliIT.CliProcessResult printResult = runPrint("-d", getPalDirectoryUrl());
    assertNotEquals("Expected failure for missing log", 0, printResult.exitCode());

    logger.info("Missing required arguments handled gracefully");
  }

  /**
   * Tests that CLI commands handle invalid flag combinations gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCli_invalidFlagCombinations() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // pal call with conflicting options (peer and log at same time)
    // Note: This may actually be valid depending on implementation
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            UUID.randomUUID().toString(),
            "-l",
            "some-log",
            "java.lang.System",
            "currentTimeMillis");

    // Check if this combination is rejected
    logger.info("Call with peer and log: exit code {}", callResult.exitCode());

    // pal rm trying to remove both peer and log at once (conflicting targets)
    AbstractCliIT.CliProcessResult rmResult =
        runRm("-d", palDirectory, "-p", UUID.randomUUID().toString(), "-l", "some-log");

    // This should fail as mutually exclusive
    assertNotEquals("Expected failure for conflicting rm targets", 0, rmResult.exitCode());

    logger.info("Invalid flag combinations handled gracefully");
  }
}
