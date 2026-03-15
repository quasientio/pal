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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for Chronicle-related CLI functionality using the new entity-operation command
 * structure.
 *
 * <p>Tests for Chronicle log handling in {@code pal log print}, {@code pal log ls}, {@code pal log
 * rm}, and {@code pal log call} commands, including:
 *
 * <ul>
 *   <li>Printing Chronicle logs without PAL_DIRECTORY
 *   <li>Chronicle log size and offset accuracy
 *   <li>Handling absolute paths in Chronicle logs
 *   <li>Storing absolute paths in LogInfo
 *   <li>Stripping {@code file:} prefix in {@code pal log print}
 *   <li>Direct mode and registry mode operations
 * </ul>
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class ChronicleCliIT extends AbstractCliIT {

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
  // Chronicle log print tests: pal log print
  // Old command: pal print -l file:<name>
  // New command: pal log print file:<name>
  // ==========================================================================

  /**
   * Tests that {@code pal log print} can print Chronicle logs without PAL_DIRECTORY.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withoutPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-nodir-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Print without -d flag (no PAL_DIRECTORY)
    CliProcessResult result = runLogPrint("file:" + absPath, "--full");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected non-empty stdout", result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log print} works with {@code file:} prefix in log identifier.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_chronicleLog_withFilePrefix() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-prefix-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Print with -d flag and file: prefix
    CliProcessResult result = runLogPrint("-d", palDir, "file:" + absPath, "--full");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected non-empty stdout", result.stdout(), is(not("")));
  }

  // ==========================================================================
  // Chronicle log list tests: pal log ls
  // Old command: pal ls -L
  // New command: pal log ls
  // ==========================================================================

  /**
   * Tests that {@code pal log ls -l} shows accurate size and offset for Chronicle logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_chronicleLog_accurateSizeAndOffsets() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-ls-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogLs("-d", palDir, "-l", "--no-trim");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    // The Chronicle log name (the absolute path) should appear in the listing
    assertThat("Expected output to contain log name", result.stdout(), containsString(absPath));
  }

  /**
   * Tests that {@code pal log ls} updates offsets when the same Chronicle log is reused.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_chronicleLog_offsetsIncrementOnRerun() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String absPath = "/tmp/test-chr-rerun-" + generateId();
    trackChronicleLog(absPath);

    // First run: launch peer, generate messages, join
    UUID peerId1 = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId1,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult firstLs = runLogLs("-d", palDir, "-l", "--no-trim");
    assertThat("Expected exit code 0 for first ls", firstLs.exitCode(), is(0));
    String firstOutput = firstLs.stdout();

    // Second run: launch another peer with the SAME WAL to append more messages
    UUID peerId2 = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId2,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult secondLs = runLogLs("-d", palDir, "-l", "--no-trim");
    assertThat("Expected exit code 0 for second ls", secondLs.exitCode(), is(0));
    String secondOutput = secondLs.stdout();

    // The second output should differ from the first (end offset should have increased)
    assertThat("Expected output to change after second run", secondOutput, is(not(firstOutput)));
  }

  /**
   * Tests that {@code pal log ls} shows Chronicle logs created with absolute paths.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_chronicleLog_withAbsolutePath() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-abspath-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult result = runLogLs("-d", palDir, "-l", "--no-trim");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat(
        "Expected output to contain absolute path", result.stdout(), containsString(absPath));
  }

  // ==========================================================================
  // Chronicle log remove tests: pal log rm
  // Old command: pal rm -L <name>
  // New command: pal log rm <name>
  // ==========================================================================

  /**
   * Tests that {@code pal log rm} can remove Chronicle logs created with absolute paths.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_chronicleLog_withAbsolutePath() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-rm-abs-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    CliProcessResult rmResult = runLogRm("-d", palDir, absPath, "--force");
    assertThat("Expected exit code 0 for log rm", rmResult.exitCode(), is(0));

    // Verify log no longer appears in listing
    CliProcessResult lsResult = runLogLs("-d", palDir);
    assertThat(
        "Log should not appear after removal", lsResult.stdout(), not(containsString(absPath)));
  }

  /**
   * Tests that {@code pal log rm} can remove Chronicle logs by filename.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_chronicleLog_withFilename() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String filename = "test-chr-rm-fname-" + generateId();
    String absPath = "/tmp/" + filename;
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Remove using just the filename (not the full path)
    CliProcessResult rmResult = runLogRm("-d", palDir, filename, "--force");
    assertThat("Expected exit code 0 for log rm by filename", rmResult.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log rm} can remove Chronicle logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_chronicleLog_withoutPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String absPath = "/tmp/test-chr-rm-nodir-" + generateId();
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Remove without -d flag (direct mode)
    CliProcessResult rmResult = runLogRm("file:" + absPath, "--force");
    assertThat("Expected exit code 0 for direct-mode log rm", rmResult.exitCode(), is(0));
  }

  // ==========================================================================
  // Chronicle log storage tests
  // ==========================================================================

  /**
   * Tests that Chronicle logs store absolute paths in LogInfo when relative paths are provided.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testChronicleLog_storesAbsolutePath() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "chr-rel-" + generateId();
    trackChronicleLog(walName);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + walName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;

    // Query LogInfo from etcd to check the stored path is absolute
    PalDirectory dir = new PalDirectory(palDir, true);
    LogInfo info = dir.getLogInfo(walName);
    dir.close();

    assertThat("LogInfo name should be an absolute path", info.getName(), startsWith("/"));
  }

  // ==========================================================================
  // Chronicle log call tests: pal log call
  // Old command: pal call --output-log file:<source> --input-log file:<wal>
  // New command: pal log call --output-log file:<source> --input-log file:<wal>
  // ==========================================================================

  /**
   * Tests that {@code pal log call} can write to Chronicle logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_chronicleLog_withPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String source = "/tmp/test-chr-call-src-" + generateId();
    String wal = "/tmp/test-chr-call-wal-" + generateId();
    trackChronicleLog(source);
    trackChronicleLog(wal);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + wal,
            "--log",
            "file:" + source,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result =
        runLogCall(
            "-d",
            palDir,
            "--output-log",
            "file:" + source,
            "--input-log",
            "file:" + wal,
            "-m",
            "staticStringWithStringArgs",
            METHODS_CLASS,
            "test-call-registry");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected RESULT in stdout", result.stdout(), containsString("RESULT:"));
  }

  /**
   * Tests that {@code pal log call} can write to Chronicle logs without PAL_DIRECTORY (Direct
   * Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_chronicleLog_withoutPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String source = "/tmp/test-chr-call-dsrc-" + generateId();
    String wal = "/tmp/test-chr-call-dwal-" + generateId();
    trackChronicleLog(source);
    trackChronicleLog(wal);

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            "file:" + wal,
            "--log",
            "file:" + source,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Call without -d flag (direct mode, using absolute paths)
    CliProcessResult result =
        runLogCall(
            "--output-log",
            "file:" + source,
            "--input-log",
            "file:" + wal,
            "-m",
            "staticStringWithStringArgs",
            METHODS_CLASS,
            "test-call-direct");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected RESULT in stdout", result.stdout(), containsString("RESULT:"));
  }
}
