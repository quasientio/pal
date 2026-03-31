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

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal peer ls} and {@code pal log ls} commands.
 *
 * <p>Tests listing of peers and logs (both Kafka and Chronicle) in various formats (short, long)
 * with sorting options using the new entity-operation command structure.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class ListIT extends AbstractCliIT {

  /** Main class used for launching peers that generate messages. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Primary peer process managed by the test lifecycle. */
  private PeerProcess peerProcess;

  /** Secondary peer process used by multi-peer tests. */
  private PeerProcess secondaryPeerProcess;

  /** Sets up test state before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    secondaryPeerProcess = null;
  }

  /**
   * Tears down test state after each test, stopping any launched peers.
   *
   * @throws Exception if stopping a peer fails
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
  // Peer listing tests: pal peer ls
  // Old command: pal ls -P
  // New command: pal peer ls
  // ==========================================================================

  /**
   * Tests that {@code pal peer ls} lists running peers by name.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeersNamed_showsRunningPeer() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "named-peer-" + generateId();
    String walName = "wal-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result = runPeerLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(peerName));
  }

  /**
   * Tests that {@code pal peer ls} lists running peers by UUID when no name is given.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeersUnnamed_showsRunningPeer() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result = runPeerLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(peerId.toString()));
  }

  /**
   * Tests that {@code pal peer ls -l} shows detailed peer information including RPC and PUB
   * addresses.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_longFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "lfp-" + generateId().substring(0, 6);
    String walName = "wal-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--tcp-pub",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result = runPeerLs("-d", palDir, "-l");

    assertThat(result.exitCode(), is(0));
    String trimmedUuid = peerId.toString().substring(0, 8) + "..";
    assertThat(result.stdout(), containsString(trimmedUuid));
    assertThat(result.stdout(), not(containsString(peerId.toString())));
    assertThat(result.stdout(), containsString(peerName));
  }

  /**
   * Tests that {@code pal peer ls -l --no-trim} shows full UUIDs without truncation.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_longFormat_noTrimShowsFullUuid() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "notrim-peer-" + generateId();
    String walName = "wal-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result = runPeerLs("-d", palDir, "-l", "--no-trim");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(peerId.toString()));
    assertThat(result.stdout(), containsString(peerName));
  }

  /**
   * Tests that {@code pal peer ls -c} sorts peers by creation time (newest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_sortByCtime() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String uniqueSuffix = generateId();

    UUID firstPeerId = UUID.randomUUID();
    String firstName = "first-" + uniqueSuffix;
    String firstWal = "wal-first-" + uniqueSuffix;

    peerProcess =
        launchPeer(
            firstPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            firstName,
            "--wal",
            firstWal,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    Thread.sleep(1000);

    UUID secondPeerId = UUID.randomUUID();
    String secondName = "second-" + uniqueSuffix;
    String secondWal = "wal-second-" + uniqueSuffix;

    secondaryPeerProcess =
        launchPeer(
            secondPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            secondName,
            "--wal",
            secondWal,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result = runPeerLs("-d", palDir, "-c");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(firstName));
    assertThat(result.stdout(), containsString(secondName));
  }

  // ==========================================================================
  // Log listing tests: pal log ls
  // Old command: pal ls -L
  // New command: pal log ls
  // ==========================================================================

  /**
   * Tests that {@code pal log ls} lists Kafka logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_showsKafkaLogs() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-kafka-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    Thread.sleep(1000);

    CliProcessResult result = runLogLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(walName));
  }

  /**
   * Tests that {@code pal log ls} lists Chronicle logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_showsChronicleLog() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String chronicleName = "chronicle-" + generateId();
    trackChronicleLog(chronicleName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);

    CliProcessResult result = runLogLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(chronicleName));
  }

  /**
   * Tests that {@code pal log ls -l} shows detailed log information including offsets and sizes.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_longFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-long-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    Thread.sleep(1000);

    CliProcessResult result = runLogLs("-d", palDir, "-l");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log ls -c} sorts logs by creation time (newest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_sortByCtime() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String uniqueSuffix = generateId();

    UUID firstPeerId = UUID.randomUUID();
    String firstWal = "wal-ctime-first-" + uniqueSuffix;

    peerProcess =
        launchPeer(
            firstPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            firstWal,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);

    Thread.sleep(1000);

    UUID secondPeerId = UUID.randomUUID();
    String secondWal = "wal-ctime-second-" + uniqueSuffix;

    secondaryPeerProcess =
        launchPeer(
            secondPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            secondWal,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(secondaryPeerProcess, PROCESS_TIMEOUT_SECONDS);

    CliProcessResult result = runLogLs("-d", palDir, "-c");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(firstWal));
    assertThat(result.stdout(), containsString(secondWal));
  }

  /**
   * Tests that {@code pal log ls -S} sorts logs by size (largest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_sortBySize() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-size-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);

    CliProcessResult result = runLogLs("-d", palDir, "-l", "-S");

    assertThat(result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log ls -c -r} reverses the sort order (oldest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_reverseOrder() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String uniqueSuffix = generateId();

    UUID firstPeerId = UUID.randomUUID();
    String firstWal = "wal-rev-first-" + uniqueSuffix;

    peerProcess =
        launchPeer(
            firstPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            firstWal,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);

    Thread.sleep(1000);

    UUID secondPeerId = UUID.randomUUID();
    String secondWal = "wal-rev-second-" + uniqueSuffix;

    secondaryPeerProcess =
        launchPeer(
            secondPeerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            secondWal,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(secondaryPeerProcess, PROCESS_TIMEOUT_SECONDS);

    CliProcessResult result = runLogLs("-d", palDir, "-c", "-r");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString(firstWal));
    assertThat(result.stdout(), containsString(secondWal));
  }

  /**
   * Tests that {@code pal log ls -l --no-trim} shows full field values without truncation.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_noTrim() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-notrim-long-name-for-testing-truncation-behavior-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);

    CliProcessResult result = runLogLs("-d", palDir, "-l", "--no-trim");

    assertThat(result.exitCode(), is(0));
  }
}
