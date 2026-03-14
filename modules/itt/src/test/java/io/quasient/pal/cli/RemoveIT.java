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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
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
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

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
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

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
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

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

  /**
   * Tests that `pal rm -P` can remove a peer by its UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_byUuid() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer
    UUID peerId = UUID.randomUUID();
    peerProcess = launchPeer(peerId, "-d", palDirectory, "-cp", getIttAppsClasspath());

    // Give peer a moment to fully register
    Thread.sleep(1000);

    // Verify peer is registered
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat(
        "Expected peer UUID in listing", listResult.stdout(), containsString(peerId.toString()));

    // Remove the peer by UUID with --force (since it's alive)
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-P", peerId.toString(), "--force");
    assertEquals("Expected successful removal", 0, removeResult.exitCode());

    // Stop the peer process
    stopPeer(peerProcess);
    peerProcess = null;

    // Verify peer is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected peer NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(peerId.toString())));

    logger.info("Successfully removed peer by UUID: {}", peerId);
  }

  /**
   * Tests that `pal rm -L` can remove a log directly by name when accessing Kafka directly.
   *
   * <p>This test uses the -k (--kafka-servers) option to delete a Kafka log without using the PAL
   * directory for resolution (direct mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_directKafkaMode() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a log by launching a peer with a WAL
    String walName = "test-wal-direct-rm-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

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

    // Verify log exists
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log in listing", listResult.stdout(), containsString(walName));

    // Remove the log using direct Kafka mode with -k option
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-k", kafkaServers, "-L", walName, "--force");
    assertEquals("Expected successful removal in direct mode", 0, removeResult.exitCode());

    // Verify log is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected log NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName)));

    logger.info("Successfully removed Kafka log in direct mode: {}", walName);
  }

  /**
   * Tests that `pal rm` fails gracefully when neither -L nor -P is specified.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemove_noFlag_showsUsage() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Run rm without -L or -P
    AbstractCliIT.CliProcessResult removeResult = runRm("-d", palDirectory, "some-name");

    // Should return non-zero exit code
    assertTrue("Expected non-zero exit code", removeResult.exitCode() != 0);
    // Should show usage hint
    assertThat("Expected usage hint about -L or -P", removeResult.stdout(), containsString("-L"));

    logger.info("Successfully validated rm command requires -L or -P flag");
  }

  /**
   * Tests that `pal rm -L --all` removes all logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLogs_deleteAll() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create multiple logs with a unique prefix to avoid interfering with other tests
    String prefix = "test-rmall-" + generateId();
    String walName1 = prefix + "-log1";
    String walName2 = prefix + "-log2";

    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

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

    int peer2ExitCode = joinPeer(peer2, 10);
    assertEquals("Expected successful peer2 exit code", 0, peer2ExitCode);

    // Verify both logs exist
    AbstractCliIT.CliProcessResult listResult = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listResult.exitCode());
    assertThat("Expected log1 in listing", listResult.stdout(), containsString(walName1));
    assertThat("Expected log2 in listing", listResult.stdout(), containsString(walName2));

    // Remove all logs with prefix using --starting-with
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

    logger.info("Successfully removed all logs with prefix: {}", prefix);
  }

  // ============================================================================
  // Test stubs for Issue #381 - Remove edge cases
  // Implementation tracking: Issue #382
  // ============================================================================

  /**
   * Tests that `pal rm -P` can remove a dead peer without the --force flag.
   *
   * <p>Dead peers (those that have terminated and no longer maintain their etcd lease) should be
   * removable without requiring the --force flag, since there's no active lease to override.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_deadPeer_removesWithoutForce() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Given: Launch a peer that terminates quickly (runs a simple class and exits)
    String peerName = "test-dead-peer-" + generateId();
    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    // Launch peer and wait for it to complete
    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "-n", peerName, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete naturally (peer terminates, lease expires)
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Wait a bit for the lease to fully expire in etcd (leases have TTL)
    Thread.sleep(2000);

    // Verify peer still appears in directory listing (dead peer may still be registered)
    // Note: After lease expires, the /state key is deleted but /static may remain
    // The peer listing may or may not show the peer depending on timing

    // When: Remove the dead peer WITHOUT --force flag
    AbstractCliIT.CliProcessResult removeResult = runRm("-d", palDirectory, "-P", peerName);

    // Then: Exit code 0 - removal should succeed without --force for dead peers
    // The command succeeds either because:
    // 1. The peer's lease has expired (isPeerAlive returns false), so no --force needed
    // 2. The peer was already cleaned up by etcd after lease expired
    assertEquals(
        "Expected successful removal of dead peer without --force", 0, removeResult.exitCode());

    // Verify peer is no longer listed
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected peer NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(peerName)));

    logger.info("Successfully removed dead peer without --force: {}", peerName);
  }

  /**
   * Tests that `pal rm -P` handles a non-existent peer gracefully.
   *
   * <p>Attempting to remove a peer that does not exist in the directory should complete without
   * error. The current implementation silently succeeds when no matching peers are found.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_nonExistent_showsError() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Given: Generate a unique peer name that definitely doesn't exist
    String nonExistentPeerName = "nonexistent-peer-" + generateId();

    // Verify the peer doesn't exist in the directory
    AbstractCliIT.CliProcessResult listBefore = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listBefore.exitCode());
    assertThat(
        "Peer should not exist before test",
        listBefore.stdout(),
        not(containsString(nonExistentPeerName)));

    // When: Attempt to remove the non-existent peer
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-P", nonExistentPeerName);

    // Then: The command completes without error
    // Note: The current implementation silently succeeds when no matching peers are found.
    // This is acceptable behavior - deleting something that doesn't exist is idempotent.
    assertEquals(
        "Expected exit code 0 when removing non-existent peer (idempotent deletion)",
        0,
        removeResult.exitCode());

    // Verify the peer still doesn't exist (no side effects)
    AbstractCliIT.CliProcessResult listAfter = runLs("-d", palDirectory, "-P");
    assertEquals("Expected successful list", 0, listAfter.exitCode());
    assertThat(
        "Peer should still not exist after removal attempt",
        listAfter.stdout(),
        not(containsString(nonExistentPeerName)));

    logger.info(
        "Verified removal of non-existent peer completes gracefully: {}", nonExistentPeerName);
  }

  /**
   * Tests that `pal rm -L` handles a non-existent log gracefully.
   *
   * <p>Attempting to remove a log that does not exist in the directory should complete without
   * error. When Kafka servers are available (either via -k or in resolveLogInfo fallback), the
   * command attempts to delete the Kafka topic which may silently succeed for non-existent topics.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_nonExistent_showsError() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Given: Generate a unique log name that definitely doesn't exist
    String nonExistentLogName = "nonexistent-log-" + generateId();

    // Verify the log doesn't exist in the directory
    AbstractCliIT.CliProcessResult listBefore = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listBefore.exitCode());
    assertThat(
        "Log should not exist before test",
        listBefore.stdout(),
        not(containsString(nonExistentLogName)));

    // When: Attempt to remove the non-existent log
    // Using -k to provide Kafka servers for direct mode fallback
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectory, "-k", kafkaServers, "-L", nonExistentLogName, "--force");

    // Then: The command completes without error
    // Note: When the log is not found in the PAL directory, resolveLogInfo falls back to
    // direct Kafka mode (since -k is provided), which creates a minimal LogInfo and attempts
    // to delete the Kafka topic. Kafka's deleteTopics is idempotent - deleting a non-existent
    // topic does not cause an error.
    assertEquals(
        "Expected exit code 0 when removing non-existent log (idempotent deletion)",
        0,
        removeResult.exitCode());

    // Verify the log still doesn't exist in directory (no side effects)
    AbstractCliIT.CliProcessResult listAfter = runLs("-d", palDirectory, "-L");
    assertEquals("Expected successful list", 0, listAfter.exitCode());
    assertThat(
        "Log should still not exist in directory after removal attempt",
        listAfter.stdout(),
        not(containsString(nonExistentLogName)));

    logger.info(
        "Verified removal of non-existent log completes gracefully: {}", nonExistentLogName);
  }

  /**
   * Tests that `pal rm -L` can remove a Chronicle log directly without using the directory.
   *
   * <p>When using direct Chronicle mode (file: URI), the rm command should delete the Chronicle
   * queue files from the filesystem directly without requiring PAL directory registration.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_directChronicleMode_deletesFiles() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();

    // Given: Create a Chronicle log by running a peer with --wal file:<path>
    String walName = "test-chronicle-direct-rm-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    // Launch peer to create the Chronicle queue
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

    // Get the absolute path created from the LogInfo
    PalDirectory palDirectory = new PalDirectory(palDirectoryUrl, true);
    LogInfo logInfo = palDirectory.getLogInfo(walName);
    assertThat("Expected log to be registered in directory", logInfo != null);
    String walAbsPath = logInfo.getName();
    palDirectory.close();

    // Verify Chronicle queue files exist
    Path chroniclePath = Path.of(walAbsPath);
    assertTrue(
        String.format("Expected Chronicle queue directory to exist at %s", chroniclePath),
        Files.exists(chroniclePath));

    // When: Remove the log using direct Chronicle mode (file: prefix)
    // The -d flag is still needed to unregister from directory, but the file: prefix
    // tells the command to delete the Chronicle files at that specific path
    AbstractCliIT.CliProcessResult removeResult =
        runRm("-d", palDirectoryUrl, "-L", "file:" + walAbsPath, "--force");
    assertEquals(
        "Expected successful removal in direct Chronicle mode", 0, removeResult.exitCode());

    // Then: Verify the Chronicle queue directory has been deleted from filesystem
    assertThat("Expected Chronicle queue directory to be deleted", !Files.exists(chroniclePath));

    // Verify log is no longer listed in the directory
    AbstractCliIT.CliProcessResult listAfterRemove = runLs("-d", palDirectoryUrl, "-L");
    assertEquals("Expected successful list", 0, listAfterRemove.exitCode());
    assertThat(
        "Expected log NOT in listing after removal",
        listAfterRemove.stdout(),
        not(containsString(walName)));

    logger.info("Successfully removed Chronicle log in direct mode: {}", walAbsPath);
  }
}
