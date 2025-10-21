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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal rm` command.
 *
 * <p>Tests removal of peers and logs (both Kafka and Chronicle) from the directory and underlying
 * storage.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class RemoveIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(RemoveIT.class);

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
   * Tests that `pal rm -P` removes a peer from the directory.
   *
   * <p>Launches a peer, removes it by UUID, then verifies it no longer appears in peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_unregistersPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer
    String peerName = "test-peer-remove-" + generateId();
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

    // First, list to get the peer UUID
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected peer in listing", listResult.stdout(), containsString(peerName));

    // Stop the peer before removing (peers should be stopped before removal)
    stopPeer(peerProcess);
    peerProcess = null;

    // Remove the peer by name
    AbstractCliIT.CliProcessResult removeResult = runRm("-d", palDirectory, "-P", peerName);
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify peer is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected peer NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(peerName)));

    logger.info("Successfully removed peer: {}", peerName);
  }

  /**
   * Tests that `pal rm -L` removes a Kafka log from directory and deletes the topic.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_deletesKafkaLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with a WAL to create a Kafka topic
    String walName = "test-wal-remove-" + generateId();
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

    // Verify log exists
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log in listing", listResult.stdout(), containsString(walName));

    // Stop peer before removing log
    stopPeer(peerProcess);
    peerProcess = null;

    // Remove the log
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-k", kafkaServers, "-L", walName);
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify log is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected log NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName)));

    logger.info("Successfully removed Kafka log: {}", walName);
  }

  /**
   * Tests that `pal rm -L` removes a Chronicle log from directory and deletes the files.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_deletesChronicleLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL
    String walName = "test-chronicle-remove-" + generateId();
    String walPath = "file:" + walName;

    peerProcess =
        launchTransientPeer(
            "-d", palDirectory, "--wal", walPath, "--rpc", "auto", "-cp", getIttAppsClasspath());

    // Verify log exists in directory
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log in listing", listResult.stdout(), containsString(walName));

    // Get base directory for Chronicle logs
    String palHome = System.getenv("PAL_HOME");
    Path chroniclePath = Paths.get(palHome, "wal", walName);

    // Verify Chronicle queue files exist
    assertThat("Expected Chronicle queue directory to exist", Files.exists(chroniclePath));

    // Stop peer before removing log
    stopPeer(peerProcess);
    peerProcess = null;

    // Remove the log
    AbstractCliIT.CliProcessResult removeResult = runRm("-d", palDirectory, "-L", walName);
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify log is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected log NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName)));

    // Verify Chronicle queue files are deleted
    assertThat("Expected Chronicle queue directory to be deleted", !Files.exists(chroniclePath));

    logger.info("Successfully removed Chronicle log: {}", walName);
  }

  /**
   * Tests that `pal rm -L -s prefix` removes logs starting with the given prefix.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLogs_withPrefix() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create multiple logs with the same prefix
    // Note: We keep peers running so logs stay registered in directory
    String prefix = "test-prefix-" + generateId();
    String walName1 = prefix + "-log1";
    String walName2 = prefix + "-log2";

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

    Process peer2 =
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

    // Verify both logs exist (while peers are running)
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log1 in listing", listResult.stdout(), containsString(walName1));
    assertThat("Expected log2 in listing", listResult.stdout(), containsString(walName2));

    // Stop peers before removing (logs should remain in Kafka)
    stopPeer(peer1);
    stopPeer(peer2);
    peerProcess = null;

    // Remove logs with prefix (using -a to auto-confirm)
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-k", kafkaServers, "-L", "-s", prefix, "-a");
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify logs are no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected log1 NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName1)));
    assertThat(
        "Expected log2 NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName2)));

    logger.info("Successfully removed logs with prefix: {}", prefix);
  }

  /**
   * Tests that `pal rm -P -a` removes all peers.
   *
   * <p>Note: This test creates peers specifically for removal and uses the -a flag to auto-confirm
   * deletion.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeers_withAll() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create multiple peers with a unique prefix to avoid interfering with other tests
    String prefix = "test-removeall-" + generateId();
    String peerName1 = prefix + "-peer1";
    String peerName2 = prefix + "-peer2";

    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName1,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());
    stopPeer(peerProcess);

    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName2,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());
    stopPeer(peerProcess);
    peerProcess = null;

    // Verify both peers exist
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected peer1 in listing", listResult.stdout(), containsString(peerName1));
    assertThat("Expected peer2 in listing", listResult.stdout(), containsString(peerName2));

    // Remove peers with prefix using -a flag
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-P", "-s", prefix, "-a");
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify peers are no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected peer1 NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(peerName1)));
    assertThat(
        "Expected peer2 NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(peerName2)));

    logger.info("Successfully removed all peers with prefix: {}", prefix);
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
