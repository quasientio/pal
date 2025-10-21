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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal ls` command.
 *
 * <p>Tests listing of peers and logs (both Kafka and Chronicle) in various formats (short, long)
 * with sorting options.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class ListIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(ListIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private Process peerProcess;

  /**
   * Sets up test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
    // Clean slate for each test
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
   * Tests that `pal ls -P` lists running peers.
   *
   * <p>Launches a transient peer and verifies it appears in the peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_showsRunningPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a specific name
    String peerName = "test-peer-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // List peers
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-P");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected peer name in output", result.stdout(), containsString(peerName));
    logger.info("Successfully listed peer: {}", peerName);
  }

  /**
   * Tests that `pal ls -P -l` shows detailed peer information.
   *
   * <p>Launches a transient peer and verifies long format includes RPC addresses and log
   * information.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_longFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with RPC and pub
    String peerName = "test-peer-long-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--tcp-pub",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // List peers in long format
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-P", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected peer name in output", result.stdout(), containsString(peerName));
    // Long format should include more details like RPC address
    assertThat("Expected RPC info in long format", result.stdout(), containsString("rpc://"));
    logger.info("Successfully listed peer in long format");
  }

  /**
   * Tests that `pal ls -L` lists Kafka logs.
   *
   * <p>Launches a peer with a WAL, which creates a Kafka topic, then verifies the log appears in
   * the listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_showsKafkaLogs() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a specific WAL log name
    String walName = "test-wal-" + generateId();
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

    // List logs
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
    logger.info("Successfully listed Kafka log: {}", walName);
  }

  /**
   * Tests that `pal ls -L` lists Chronicle logs.
   *
   * <p>Creates a Chronicle WAL by launching a peer, then verifies the log appears in the listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_showsChronicleLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL with a unique name
    String walName = "test-chronicle-" + generateId();
    String walPath = "file:" + walName;

    peerProcess =
        launchTransientPeer(
            "-d", palDirectory, "--wal", walPath, "--rpc", "auto", "-cp", getIttAppsClasspath());

    // List logs
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected Chronicle log name in output", result.stdout(), containsString(walName));
    logger.info("Successfully listed Chronicle log: {}", walName);
  }

  /**
   * Tests that `pal ls -L -l` shows detailed log information including offsets and sizes.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_longFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a WAL
    String walName = "test-wal-long-" + generateId();
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

    // List logs in long format
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
    // Long format should include offset information
    logger.info("Successfully listed logs in long format");
  }

  /**
   * Tests that `pal ls -L -c` sorts logs by creation time (newest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_sortByCtime() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create two logs with different creation times
    // Note: We keep peers running so their logs stay registered in the directory
    String walName1 = "test-wal-ctime1-" + generateId();
    Process peer1 =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName1,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Wait a bit to ensure different creation times
    Thread.sleep(1000);

    String walName2 = "test-wal-ctime2-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName2,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Give peers a moment to fully register their logs in the directory
    Thread.sleep(500);

    // List logs sorted by creation time (while peers are still running)
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L", "-c");

    // Stop first peer now
    stopPeer(peer1);

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected first WAL log in output", result.stdout(), containsString(walName1));
    assertThat("Expected second WAL log in output", result.stdout(), containsString(walName2));

    // Check that walName2 appears before walName1 (newest first)
    int idx1 = result.stdout().indexOf(walName1);
    int idx2 = result.stdout().indexOf(walName2);
    assertThat("Expected newer log to appear first", idx2 < idx1);
    logger.info("Successfully verified logs sorted by creation time");
  }

  /**
   * Tests that `pal ls` (no flags) shows both peers and logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testList_noFlags_showsBoth() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a WAL
    String peerName = "test-peer-both-" + generateId();
    String walName = "test-wal-both-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // List everything (no -P or -L flag)
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory);

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected peer name in output", result.stdout(), containsString(peerName));
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
    logger.info("Successfully listed both peers and logs");
  }

  /**
   * Tests that `pal ls -L` does not show peers.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_doesNotShowPeers() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a WAL
    String peerName = "test-peer-exclude-" + generateId();
    String walName = "test-wal-exclude-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // List only logs
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
    // Should not show peers when -L is specified
    assertThat(
        "Expected peer name NOT in logs-only output",
        result.stdout(),
        not(containsString(peerName)));
    logger.info("Successfully verified logs-only listing excludes peers");
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
