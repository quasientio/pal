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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Splitter;
import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.tools.cli.AbstractPalSubcommand;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  /**
   * Main class that exercises the full {@code --with-return} matrix in a single run: constructors,
   * instance methods, class methods, and instance field reads/writes via mutations on a
   * public-field Order (so {@code EXEC_PUT_FIELD}/{@code EXEC_PUT_FIELD_DONE} are captured in the
   * WAL).
   */
  private static final String MUTATING_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MutatingApp";

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
   * Verifies the {@code --with-return} contract across message types against a real Kafka WAL.
   *
   * <p>For every operation-style message type produced by {@link #MUTATING_CLASS} (constructor,
   * instance method, class method, instance field read/write — i.e. anything that has a matching
   * {@code RETURN_VALUE} or {@code _DONE} message), {@code pal log print -o N --with-return} must
   * emit exactly two records: the operation itself and its corresponding completion.
   *
   * <p>For each return-style type ({@code RETURN_VALUE}, {@code PUT_FIELD_DONE}) the same command
   * must emit exactly one record (the offset itself) — the offset already represents a completion,
   * so there is nothing to scan forward for. To make the assertion bite the original regression we
   * pick a return-style offset whose immediate successor is also a return-style record (the
   * scenario in which the pre-fix code printed the unrelated successor).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withReturn_acrossMessageTypes() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-print-matrix-" + generateId();
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
            "--rpc-default-action",
            "ALLOW",
            "-cp",
            getIttAppsClasspath(),
            MUTATING_CLASS,
            "widget",
            "100");
    joinPeer(peerProcess, 15);
    peerProcess = null;

    Thread.sleep(1000);

    assertWithReturnMatrix("-d", palDir, walName);
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
   * Chronicle counterpart to {@link #testLogPrint_kafkaLog_withReturn_acrossMessageTypes}. Runs the
   * same operation/return-style matrix against a Chronicle-backed WAL.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withReturn_acrossMessageTypes() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chronicleName = "test-print-chr-matrix-" + generateId();
    trackChronicleLog(chronicleName);
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--wal",
            "file:" + chronicleName,
            "--rpc-default-action",
            "ALLOW",
            "-cp",
            getIttAppsClasspath(),
            MUTATING_CLASS,
            "widget",
            "100");
    joinPeer(peerProcess, 15);
    peerProcess = null;

    assertWithReturnMatrix("-d", palDir, "file:" + chronicleName);
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
  // command: pal peer print <pubAddress> / pal peer print <peerUuid>
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

    // Verify the command did not crash (EXIT_INTERRUPTED means interrupted after timeout)
    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
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

    assertEquals(AbstractPalSubcommand.EXIT_INTERRUPTED, result.exitCode());
  }

  // ==========================================================================
  // Tree format indentation tests: put/put_done nesting
  // ==========================================================================

  /** Fully qualified name of the MinimalReceiptCalculator test application. */
  private static final String RECEIPT_CALC_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";

  /**
   * Creates a WAL spec appropriate for the given backend and registers Chronicle paths for cleanup.
   *
   * @param prefix a descriptive prefix for the WAL name
   * @param backend the WAL backend type ("chronicle" or "kafka")
   * @return the WAL spec string
   */
  private String createWalSpec(String prefix, String backend) {
    String id = generateId();
    if ("chronicle".equals(backend)) {
      String path = "/tmp/pal-" + prefix + "-" + id;
      trackChronicleLog(path);
      return "file:" + path;
    } else {
      return "test-" + prefix + "-" + id;
    }
  }

  /**
   * Records a WAL by running the peer with MinimalReceiptCalculator.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param backend the WAL backend type
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordReceiptCalcWal(String walSpec, String backend, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("--wal");
    args.add(walSpec);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(RECEIPT_CALC_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs {@code pal log print --tree} against the given WAL spec.
   *
   * @param walSpec the WAL spec
   * @param backend the WAL backend type
   * @return the CLI process result
   * @throws Exception if the command fails
   */
  private CliProcessResult doLogPrintTree(String walSpec, String backend) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add(walSpec);
    args.add("--tree");
    return runLogPrint(args.toArray(new String[0]));
  }

  /**
   * Tests that put_done messages in tree output are indented at the same level as their
   * corresponding put messages when using a Kafka log.
   *
   * <p>Records a WAL using MinimalReceiptCalculator (which has instance field assignments in its
   * constructor) and verifies the tree output nesting. Before the fix, put_done messages were
   * incorrectly nested one level deeper than put messages.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_treeFormat_putDoneIndentation() throws Exception {
    verifyPutDoneTreeIndentation("kafka");
  }

  /**
   * Tests that put_done messages in tree output are indented at the same level as their
   * corresponding put messages when using a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_treeFormat_putDoneIndentation() throws Exception {
    verifyPutDoneTreeIndentation("chronicle");
  }

  /**
   * Verifies that put_done messages have the same indentation as their corresponding put messages
   * in tree output, for the given backend type.
   *
   * <p>The MinimalReceiptCalculator constructor assigns instance fields ({@code this.taxRate} and
   * {@code this.loyalCustomer}), generating put/put_done message pairs. This test verifies that
   * put_done messages are printed at the same tree depth as their corresponding put messages.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   * @throws Exception if test execution fails
   */
  private void verifyPutDoneTreeIndentation(String backend) throws Exception {
    String walSpec = createWalSpec("tree-putdone", backend);

    // Record WAL with MinimalReceiptCalculator (has field puts in constructor)
    ProcessResult recordResult = recordReceiptCalcWal(walSpec, backend, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Print tree output
    CliProcessResult printResult = doLogPrintTree(walSpec, backend);
    assertEquals("Tree print should succeed", 0, printResult.exitCode());

    String treeOutput = printResult.stdout();
    assertThat("Tree output should not be empty", treeOutput.length(), greaterThan(0));

    // Parse lines and verify put/put_done indentation
    List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(treeOutput);

    // Find all put and put_done lines
    List<TreeLine> putLines = new ArrayList<>();
    List<TreeLine> putDoneLines = new ArrayList<>();
    for (String line : lines) {
      int indent = countLeadingSpaces(line);
      String trimmed = line.trim();
      if (trimmed.contains(" put_done ")) {
        putDoneLines.add(new TreeLine(indent, trimmed));
      } else if (trimmed.contains(" put ")) {
        putLines.add(new TreeLine(indent, trimmed));
      }
    }

    // MinimalReceiptCalculator has field puts (taxRate, loyalCustomer)
    assertThat("Should have put operations", putLines.size(), greaterThanOrEqualTo(1));
    assertThat("Should have put_done operations", putDoneLines.size(), greaterThanOrEqualTo(1));
    assertThat("put and put_done counts should match", putDoneLines.size(), is(putLines.size()));

    // Each put_done must be at the same depth as its corresponding put
    for (int i = 0; i < putLines.size(); i++) {
      assertThat(
          String.format(
              "put_done #%d indent should match put #%d indent. put='%s', put_done='%s'",
              i, i, putLines.get(i).content(), putDoneLines.get(i).content()),
          putDoneLines.get(i).indent(),
          is(putLines.get(i).indent()));
    }
  }

  /**
   * Counts the number of leading space characters in a string.
   *
   * @param line the line to measure
   * @return the number of leading spaces
   */
  private static int countLeadingSpaces(String line) {
    int count = 0;
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == ' ') {
        count++;
      } else {
        break;
      }
    }
    return count;
  }

  /**
   * Record representing a line of tree output with its indentation level and content.
   *
   * @param indent the number of leading spaces
   * @param content the trimmed line content
   */
  private record TreeLine(int indent, String content) {}

  /**
   * Operation-style message types that {@link #MUTATING_CLASS} is known to produce. {@code
   * --with-return} at any of these must emit exactly two records: the operation and its matching
   * return / done message.
   */
  private static final List<String> WITH_RETURN_OPERATION_TYPES =
      List.of("CONSTRUCTOR", "INSTANCE_METHOD", "CLASS_METHOD", "GET_FIELD", "PUT_FIELD");

  /**
   * Return-style message types covered by the regression: at offsets of these types {@code
   * --with-return} must emit only the record itself.
   */
  private static final List<String> WITH_RETURN_RETURNY_TYPES =
      List.of("RETURN_VALUE", "PUT_FIELD_DONE");

  /**
   * Asserts the {@code --with-return} contract across message types for the given log identifier.
   * For each operation type expects {@code op + matching return} (2 records); for each return-style
   * type expects only the record itself (1 record), using offsets whose successor is also a
   * return-style record so that the pre-fix bug would actually fire.
   *
   * @param logArgs prefix args identifying the log: typically {@code "-d", palDir, walOrFilePath}
   * @throws Exception if a {@code pal log print} invocation fails
   */
  private void assertWithReturnMatrix(String... logArgs) throws Exception {
    Set<Long> returnyOffsets = new HashSet<>();
    for (String type : WITH_RETURN_RETURNY_TYPES) {
      returnyOffsets.addAll(parseCompactOffsets(runLogPrintWithTypes(type, logArgs)));
    }

    for (String type : WITH_RETURN_OPERATION_TYPES) {
      List<Long> offsets = parseCompactOffsets(runLogPrintWithTypes(type, logArgs));
      assertThat("expected at least one " + type + " in WAL", offsets.size(), greaterThan(0));
      long offset = offsets.get(0);

      CliProcessResult result = runLogPrintAt(offset, logArgs);
      assertEquals("type=" + type + " exit code", 0, result.exitCode());
      assertThat(
          "type=" + type + " expected op+return; stdout=\n" + result.stdout(),
          countCompactRecords(result.stdout()),
          is(2));
      assertThat(
          "type=" + type + " expected op offset present",
          result.stdout(),
          containsString("offset=" + offset + " "));
    }

    for (String type : WITH_RETURN_RETURNY_TYPES) {
      long offset = findFirstOffsetWithReturnySuccessor(type, returnyOffsets, logArgs);

      CliProcessResult result = runLogPrintAt(offset, logArgs);
      assertEquals("type=" + type + " exit code", 0, result.exitCode());
      assertThat(
          "type=" + type + " expected only the requested record; stdout=\n" + result.stdout(),
          countCompactRecords(result.stdout()),
          is(1));
      assertThat(
          "type=" + type + " expected requested offset present",
          result.stdout(),
          containsString("offset=" + offset + " "));
    }
  }

  /**
   * Runs {@code pal log print} on the given log filtered to a single message type (COMPACT format).
   *
   * @param type the {@code --types} value (without the {@code EXEC_} prefix)
   * @param logArgs prefix args identifying the log
   * @return the CLI result
   * @throws Exception if the invocation fails
   */
  private CliProcessResult runLogPrintWithTypes(String type, String... logArgs) throws Exception {
    String[] args = new String[logArgs.length + 2];
    System.arraycopy(logArgs, 0, args, 0, logArgs.length);
    args[logArgs.length] = "--types";
    args[logArgs.length + 1] = type;
    return runLogPrint(args);
  }

  /**
   * Runs {@code pal log print -o N --with-return} on the given log (COMPACT format).
   *
   * @param offset the offset to print from
   * @param logArgs prefix args identifying the log
   * @return the CLI result
   * @throws Exception if the invocation fails
   */
  private CliProcessResult runLogPrintAt(long offset, String... logArgs) throws Exception {
    String[] args = new String[logArgs.length + 3];
    System.arraycopy(logArgs, 0, args, 0, logArgs.length);
    args[logArgs.length] = "-o";
    args[logArgs.length + 1] = String.valueOf(offset);
    args[logArgs.length + 2] = "--with-return";
    return runLogPrint(args);
  }

  /**
   * Finds the first offset of the given message type whose immediate successor in the log is also a
   * return-style record. The pre-fix bug only manifests under this adjacency, so this is what we
   * feed to {@code --with-return} to make the assertion meaningful.
   *
   * @param type the message type to look for (without the {@code EXEC_} prefix)
   * @param returnyOffsets offsets of all return-style records in the log
   * @param logArgs prefix args identifying the log
   * @return the first offset of {@code type} whose {@code offset+1} is in {@code returnyOffsets}
   * @throws Exception if the discovery invocation fails
   */
  private long findFirstOffsetWithReturnySuccessor(
      String type, Set<Long> returnyOffsets, String... logArgs) throws Exception {
    List<Long> offsets = parseCompactOffsets(runLogPrintWithTypes(type, logArgs));
    for (Long o : offsets) {
      if (returnyOffsets.contains(o + 1L)) {
        return o;
      }
    }
    fail(
        "expected a "
            + type
            + " offset whose successor is a return-style record; got offsets="
            + offsets);
    return -1L;
  }

  /**
   * Counts the number of records present in a COMPACT-formatted {@code pal log print} stdout
   * stream. Each record is exactly one line that starts with {@code offset=}.
   *
   * @param stdout the captured stdout from a {@code pal log print} invocation in COMPACT format
   * @return the number of records found
   */
  private static int countCompactRecords(String stdout) {
    int count = 0;
    for (String line : Splitter.on('\n').split(stdout)) {
      if (line.startsWith("offset=")) {
        count++;
      }
    }
    return count;
  }

  /**
   * Parses the offsets from a COMPACT-formatted {@code pal log print} stdout stream, in input
   * order. Lines that do not start with {@code offset=} are ignored.
   *
   * @param result the result of a {@code pal log print} invocation in COMPACT format
   * @return the offsets extracted from each {@code offset=N ...} line
   */
  private static List<Long> parseCompactOffsets(CliProcessResult result) {
    assertEquals(0, result.exitCode());
    List<Long> offsets = new ArrayList<>();
    for (String line : Splitter.on('\n').split(result.stdout())) {
      if (!line.startsWith("offset=")) {
        continue;
      }
      int eq = line.indexOf('=');
      int sp = line.indexOf(' ', eq);
      offsets.add(Long.parseLong(line.substring(eq + 1, sp)));
    }
    return offsets;
  }
}
