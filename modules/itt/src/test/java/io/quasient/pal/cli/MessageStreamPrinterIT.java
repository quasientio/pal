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
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal log print} and {@code pal peer print} commands.
 *
 * <p>Tests printing messages from Kafka and Chronicle logs in various output formats (FULL, JSON,
 * COMPACT, TREE) with filtering and offset options, as well as streaming from peer PUB sockets,
 * using the new entity-operation command structure.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class MessageStreamPrinterIT extends AbstractCliIT {

  /** Main class that creates objects, calls methods, and accesses fields. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Main class whose main() calls alwaysThrows() which throws RuntimeException. */
  private static final String THROWING_MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.rpc.ThrowingMain";

  /** Peer process handle for the currently running peer, if any. */
  private PeerProcess peerProcess;

  /** Resets the peer process handle before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Stops any running peer process after each test.
   *
   * @throws Exception if stopping the peer fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  // ==========================================================================
  // Log print tests (Kafka): pal log print
  // Old command: pal print -l <log>
  // New command: pal log print <log>
  // ==========================================================================

  /**
   * Tests that {@code pal log print} can print messages from a Kafka log in FULL format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_fullFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-full-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print} can print messages from a Kafka log in JSON format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_jsonFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-json-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--json");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print} can print messages from a Kafka log in COMPACT format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_compactFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-compact-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName);
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --tree} outputs messages in tree format from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_treeFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-tree-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--tree");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print -o} works with Kafka logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withStartOffset() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-offset-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "-o", "0", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --offset N --with-return} shows the operation and its return
   * value from a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withReturn() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-ret-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "-o", "0", "--with-return", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --offset 0 --with-return} correctly finds the matching return
   * for a method that throws an exception (Kafka log).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withReturn_throwingMethod() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-throw-" + generateId();
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--no-wal-incoming-cli",
            "-cp",
            getIttAppsClasspath(),
            THROWING_MAIN_CLASS);
    joinPeer(peerProcess, 15);
    peerProcess = null;

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "-o", "0", "--with-return", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --types} can filter messages by type.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_filterByMessageType() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-type-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "--types", "CLASS_METHOD", "--full");
    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log print --types} can filter by multiple message types.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_multipleTypeFilters() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-mtypes-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "--types", "CONSTRUCTOR,INSTANCE_METHOD", "--full");
    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log print --from-peer} can filter messages by peer UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_filterByPeer() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-peer-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "--from-peer", peerId.toString(), "--full");
    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log print --from-thread} can filter messages by thread name.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_filterByThread() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-thread-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "--from-thread", "nonexistent-thread", "--full");
    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log print} can access Kafka logs directly with -k option.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_directMode() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-direct-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, "-k", kafkaServers, walName, "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --filter "class=..."} filters messages by class name from a
   * Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_filterByClass() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-class-" + generateId();
    UUID peerId = UUID.randomUUID();

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

    Thread.sleep(1000);

    CliProcessResult result =
        runLogPrint("-d", palDir, walName, "--filter", "class=com.nonexistent.DoesNotExist");
    assertEquals(0, result.exitCode());
  }

  // ==========================================================================
  // Log print tests (Chronicle): pal log print
  // Old command: pal print -l <log>
  // New command: pal log print <log>
  // ==========================================================================

  /**
   * Tests that {@code pal log print} can print messages from a Chronicle log in FULL format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_fullFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-full-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, chronicleName, "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print} can print messages from a Chronicle log in COMPACT format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_compactFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-compact-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, chronicleName);
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print} can print messages from a Chronicle log in JSON format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_jsonFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-json-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, chronicleName, "--json");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --tree} outputs messages in tree format from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_treeFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-tree-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, chronicleName, "--tree");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print -o} works with Chronicle logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withStartOffset() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-offset-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, chronicleName, "-o", "0", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --offset N --with-return} shows the operation and its return
   * value from a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withReturn() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-ret-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result =
        runLogPrint("-d", palDir, "file:" + chronicleName, "-o", "0", "--with-return", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --offset 0 --with-return} correctly finds the matching return
   * for a method that throws an exception (Chronicle log).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withReturn_throwingMethod() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-throw-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "--no-wal-incoming-cli",
            "-cp",
            getIttAppsClasspath(),
            THROWING_MAIN_CLASS);
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result =
        runLogPrint("-d", palDir, chronicleName, "-o", "0", "--with-return", "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --types} can filter messages by type from Chronicle logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_filterByMessageType() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-type-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result =
        runLogPrint("-d", palDir, chronicleName, "--types", "CLASS_METHOD", "--full");
    assertEquals(0, result.exitCode());
  }

  /**
   * Tests that {@code pal log print} with Chronicle log can use direct file path.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_directFilePath() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-dirfile-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result = runLogPrint("-d", palDir, "file:" + chronicleName, "--full");
    assertEquals(0, result.exitCode());
    assertThat(result.stdout().length(), greaterThan(0));
  }

  /**
   * Tests that {@code pal log print --filter "class=..."} filters messages by class name from a
   * Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_filterByClass() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-class-" + generateId();
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
    joinPeer(peerProcess, 15);
    peerProcess = null;

    CliProcessResult result =
        runLogPrint("-d", palDir, chronicleName, "--filter", "class=com.nonexistent.DoesNotExist");
    assertEquals(0, result.exitCode());
  }

  // ==========================================================================
  // Peer print tests (socket streaming): pal peer print
  // Old command: pal print -pa <pubAddress> / pal print -pu <peerUuid>
  // New command: pal peer print <pubAddress> / pal peer print <peerUuid>
  //              (address/UUID becomes positional argument)
  // ==========================================================================

  /**
   * Tests that {@code pal peer print} can print messages from a peer's PUB socket in FULL format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_fullFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-ppfull-" + generateId();
    String peerName = "pp-full-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-full-test");

    // Look up PUB address from directory
    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    // Stream for 5 seconds
    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"}, 5, "-d", palDir, pubAddress, "--full");

    // Verify the command did not crash (exit -1 means killed after timeout, which is expected)
    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print} can print messages from a peer's PUB socket in JSON format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_jsonFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-ppjson-" + generateId();
    String peerName = "pp-json-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-json-test");

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"}, 5, "-d", palDir, pubAddress, "--json");

    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print} can print messages from a peer's PUB socket in COMPACT
   * format.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_compactFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-ppcompact-" + generateId();
    String peerName = "pp-compact-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-compact-test");

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    CliProcessResult result =
        runCliSubcommandForDuration(new String[] {"peer", "print"}, 5, "-d", palDir, pubAddress);

    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print --types} can filter messages by type from a peer socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_filterByMessageType() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-pptype-" + generateId();
    String peerName = "pp-type-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-type-test");

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"},
            5,
            "-d",
            palDir,
            pubAddress,
            "--types",
            "CONSTRUCTOR",
            "--json");

    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print --from-peer} can filter messages by peer UUID from a socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_filterByPeer() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-pppeer-" + generateId();
    String peerName = "pp-peer-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-peer-test");

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"},
            5,
            "-d",
            palDir,
            pubAddress,
            "--from-peer",
            peerId.toString(),
            "--json");

    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print --from-thread} can filter messages by thread name from a
   * socket.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_filterByThread() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-ppthread-" + generateId();
    String peerName = "pp-thread-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-thread-test");

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
    PeerInfo peerInfo = dir.getPeer(peerId);
    String pubAddress = peerInfo.getPubAddress();
    dir.close();

    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"},
            5,
            "-d",
            palDir,
            pubAddress,
            "--from-thread",
            "main",
            "--json");

    assertEquals(-1, result.exitCode());
  }

  /**
   * Tests that {@code pal peer print} accepts a peer UUID as positional argument.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrint_peerSocket_byUuid() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-ppuuid-" + generateId();
    String peerName = "pp-uuid-" + generateId();
    UUID peerId = UUID.randomUUID();

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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Invoke a method to generate messages
    runPeerCall(
        "-d",
        palDir,
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "processArgs",
        METHODS_CLASS,
        "pub-uuid-test");

    // Use UUID as positional argument instead of PUB address
    CliProcessResult result =
        runCliSubcommandForDuration(
            new String[] {"peer", "print"}, 5, "-d", palDir, peerId.toString(), "--json");

    assertEquals(-1, result.exitCode());
  }
}
