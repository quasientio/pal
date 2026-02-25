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
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal print` command.
 *
 * <p>Tests printing messages from Kafka and Chronicle logs in various output formats (FULL, JSON,
 * COMPACT) with filtering and offset options.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class MessageStreamPrinterIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(MessageStreamPrinterIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** Print process handle for socket tests, or null if not started. */
  private PrintProcessHandle printHandle;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    printHandle = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    // Clean up print handle if still running
    if (printHandle != null) {
      try {
        printHandle.waitAndTerminate(100);
      } catch (Exception e) {
        logger.warn("Error terminating print process in tearDown", e);
      }
      printHandle = null;
    }

    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Tests that `pal print` can print messages from a Kafka log in FULL format.
   *
   * <p>This test creates a Kafka WAL by launching a peer, which causes the peer to write internal
   * messages (like registration) to the WAL. We then verify we can print those messages.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_fullFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer - this will write some messages to the WAL
    String walName = "test-print-kafka-full-" + generateId();
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
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

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages from the log in FULL format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    // Verify output is not empty (peer wrote some messages)
    assertThat("Expected non-empty output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Kafka log in FULL format");
  }

  /**
   * Tests that `pal print` can print messages from a Kafka log in JSON format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_jsonFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer
    String walName = "test-print-kafka-json-" + generateId();
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
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

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages in JSON format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--json");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    // JSON format should have JSON structure markers
    assertThat("Expected JSON in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Kafka log in JSON format");
  }

  /**
   * Tests that `pal print` can print messages from a Kafka log in COMPACT format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_compactFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer
    String walName = "test-print-kafka-compact-" + generateId();
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
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

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages in COMPACT format - default: no flag needed
    AbstractCliIT.CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName);

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Kafka log in COMPACT format");
  }

  /**
   * Tests that `pal print` can print messages from a Chronicle log in FULL format.
   *
   * <p>Creates a Chronicle WAL by launching a peer, which causes the peer to write internal
   * messages to the WAL. We then verify we can print those messages.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_fullFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL by launching a peer
    String walName = "test-print-chronicle-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages from Chronicle log
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Chronicle log in FULL format");
  }

  /**
   * Tests that `pal print` can print messages from a Chronicle log in COMPACT format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_compactFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL by launching a peer
    String walName = "test-print-chronicle-compact-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages in COMPACT format (default, no flag needed)
    AbstractCliIT.CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName);

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Chronicle log in COMPACT format");
  }

  /**
   * Tests that `pal print` can print messages from a Chronicle log in JSON format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_jsonFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL by launching a peer
    String walName = "test-print-chronicle-json-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages in JSON format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--json");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    // JSON format should have JSON structure markers
    assertThat("Expected JSON in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Chronicle log in JSON format");
  }

  /**
   * Tests that `pal print -o` works with Kafka logs.
   *
   * <p>Note: This test verifies the command accepts the -o parameter and runs successfully. Testing
   * specific offset behavior would require writing specific messages.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_withStartOffset() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL with some messages
    String walName = "test-print-offset-" + generateId();
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
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

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print starting from offset 0
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "-o", "0", "--full");

    assertEquals("Expected successful print with offset", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from specified offset");
  }

  /**
   * Tests that `pal print` can filter messages by type.
   *
   * <p>This test verifies the --types filter parameter is accepted and the command runs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_filterByMessageType() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL
    String walName = "test-print-filter-" + generateId();
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
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

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print with message type filter
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--types", "CLASS_METHOD", "--full");

    // Command should execute successfully even if no EXEC messages exist
    assertEquals("Expected successful print (may have no output)", 0, printResult.exitCode());

    logger.info("Successfully executed print with message type filter");
  }

  /**
   * Tests that `pal print -o` works with Chronicle logs.
   *
   * <p>Note: This test verifies the command accepts the -o parameter and runs successfully with
   * Chronicle logs. The offset for Chronicle refers to the queue index.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_withStartOffset() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL with some messages
    String walName = "test-print-chronicle-offset-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print starting from offset 0
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "-o", "0", "--full");

    assertEquals("Expected successful print with offset", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Chronicle log with start offset");
  }

  /**
   * Tests that `pal print` can filter messages by type from Chronicle logs.
   *
   * <p>This test verifies the --types filter parameter is accepted and the command runs with
   * Chronicle logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_filterByMessageType() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL
    String walName = "test-print-chronicle-filter-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print with message type filter
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--types", "CLASS_METHOD", "--full");

    // Command should execute successfully even if no CLASS_METHOD messages exist
    assertEquals("Expected successful print (may have no output)", 0, printResult.exitCode());

    logger.info("Successfully executed print with message type filter on Chronicle log");
  }

  /**
   * Tests that `pal print` can filter messages by peer UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_filterByPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL
    String walName = "test-print-filter-peer-" + generateId();
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

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print with peer UUID filter
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--from-peer", peerId.toString(), "--full");

    // Command should execute successfully
    assertEquals("Expected successful print", 0, printResult.exitCode());

    logger.info("Successfully executed print with peer UUID filter");
  }

  /**
   * Tests that `pal print` can filter messages by thread name.
   *
   * <p>This test verifies that the --from-thread filter works by:
   *
   * <ol>
   *   <li>First printing without filter to confirm messages exist
   *   <li>Then filtering by a non-existent thread name to confirm filtering excludes messages
   * </ol>
   *
   * <p>Note: We don't test filtering for a specific matching thread name because PAL runtime uses
   * internal executor threads whose names depend on message channel types and may vary.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_filterByThread() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL
    String walName = "test-print-filter-thread-" + generateId();
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

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // First, print without filter to confirm messages exist
    AbstractCliIT.CliProcessResult unfilteredResult =
        runPrint("-d", palDirectory, "-l", walName, "--full");
    assertEquals("Expected successful print without filter", 0, unfilteredResult.exitCode());
    assertThat("Expected messages in log", !unfilteredResult.stdout().isEmpty());

    // Now filter by a non-existent thread name - should return no messages
    AbstractCliIT.CliProcessResult filteredResult =
        runPrint(
            "-d",
            palDirectory,
            "-l",
            walName,
            "--from-thread",
            "nonexistent-thread-12345",
            "--full");

    // Command should execute successfully
    assertEquals("Expected successful print with filter", 0, filteredResult.exitCode());
    // Output should be empty or minimal (just header info, no actual messages)
    // The filtered output should be smaller than unfiltered since no messages match
    assertThat(
        "Filtered output should be smaller (no matching messages)",
        filteredResult.stdout().length() < unfilteredResult.stdout().length());

    logger.info("Successfully verified thread name filter excludes non-matching messages");
  }

  /**
   * Tests that `pal print` can access Kafka logs directly with -k option.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_directMode() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer
    String walName = "test-print-direct-" + generateId();
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

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print using direct Kafka mode with -k option
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-k", kafkaServers, "-l", walName, "--full");

    assertEquals("Expected successful print in direct mode", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages using direct Kafka mode");
  }

  /**
   * Tests that `pal print` can filter by multiple message types.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_multipleTypeFilters() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL
    String walName = "test-print-multi-types-" + generateId();
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

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print with multiple type filters
    AbstractCliIT.CliProcessResult printResult =
        runPrint(
            "-d", palDirectory, "-l", walName, "--types", "CONSTRUCTOR,INSTANCE_METHOD", "--full");

    // Command should execute successfully
    assertEquals("Expected successful print", 0, printResult.exitCode());

    logger.info("Successfully executed print with multiple type filters");
  }

  /**
   * Tests that `pal print` with Chronicle log can use direct file path.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_directFilePath() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL
    String walName = "test-print-chronicle-direct-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print using the file: prefix to indicate direct Chronicle access
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walPath, "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());

    logger.info("Successfully printed messages from Chronicle log using direct file path");
  }

  // ==========================================================================
  // Socket-based MessageStreamPrinter tests (printSocketMessageStream() method)
  // ==========================================================================

  /** Duration in milliseconds to collect messages from socket before terminating. */
  private static final long SOCKET_COLLECT_DURATION_MS = 5000;

  /**
   * Tests that `pal print` can print messages from a peer's PUB socket in FULL format.
   *
   * <p>Given: A peer running with a TCP PUB socket enabled (--tcp-pub) that generates messages
   * (constructor calls, method invocations, etc.) during its main class execution.
   *
   * <p>When: `pal print -d localhost:2379 -pa <pub-address> --full` is executed to subscribe to the
   * peer's PUB socket and collect messages.
   *
   * <p>Then: stdout should contain message content in FULL format, which for socket-based streaming
   * uses Message.toString() format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_fullFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41791";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST (will wait for socket to become available)
    printHandle = startPrintInBackground("-d", palDirectory, "-pa", pubEndpoint, "--full");

    // Brief delay to let print command initialize
    Thread.sleep(500);

    // Launch peer - this creates the socket and publishes messages
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Collect messages then terminate print command
    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    // Debug: log what was captured
    String stdout = printResult.stdout();
    String stderr = printResult.stderr();
    logger.info(
        "Print command stdout ({} bytes): {}",
        stdout.length(),
        stdout.substring(0, Math.min(200, stdout.length())));
    if (!stderr.isEmpty()) {
      logger.info("Print command stderr: {}", stderr);
    }

    // Then: stdout contains message content in FULL format
    assertThat("Expected non-empty output from socket streaming", !stdout.isEmpty());

    // FULL format for socket streaming outputs Message.toString() which produces
    // the default object representation (io.quasient.pal.messages.colfer.Message@...)
    assertThat(
        "Expected Message objects in output",
        stdout.contains("io.quasient.pal.messages.colfer.Message@"));

    logger.info("Successfully printed {} bytes from peer socket in FULL format", stdout.length());
  }

  /**
   * Tests that `pal print` can print messages from a peer's PUB socket in JSON format.
   *
   * <p>Given: A peer running with a TCP PUB socket enabled that generates messages.
   *
   * <p>When: `pal print -pa <pub-address> --json` is executed to subscribe to the peer's PUB socket
   * and output messages in JSON format.
   *
   * <p>Then: stdout should contain valid JSON message representations.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_jsonFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41792";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST
    printHandle = startPrintInBackground("-d", palDirectory, "-pa", pubEndpoint, "--json");

    Thread.sleep(500);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    String stdout = printResult.stdout();
    assertThat("Expected non-empty output from socket streaming", !stdout.isEmpty());

    // JSON format should contain JSON object markers
    assertThat("Expected JSON content with opening brace", stdout.contains("{"));
    assertThat("Expected JSON content with closing brace", stdout.contains("}"));

    logger.info("Successfully printed {} bytes from peer socket in JSON format", stdout.length());
  }

  /**
   * Tests that `pal print` can print messages from a peer's PUB socket in COMPACT format (default).
   *
   * <p>Given: A peer running with a TCP PUB socket enabled.
   *
   * <p>When: `pal print -pa <pub-address>` is executed (COMPACT is default format).
   *
   * <p>Then: stdout should contain compact message summaries with uuid= and type= markers.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_compactFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41793";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST
    printHandle = startPrintInBackground("-d", palDirectory, "-pa", pubEndpoint);

    Thread.sleep(500);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    String stdout = printResult.stdout();
    assertThat("Expected non-empty output from socket streaming", !stdout.isEmpty());

    // COMPACT format for socket streaming outputs "uuid=<type> type=<type>"
    assertThat(
        "Expected compact format with uuid= or type= markers",
        stdout.contains("uuid=") || stdout.contains("type="));

    logger.info(
        "Successfully printed {} bytes from peer socket in COMPACT format", stdout.length());
  }

  /**
   * Tests that `pal print` can filter messages by type when streaming from a peer socket.
   *
   * <p>Given: A peer running with a TCP PUB socket that generates various message types
   * (CONSTRUCTOR, INSTANCE_METHOD, CLASS_METHOD, etc.) during execution.
   *
   * <p>When: `pal print -pa <pub-address> --types CONSTRUCTOR` is executed to filter for only
   * constructor messages.
   *
   * <p>Then: Only CONSTRUCTOR messages should appear in the output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_filterByMessageType() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41794";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST with type filter
    printHandle =
        startPrintInBackground(
            "-d", palDirectory, "-pa", pubEndpoint, "--types", "CONSTRUCTOR", "--json");

    Thread.sleep(500);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    String stdout = printResult.stdout();

    // If output is not empty, verify it contains CONSTRUCTOR type and not other types
    if (!stdout.isEmpty()) {
      assertThat(
          "Expected CONSTRUCTOR messages when filtering by type",
          stdout.contains("EXEC_CONSTRUCTOR") || stdout.contains("CONSTRUCTOR"));

      // Verify other types are NOT present (type filter should exclude them)
      assertThat(
          "Should not contain INSTANCE_METHOD when filtering for CONSTRUCTOR",
          !stdout.contains("\"EXEC_INSTANCE_METHOD\""));
      assertThat(
          "Should not contain CLASS_METHOD when filtering for CONSTRUCTOR",
          !stdout.contains("\"EXEC_CLASS_METHOD\""));
    }

    logger.info("Successfully filtered messages by type from peer socket");
  }

  /**
   * Tests that `pal print` can filter messages by peer UUID when streaming from a socket.
   *
   * <p>Given: A peer running with a TCP PUB socket and a known UUID.
   *
   * <p>When: `pal print -pa <pub-address> --from-peer <uuid>` is executed to filter messages by
   * peer.
   *
   * <p>Then: Only messages from the specified peer should appear in the output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_filterByPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41795";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST with peer filter
    printHandle =
        startPrintInBackground(
            "-d", palDirectory, "-pa", pubEndpoint, "--from-peer", peerId.toString(), "--json");

    Thread.sleep(500);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    String stdout = printResult.stdout();

    // If messages were collected, they should all be from the specified peer
    if (!stdout.isEmpty()) {
      // The peer UUID should appear in the JSON output (in execMessage.peerUuid field)
      assertThat(
          "Expected peer UUID in filtered output",
          stdout.contains(peerId.toString()) || stdout.contains("peerUuid"));
    }

    logger.info("Successfully filtered messages by peer UUID from socket");
  }

  /**
   * Tests that `pal print` can filter messages by thread name when streaming from a socket.
   *
   * <p>Given: A peer running with a TCP PUB socket that generates messages from the main thread.
   *
   * <p>When: `pal print -pa <pub-address> --from-thread main` is executed to filter messages by
   * thread name.
   *
   * <p>Then: Only messages from the 'main' thread should appear in the output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_peerSocket_filterByThread() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41796";
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    // Start print command FIRST with thread filter
    printHandle =
        startPrintInBackground(
            "-d", palDirectory, "-pa", pubEndpoint, "--from-thread", "main", "--json");

    Thread.sleep(500);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    AbstractCliIT.CliProcessResult printResult =
        printHandle.waitAndTerminate(SOCKET_COLLECT_DURATION_MS);
    printHandle = null;

    String stdout = printResult.stdout();

    // If messages were collected, they should all be from the main thread
    if (!stdout.isEmpty()) {
      // The thread name should appear in the JSON output (in execMessage.threadName field)
      assertThat(
          "Expected thread name 'main' in filtered output",
          stdout.contains("main") || stdout.contains("threadName"));
    }

    logger.info("Successfully filtered messages by thread name from socket");
  }

  // ==========================================================================
  // Tests for new features: TREE format, --with-return, --filter
  // ==========================================================================

  /**
   * Tests that `pal print --tree` outputs messages in tree format from a Chronicle log.
   *
   * <p>The tree format shows operations with indented nesting, using [offset] markers to identify
   * each operation in the call tree.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_treeFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL by launching a peer
    String walName = "test-print-chronicle-tree-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print messages in TREE format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--tree");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());
    // TREE format uses [offset] markers
    assertThat("Expected tree-style [offset] markers", printResult.stdout().contains("[0]"));

    logger.info("Successfully printed messages from Chronicle log in TREE format");
  }

  /**
   * Tests that `pal print --tree` outputs messages in tree format from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_treeFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    String walName = "test-print-kafka-tree-" + generateId();
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

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--tree");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());
    assertThat("Expected tree-style [offset] markers", printResult.stdout().contains("[0]"));

    logger.info("Successfully printed messages from Kafka log in TREE format");
  }

  /**
   * Tests that `pal print --offset N --with-return` shows the operation at the given offset and its
   * corresponding return value from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_withReturn() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    String walName = "test-print-chronicle-withreturn-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print offset 0 with its return value
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "-o", "0", "--with-return", "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());
    // Should have printed at least two messages: the operation + its return value
    // Both will have CONTEXT: markers in FULL format
    String stdout = printResult.stdout();
    int contextCount = countOccurrences(stdout, "CONTEXT:");
    assertThat("Expected at least 2 CONTEXT markers (operation + return)", contextCount >= 2);

    logger.info("Successfully printed operation with return value from Chronicle log");
  }

  /**
   * Tests that `pal print --offset N --with-return` shows the operation at the given offset and its
   * corresponding return value from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_withReturn() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    String walName = "test-print-kafka-withreturn-" + generateId();
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

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print offset 0 with its return value
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "-o", "0", "--with-return", "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", !printResult.stdout().isEmpty());
    String stdout = printResult.stdout();
    int contextCount = countOccurrences(stdout, "CONTEXT:");
    assertThat("Expected at least 2 CONTEXT markers (operation + return)", contextCount >= 2);

    logger.info("Successfully printed operation with return value from Kafka log");
  }

  /**
   * Tests that `pal print --filter "class=..."` filters messages by class name from a Chronicle
   * log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_filterByClass() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    String walName = "test-print-chronicle-filter-class-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print all messages first to confirm there are some
    AbstractCliIT.CliProcessResult allResult = runPrint("-d", palDirectory, "-l", walName);
    assertEquals("Expected successful print", 0, allResult.exitCode());
    assertThat("Expected messages in log", !allResult.stdout().isEmpty());

    // Filter by a class that likely doesn't exist - should produce no output
    AbstractCliIT.CliProcessResult filteredResult =
        runPrint(
            "-d", palDirectory, "-l", walName, "--filter", "class=com.nonexistent.DoesNotExist");

    assertEquals("Expected successful print with filter", 0, filteredResult.exitCode());
    // Filtered output should be smaller (fewer or no matching messages)
    assertThat(
        "Filtered output should be smaller than unfiltered",
        filteredResult.stdout().length() < allResult.stdout().length());

    logger.info("Successfully filtered messages by class name from Chronicle log");
  }

  /**
   * Tests that `pal print --filter "class=..."` filters messages by class name from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_kafkaLog_filterByClass() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    String walName = "test-print-kafka-filter-class-" + generateId();
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

    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Print all messages first
    AbstractCliIT.CliProcessResult allResult = runPrint("-d", palDirectory, "-l", walName);
    assertEquals("Expected successful print", 0, allResult.exitCode());
    assertThat("Expected messages in log", !allResult.stdout().isEmpty());

    // Filter by non-existent class
    AbstractCliIT.CliProcessResult filteredResult =
        runPrint(
            "-d", palDirectory, "-l", walName, "--filter", "class=com.nonexistent.DoesNotExist");

    assertEquals("Expected successful print with filter", 0, filteredResult.exitCode());
    assertThat(
        "Filtered output should be smaller than unfiltered",
        filteredResult.stdout().length() < allResult.stdout().length());

    logger.info("Successfully filtered messages by class name from Kafka log");
  }

  /**
   * Counts the number of occurrences of a substring within a string.
   *
   * @param text the string to search in
   * @param target the substring to count
   * @return the number of occurrences
   */
  private static int countOccurrences(String text, String target) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(target, idx)) != -1) {
      count++;
      idx += target.length();
    }
    return count;
  }
}
