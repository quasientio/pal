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
import static org.junit.Assert.assertTrue;

import com.quasient.pal.PeerProcess;
import com.quasient.pal.cxn.directory.PalDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
   * Tests that `pal rm -P` removes a peer from the directory.
   *
   * <p>Launches a peer, removes it by UUID, then verifies it no longer appears in peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_unregistersPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer
    String peerName = "test-peer-remove-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(peerId, "-d", palDirectory, "-n", peerName, "-cp", getIttAppsClasspath());

    // Give peer a moment to fully register with its lease
    Thread.sleep(1000);

    // Verify peer is registered and alive
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected peer in listing", listResult.stdout(), containsString(peerName));

    // Try to remove the live peer without --force - should fail
    AbstractCliIT.CliProcessResult removeWithoutForce = runRm("-d", palDirectory, "-P", peerName);
    assertTrue(
        "Expected removal to fail for live peer without --force",
        removeWithoutForce.exitCode() != 0);
    assertThat(
        "Expected error message about active lease",
        removeWithoutForce.stdout(),
        containsString("active lease"));

    // Verify peer is still listed (wasn't removed)
    AbstractCliIT.CliProcessResult listAfterFailedRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterFailedRemove.exitCode());
    assertThat(
        "Expected peer still in listing", listAfterFailedRemove.stdout(), containsString(peerName));

    // Now remove with --force - should succeed
    AbstractCliIT.CliProcessResult removeWithForce =
        runRm("-d", palDirectory, "-P", peerName, "--force");
    assertEquals("Expected successful removal with --force", 0, removeWithForce.exitCode());

    // Stop the peer process
    stopPeer(peerProcess);
    peerProcess = null;

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
    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

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

    // Verify log exists
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log in listing", listResult.stdout(), containsString(walName));

    // Remove the log with --force flag to skip confirmation prompts
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-L", walName, "--force");
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
    String palDirectoryUrl = getPalDirectoryUrl();

    // Create a Chronicle WAL
    String walName = "test-chronicle-remove-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectoryUrl,
            "--wal",
            walPath,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Verify log exists in directory
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectoryUrl, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log in listing", listResult.stdout(), containsString(walName));

    // get the absolute path created from the LogInfo (not printed by `pal ls`)
    PalDirectory palDirectory = new PalDirectory(palDirectoryUrl, true);
    String walAbsPath = palDirectory.getLogInfo(walName).getName();
    palDirectory.close();

    // Verify Chronicle queue files exist
    Path chroniclePath = Path.of(walAbsPath);
    assertThat(
        String.format("Expected Chronicle queue directory to exist at %s", chroniclePath),
        Files.exists(chroniclePath));

    // Remove the log with --force flag to skip confirmation prompts
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectoryUrl, "-L", walName, "--force");
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Verify log is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectoryUrl, "-L");
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
    String prefix = "test-prefix-" + generateId();
    String walName1 = prefix + "-log1";
    String walName2 = prefix + "-log2";

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
    String classToRun = "com.quasient.pal.apps.quantized.rpc.Methods";

    UUID peerId1 = UUID.randomUUID();
    PeerProcess peer1 =
        launchPeer(
            peerId1,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName1,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for first peer to complete and create its log
    int peer1ExitCode = joinPeer(peer1, 10);
    assertEquals("Expected successful peer1 exit code", 0, peer1ExitCode);

    UUID peerId2 = UUID.randomUUID();
    PeerProcess peer2 =
        launchPeer(
            peerId2,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName2,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for second peer to complete and create its log
    int peer2ExitCode = joinPeer(peer2, 10);
    assertEquals("Expected successful peer2 exit code", 0, peer2ExitCode);
    peerProcess = null;

    // Verify both logs exist
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log1 in listing", listResult.stdout(), containsString(walName1));
    assertThat("Expected log2 in listing", listResult.stdout(), containsString(walName2));

    // Remove logs with prefix (using --force to skip confirmation prompts)
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-L", "-s", prefix, "--force");
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

    // Create multiple peers with a unique prefix to avoid interfering with other tests
    String prefix = "test-removeall-" + generateId();
    String peerName1 = prefix + "-peer1";
    String peerName2 = prefix + "-peer2";

    UUID peerId1 = UUID.randomUUID();
    PeerProcess peer1 =
        launchPeer(peerId1, "-d", palDirectory, "-n", peerName1, "-cp", getIttAppsClasspath());

    UUID peerId2 = UUID.randomUUID();
    peerProcess =
        launchPeer(peerId2, "-d", palDirectory, "-n", peerName2, "-cp", getIttAppsClasspath());

    // Give peers a moment to fully register in the directory with their leases
    Thread.sleep(1000);

    // Verify both peers exist (while they're still running)
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected peer1 in listing", listResult.stdout(), containsString(peerName1));
    assertThat("Expected peer2 in listing", listResult.stdout(), containsString(peerName2));

    // Try to remove live peers without --force - should fail
    AbstractCliIT.CliProcessResult removeWithoutForce =
        runRm("-d", palDirectory, "-P", "-s", prefix);
    assertTrue(
        "Expected removal to fail for live peers without --force",
        removeWithoutForce.exitCode() != 0);

    // Verify peers are still listed (weren't removed)
    AbstractCliIT.CliProcessResult listAfterFailedRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterFailedRemove.exitCode());
    assertThat(
        "Expected peer1 still in listing",
        listAfterFailedRemove.stdout(),
        containsString(peerName1));
    assertThat(
        "Expected peer2 still in listing",
        listAfterFailedRemove.stdout(),
        containsString(peerName2));

    // Now remove with --force - should succeed
    AbstractCliIT.CliProcessResult removeWithForce =
        runRm("-d", palDirectory, "-P", "-s", prefix, "--force");
    assertEquals("Expected successful removal with --force", 0, removeWithForce.exitCode());

    // Stop the peer processes
    stopPeer(peer1);
    stopPeer(peerProcess);
    peerProcess = null;

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
}
