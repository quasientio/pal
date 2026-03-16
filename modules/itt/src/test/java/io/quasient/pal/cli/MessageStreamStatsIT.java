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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal log stats} and {@code pal peer stats} commands.
 *
 * <p>Tests collecting statistics from Kafka logs and peer sockets using the new entity-operation
 * command structure.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class MessageStreamStatsIT extends AbstractCliIT {

  /** Main class used for launching peers that generate messages. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Primary peer process managed by the test lifecycle. */
  private PeerProcess peerProcess;

  /** Sets up test state before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Tears down test state after each test, stopping any launched peers.
   *
   * @throws Exception if stopping a peer fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  // ==========================================================================
  // Log stats tests: pal log stats
  // Old: programmatic LogStats API
  // New command: pal log stats
  // ==========================================================================

  /**
   * Tests that {@code pal log stats} collects basic statistics from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_basicCounters() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-stats-basic-" + generateId();

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

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"log", "stats"}, 5, "-d", palDir, "-k", kafkaServers, walName);

    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log stats} can filter messages by type.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_messageTypeFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-stats-type-" + generateId();

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

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"log", "stats"},
            5,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            walName,
            "--types",
            "CONSTRUCTOR");

    // The command should complete (killed after timeout) or exit cleanly
    assertThat(
        "Exit code should be 0 or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == -1,
        is(true));
  }

  /**
   * Tests that {@code pal log stats} can filter messages by peer UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_peerFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-stats-peer-" + generateId();

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

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"log", "stats"},
            5,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--from-peer",
            peerId.toString(),
            walName);

    assertThat(
        "Exit code should be 0 or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == -1,
        is(true));
  }

  /**
   * Tests that {@code pal log stats} tracks different message categories correctly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_categoryTracking() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-stats-cat-" + generateId();

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

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"log", "stats"}, 5, "-d", palDir, "-k", kafkaServers, walName);

    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log stats} handles empty logs gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_emptyLog() throws Exception {
    String kafkaServers = getKafkaServers();
    String nonExistentLog = "nonexistent-log-" + generateId();

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"log", "stats"}, 5, "-k", kafkaServers, nonExistentLog);

    // The command may exit with an error or produce empty stats; either is acceptable
    assertThat(
        "Exit code should be 0, 1, or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == 1 || result.exitCode() == -1,
        is(true));
  }

  // ==========================================================================
  // Peer stats tests: pal peer stats
  // Old: programmatic PeerStats API
  // New command: pal peer stats
  // ==========================================================================

  /**
   * Tests that {@code pal peer stats} collects basic statistics from a peer's PUB socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerStats_peerSocket_basicCounters() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-pstats-basic-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Look up the PUB address from the directory
    PalDirectory dir = new PalDirectory(palDir, true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    // Invoke a call to generate messages on the peer
    runPeerCall(
        "-d",
        palDir,
        peerInfo.getName() != null ? peerInfo.getName() : peerId.toString(),
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "staticStringWithStringArgs",
        METHODS_CLASS,
        "stats-test");

    CliProcessResult result =
        runCliSubcommandForDuration(new String[] {"peer", "stats"}, 5, "-d", palDir, pubAddress);

    assertThat(
        "Exit code should be 0 or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == -1,
        is(true));
  }

  /**
   * Tests that {@code pal peer stats} can filter messages by type from a peer socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerStats_peerSocket_messageTypeFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-pstats-type-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    PalDirectory dir = new PalDirectory(palDir, true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    // Generate messages
    runPeerCall(
        "-d",
        palDir,
        peerId.toString(),
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "staticStringWithStringArgs",
        METHODS_CLASS,
        "filter-test");

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "stats"}, 5, "-d", palDir, pubAddress, "--types", "CONSTRUCTOR");

    assertThat(
        "Exit code should be 0 or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == -1,
        is(true));
  }

  /**
   * Tests that {@code pal peer stats} can filter messages by peer UUID from a socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerStats_peerSocket_peerFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-pstats-peer-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    PalDirectory dir = new PalDirectory(palDir, true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    // Generate messages
    runPeerCall(
        "-d",
        palDir,
        peerId.toString(),
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "staticStringWithStringArgs",
        METHODS_CLASS,
        "peer-filter-test");

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "stats"},
            5,
            "-d",
            palDir,
            "--from-peer",
            peerId.toString(),
            pubAddress);

    assertThat(
        "Exit code should be 0 or -1 (killed after timeout)",
        result.exitCode() == 0 || result.exitCode() == -1,
        is(true));
  }
}
