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
import static org.junit.Assert.assertEquals;

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
  private Process peerProcess;

  /**
   * Sets up test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
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
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Stop peer to ensure messages are flushed
    stopPeer(peerProcess);
    peerProcess = null;

    // Print messages from the log in FULL format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-k", kafkaServers, "-l", walName, "--output-format", "FULL");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    // Verify output is not empty (peer wrote some messages)
    assertThat("Expected non-empty output", printResult.stdout().length() > 0);

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
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    stopPeer(peerProcess);
    peerProcess = null;

    // Print messages in JSON format
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-k", kafkaServers, "-l", walName, "--output-format", "JSON");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    // JSON format should have JSON structure markers
    assertThat("Expected JSON in output", printResult.stdout().length() > 0);

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
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    stopPeer(peerProcess);
    peerProcess = null;

    // Print messages in COMPACT format
    AbstractCliIT.CliProcessResult printResult =
        runPrint(
            "-d", palDirectory, "-k", kafkaServers, "-l", walName, "--output-format", "COMPACT");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", printResult.stdout().length() > 0);

    logger.info("Successfully printed messages from Kafka log in COMPACT format");
  }

  /**
   * Tests that `pal print` can print messages from a Chronicle log in FULL format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrint_chronicleLog_fullFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL by launching a peer
    String walName = "test-print-chronicle-" + generateId();
    String walPath = "file:" + walName;

    peerProcess =
        launchTransientPeer(
            "-d", palDirectory, "--wal", walPath, "--rpc", "auto", "-cp", getIttAppsClasspath());

    stopPeer(peerProcess);
    peerProcess = null;

    // Print messages from Chronicle log
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", walName, "--output-format", "FULL");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected content in output", printResult.stdout().length() > 0);

    logger.info("Successfully printed messages from Chronicle log");
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
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    stopPeer(peerProcess);
    peerProcess = null;

    // Print starting from offset 0
    AbstractCliIT.CliProcessResult printResult =
        runPrint(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-l",
            walName,
            "-o",
            "0",
            "--output-format",
            "FULL");

    assertEquals("Expected successful print with offset", 0, printResult.exitCode());
    assertThat("Expected content in output", printResult.stdout().length() > 0);

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
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    stopPeer(peerProcess);
    peerProcess = null;

    // Print with message type filter
    AbstractCliIT.CliProcessResult printResult =
        runPrint(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-l",
            walName,
            "--types",
            "CLASS_METHOD",
            "--output-format",
            "FULL");

    // Command should execute successfully even if no EXEC messages exist
    assertEquals("Expected successful print (may have no output)", 0, printResult.exitCode());

    logger.info("Successfully executed print with message type filter");
  }

  /**
   * Gets the classpath for itt-apps module.
   *
   * @return classpath string
   */
  private String getIttAppsClasspath() {
    String palHome = System.getenv("PAL_HOME");
    return palHome + "/modules/itt-apps/target/classes";
  }
}
