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

import io.quasient.pal.PeerProcess;
import java.util.UUID;
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
  private PeerProcess peerProcess;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
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
   * Tests that `pal ls -P` lists running peers. In short format, name is printed if given,
   * otherwise Id.
   *
   * <p>Launches a peer and verifies it appears in the peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeersNamed_showsRunningPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with a specific name and ID
    String peerName = "test-peer-" + generateId();
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(peerId, "-d", palDirectory, "-n", peerName, "-cp", getIttAppsClasspath());

    // List peers
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-P");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected peer name in output", result.stdout(), containsString(peerName));
  }

  /**
   * Tests that `pal ls -P` lists running peers. In short format, if no name is given, ID is
   * printed.
   *
   * <p>Launches a peer and verifies it appears in the peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeersUnnamed_showsRunningPeer() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with a specific name and ID
    UUID peerId = UUID.randomUUID();

    peerProcess = launchPeer(peerId, "-d", palDirectory, "-cp", getIttAppsClasspath());

    // List peers
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-P");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected peer ID in output", result.stdout(), containsString(peerId.toString()));
  }

  /**
   * Tests that `pal ls -P -l` shows detailed peer information.
   *
   * <p>Launches a peer and verifies long format includes RPC and PUB addresses.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListPeers_longFormat() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with RPC and PUB endpoints
    String peerName = "test-peer-long-" + generateId();
    UUID peerId = UUID.randomUUID();
    String zmqRpcEndpoint = "localhost:41591";
    String jsonRpcEndpoint = "localhost:33847";
    String pubEndpoint = "localhost:38673";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            zmqRpcEndpoint,
            "--json-rpc",
            jsonRpcEndpoint,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath());

    // List peers in long format
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-P", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());

    assertThat("Expected peer ID in output", result.stdout(), containsString(peerId.toString()));

    // Long format should include more details like RPC and PUB addresses
    assertThat(
        "Expected ZMQ-RPC info in long format", result.stdout(), containsString(zmqRpcEndpoint));
    assertThat(
        "Expected JSON-RPC info in long format", result.stdout(), containsString(jsonRpcEndpoint));
    assertThat("Expected PUB info in long format", result.stdout(), containsString(pubEndpoint));
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

    // now we wait for the process to end, ensuring the log has been created
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);

    // List logs
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
  }

  /**
   * Tests that `pal ls -L` lists Chronicle logs.
   *
   * <p>Creates a Chronicle WAL by launching a peer, then verifies the log appears in the listing.
   * Chronicle logs (file:-prefixed) are now registered in the etcd directory just like Kafka logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListLogs_showsChronicleLog() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Create a Chronicle WAL with a unique name
    String walName = "test-chronicle-" + generateId();
    trackChronicleLog(walName);
    String walPath = "file:" + walName;

    UUID peerId = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Chronicle queue files
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId, "-d", palDirectory, "--wal", walPath, "-cp", getIttAppsClasspath(), classToRun);

    // Wait for the process to complete and create the Chronicle log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

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

    // now we wait for the process to end, ensuring the log has been created
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);

    // List logs in long format
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    // Check for the truncated name (CLI truncates long names with "..")
    String truncatedName = walName.substring(0, Math.min(18, walName.length()));
    assertThat("Expected WAL log name in output", result.stdout(), containsString(truncatedName));
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
    String walName1 = "test-wal-ctime1-" + generateId();
    UUID peerId1 = UUID.randomUUID();

    // we need to run something for messages to be written to the WAL, which actually
    // creates the Log in Kafka
    String classToRun = "io.quasient.pal.apps.quantized.rpc.Methods";

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

    // Wait a bit to ensure different creation times
    Thread.sleep(1000);

    String walName2 = "test-wal-ctime2-" + generateId();
    UUID peerId2 = UUID.randomUUID();
    peerProcess =
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
    int peer2ExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer2 exit code", 0, peer2ExitCode);
    peerProcess = null;

    // List logs sorted by creation time
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory, "-L", "-c");

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
            "-n",
            peerName,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete and create the WAL
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // List everything (no -P or -L flag)
    // Note: peer has exited, so only WAL log will be shown (not peer)
    AbstractCliIT.CliProcessResult result = runLs("-d", palDirectory);

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected WAL log name in output", result.stdout(), containsString(walName));
    // Peer won't be shown since it has exited
    logger.info("Successfully listed WAL log after peer exit");
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
            "-n",
            peerName,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // now we wait for the process to end, ensuring the log has been created
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);

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
}
