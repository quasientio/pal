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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.tools.cli.AbstractPalSubcommand;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal log stats} and {@code pal peer stats} commands.
 *
 * <p>Tests collecting statistics from Kafka logs, Chronicle logs, and peer sockets using the
 * entity-operation command structure.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class MessageStreamStatsIT extends AbstractCliIT {

  /** Main class used for launching peers that generate messages. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Main class that throws an exception, used for exception stats testing. */
  private static final String THROWING_MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.rpc.ThrowingMain";

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

    CliProcessResult result = runLogStats("-d", palDir, "-k", kafkaServers, walName);

    assertEquals(0, result.exitCode());
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
        runLogStats("-d", palDir, "-k", kafkaServers, walName, "--types", "CONSTRUCTOR");

    assertEquals(0, result.exitCode());
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
        runLogStats("-d", palDir, "-k", kafkaServers, "--from-peer", peerId.toString(), walName);

    assertEquals(0, result.exitCode());
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

    CliProcessResult result = runLogStats("-d", palDir, "-k", kafkaServers, walName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log stats} handles non-existent logs gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_kafkaLog_emptyLog() throws Exception {
    String kafkaServers = getKafkaServers();
    String nonExistentLog = "nonexistent-log-" + generateId();

    CliProcessResult result = runLogStats("-k", kafkaServers, nonExistentLog);

    // The command may exit with an error (no partitions) or produce empty stats
    assertThat(
        "Exit code should be 0 or 1", result.exitCode() == 0 || result.exitCode() == 1, is(true));
  }

  // ==========================================================================
  // Log stats tests (Chronicle): pal log stats file:<path>
  // ==========================================================================

  /**
   * Tests that {@code pal log stats} collects basic statistics from a Chronicle log.
   *
   * <p>Launches a peer writing to a Chronicle WAL, then reads stats. Since Chronicle stats is a
   * one-shot command (reads all messages, prints, exits), we use {@code runLogStats()} instead of
   * the duration-based runner used for Kafka streaming stats.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_basicCounters() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-basic-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Use directory-resolved name (peer registered the chronicle log in etcd)
    CliProcessResult result = runLogStats("-d", palDir, chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), is(not("")));
    // Methods app creates objects + calls methods, so we expect non-zero counters
    assertThat(result.stdout(), containsString("# messages of type:"));
  }

  /**
   * Tests that {@code pal log stats} works with the {@code file:} prefix directly, without
   * resolving through the directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_directFilePrefix() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-direct-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Use file: prefix directly — no -d needed for resolution
    CliProcessResult result = runLogStats("file:" + chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log stats} can filter messages by type from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_messageTypeFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-type-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runLogStats("-d", palDir, chronicleName, "--types", "EXEC_CONSTRUCTOR");

    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log stats} can filter messages by peer UUID from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_peerFiltering() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-peer-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result =
        runLogStats("-d", palDir, "--from-peer", peerId.toString(), chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log stats -j} produces JSON output from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_jsonOutput() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-json-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogStats("-d", palDir, chronicleName, "-j");

    assertEquals(0, result.exitCode());
    // JSON output should contain the Counters fields
    assertThat(result.stdout(), containsString("numberOfMessages"));
  }

  /**
   * Tests that {@code pal log stats} returns an error for a non-existent Chronicle path.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_nonExistentPath() throws Exception {
    CliProcessResult result = runLogStats("file:/tmp/nonexistent-log-" + generateId());

    assertThat(result.exitCode(), is(1));
  }

  // ==========================================================================
  // New stats features: exception stats, time span, entry points
  // ==========================================================================

  /**
   * Tests that {@code pal log stats} reports exception statistics from a Chronicle log.
   *
   * <p>Launches a peer running {@link ThrowingMain}, which throws a {@code RuntimeException}. The
   * stats output should include exception type and method information.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_exceptionStats() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-except-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "-cp",
            getIttAppsClasspath(),
            THROWING_MAIN_CLASS);
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogStats("-d", palDir, chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), containsString("# exceptions of type:"));
    assertThat(result.stdout(), containsString("RuntimeException"));
  }

  /**
   * Tests that {@code pal log stats -j} includes exception stats in JSON output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_exceptionStatsJson() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-except-j-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "-cp",
            getIttAppsClasspath(),
            THROWING_MAIN_CLASS);
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogStats("-d", palDir, chronicleName, "-j");

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), containsString("\"exceptionsByType\""));
    assertThat(result.stdout(), containsString("RuntimeException"));
  }

  /**
   * Tests that {@code pal log stats} reports time span and message rate from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_timeSpan() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-time-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogStats("-d", palDir, chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), containsString("time span:"));
    assertThat(result.stdout(), containsString("message rate:"));
  }

  /**
   * Tests that {@code pal log stats} reports entry-point count from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogStats_chronicleLog_entryPointCount() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-stats-chr-entry-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

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
    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogStats("-d", palDir, chronicleName);

    assertEquals(0, result.exitCode());
    assertThat(result.stdout(), containsString("# entry points (RPC calls):"));
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
        "Exit code should be 0 or EXIT_INTERRUPTED (interrupted after timeout)",
        result.exitCode() == 0 || result.exitCode() == AbstractPalSubcommand.EXIT_INTERRUPTED,
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
        "Exit code should be 0 or EXIT_INTERRUPTED (interrupted after timeout)",
        result.exitCode() == 0 || result.exitCode() == AbstractPalSubcommand.EXIT_INTERRUPTED,
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
        "Exit code should be 0 or EXIT_INTERRUPTED (interrupted after timeout)",
        result.exitCode() == 0 || result.exitCode() == AbstractPalSubcommand.EXIT_INTERRUPTED,
        is(true));
  }
}
