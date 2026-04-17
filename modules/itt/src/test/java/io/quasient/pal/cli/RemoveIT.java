/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal peer rm} and {@code pal log rm} commands.
 *
 * <p>Tests removal of peers and logs (both Kafka and Chronicle) from the directory and underlying
 * storage using the new entity-operation command structure.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class RemoveIT extends AbstractCliIT {

  /** The fully-qualified class name used for peer launch tests. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** A peer process handle for tests that launch peers, or null if none launched. */
  private PeerProcess peerProcess;

  /** A secondary peer process handle for tests that launch two peers, or null if none launched. */
  private PeerProcess secondaryPeerProcess;

  /** Initializes test state before each test method. */
  @Before
  public void setUp() {
    peerProcess = null;
    secondaryPeerProcess = null;
  }

  /**
   * Tears down test state after each test method, stopping any launched peers.
   *
   * @throws Exception if stopping the peer fails
   */
  @After
  public void tearDown() throws Exception {
    if (secondaryPeerProcess != null) {
      stopPeer(secondaryPeerProcess);
      secondaryPeerProcess = null;
    }
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  // ==========================================================================
  // Peer removal tests: pal peer rm
  // command: pal peer rm <name>
  // ==========================================================================

  /**
   * Tests that {@code pal peer rm} removes a peer from the directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_unregistersPeer() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "rm-peer-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Force-remove the live peer
    CliProcessResult rmResult = runPeerRm("-d", palDir, peerName, "--force");
    assertThat("Expected exit code 0 for peer rm --force", rmResult.exitCode(), is(0));

    // Verify the peer no longer appears in listing
    CliProcessResult lsResult = runPeerLs("-d", palDir);
    assertThat(
        "Peer should not appear in listing after removal",
        lsResult.stdout(),
        not(containsString(peerName)));

    // Stop the peer process since we removed its registration
    stopPeer(peerProcess);
    peerProcess = null;
  }

  /**
   * Tests that {@code pal peer rm} can remove a peer by its UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_byUuid() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Remove the peer by UUID
    CliProcessResult rmResult = runPeerRm("-d", palDir, peerId.toString(), "--force");
    assertThat("Expected exit code 0 for peer rm by UUID", rmResult.exitCode(), is(0));

    // Stop the peer process since we removed its registration
    stopPeer(peerProcess);
    peerProcess = null;
  }

  /**
   * Tests that {@code pal peer rm} can remove a dead peer without the --force flag.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_deadPeer_removesWithoutForce() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "rm-dead-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Stop the peer so it becomes dead
    stopPeer(peerProcess);
    peerProcess = null;

    // Allow etcd lease to begin expiring
    Thread.sleep(1000);

    // Remove the dead peer with --force (lease may still be active shortly after shutdown).
    // After graceful shutdown, the peer may already be unregistered (exit 1 = "no peer found"),
    // or its lease may still be active (exit 0 = removed successfully).
    CliProcessResult rmResult = runPeerRm("-d", palDir, peerName, "--force");
    assertThat(
        "Expected exit code 0 or 1 for removing dead peer",
        rmResult.exitCode(),
        is(not(greaterThan(1))));

    // Verify the peer is no longer in the directory
    CliProcessResult lsResult = runPeerLs("-d", palDir);
    assertThat(
        "Dead peer should not appear in listing", lsResult.stdout(), not(containsString(peerName)));
  }

  /**
   * Tests that {@code pal peer rm} handles a non-existent peer gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeer_nonExistent_showsError() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult rmResult = runPeerRm("-d", palDir, "nonexistent-" + generateId());

    // Non-existent peer returns non-zero exit code
    assertThat(
        "Expected non-zero exit code for non-existent peer", rmResult.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal peer rm -s} removes all peers matching a prefix.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemovePeers_withAll() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String prefix = "rmtest-" + generateId() + "-";
    String peerName1 = prefix + "a";
    String peerName2 = prefix + "b";
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId1,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName1,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    secondaryPeerProcess =
        launchPeer(
            peerId2,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName2,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Stop both peers so they are dead
    stopPeer(peerProcess);
    peerProcess = null;
    stopPeer(secondaryPeerProcess);
    secondaryPeerProcess = null;

    // Remove all peers with the prefix
    CliProcessResult rmResult = runPeerRm("-d", palDir, "-s", prefix, "--force");
    assertThat("Expected exit code 0 for peer rm with prefix", rmResult.exitCode(), is(0));

    // Verify neither peer appears in listing
    CliProcessResult lsResult = runPeerLs("-d", palDir);
    assertThat(
        "First peer should not appear after prefix removal",
        lsResult.stdout(),
        not(containsString(peerName1)));
    assertThat(
        "Second peer should not appear after prefix removal",
        lsResult.stdout(),
        not(containsString(peerName2)));
  }

  // ==========================================================================
  // Log removal tests: pal log rm
  // command: pal log rm <name>
  // ==========================================================================

  /**
   * Tests that {@code pal log rm} removes a Kafka log from directory and deletes the topic.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_deletesKafkaLog() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "rm-kafka-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Remove the Kafka log
    CliProcessResult rmResult = runLogRm("-d", palDir, walName, "--force");
    assertThat("Expected exit code 0 for log rm", rmResult.exitCode(), is(0));

    // Verify the log no longer appears in listing
    CliProcessResult lsResult = runLogLs("-d", palDir);
    assertThat(
        "Log should not appear in listing after removal",
        lsResult.stdout(),
        not(containsString(walName)));
  }

  /**
   * Tests that {@code pal log rm} removes a Chronicle log from directory and deletes the files.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_deletesChronicleLog() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String chronicleName = "rm-chronicle-" + generateId();

    trackChronicleLog(chronicleName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    // Remove the Chronicle log
    CliProcessResult rmResult = runLogRm("-d", palDir, chronicleName, "--force");
    assertThat("Expected exit code 0 for chronicle log rm", rmResult.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log rm -s} removes logs matching a prefix.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLogs_withPrefix() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String prefix = "rmlog-" + generateId() + "-";
    String walName1 = prefix + "a";
    String walName2 = prefix + "b";
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId1,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName1,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    secondaryPeerProcess =
        launchPeer(
            peerId2,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName2,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(secondaryPeerProcess, 15);
    secondaryPeerProcess = null;

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Remove all logs matching the prefix
    CliProcessResult rmResult = runLogRm("-d", palDir, "-s", prefix, "--force");
    assertThat("Expected exit code 0 for log rm with prefix", rmResult.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log rm -s} deletes all logs matching a prefix.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLogs_deleteAll() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String prefix = "rmdel-" + generateId() + "-";
    String walName1 = prefix + "a";
    String walName2 = prefix + "b";
    UUID peerId1 = UUID.randomUUID();
    UUID peerId2 = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId1,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName1,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    secondaryPeerProcess =
        launchPeer(
            peerId2,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName2,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(secondaryPeerProcess, 15);
    secondaryPeerProcess = null;

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Remove all logs matching the prefix
    CliProcessResult rmResult = runLogRm("-d", palDir, "-s", prefix, "--force");
    assertThat(
        "Expected exit code 0 for log rm delete all with prefix", rmResult.exitCode(), is(0));

    // Verify neither log appears in listing
    CliProcessResult lsResult = runLogLs("-d", palDir);
    assertThat(
        "First log should not appear after prefix removal",
        lsResult.stdout(),
        not(containsString(walName1)));
    assertThat(
        "Second log should not appear after prefix removal",
        lsResult.stdout(),
        not(containsString(walName2)));
  }

  /**
   * Tests that {@code pal log rm} handles a non-existent log gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_nonExistent_showsError() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Kafka topic deletion is idempotent, so this should succeed
    CliProcessResult rmResult =
        runLogRm("-d", palDir, "-k", kafkaServers, "nonexistent-" + generateId(), "--force");
    assertThat(
        "Expected exit code 0 for idempotent Kafka topic deletion", rmResult.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log rm} can remove a log directly via Kafka servers.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_directKafkaMode() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "rm-kafka-direct-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Remove the log using direct Kafka mode
    CliProcessResult rmResult = runLogRm("-d", palDir, "-k", kafkaServers, walName, "--force");
    assertThat("Expected exit code 0 for direct Kafka log rm", rmResult.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log rm} can remove a Chronicle log directly without using the directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemoveLog_directChronicleMode_deletesFiles() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String chronicleSuffix = generateId();
    String chroniclePath = "/tmp/test-chronicle-rm-" + chronicleSuffix;

    trackChronicleLog(chroniclePath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chroniclePath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, 15);
    peerProcess = null;

    // Remove the Chronicle log using direct file mode
    CliProcessResult rmResult = runLogRm("-d", palDir, "file:" + chroniclePath, "--force");
    assertThat("Expected exit code 0 for direct Chronicle log rm", rmResult.exitCode(), is(0));
  }
}
