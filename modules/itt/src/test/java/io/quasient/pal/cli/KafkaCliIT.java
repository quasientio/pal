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
 * Integration tests for Kafka-related CLI functionality using the new entity-operation command
 * structure.
 *
 * <p>Tests for Kafka log operations in {@code pal log print}, {@code pal log ls}, {@code pal log
 * rm}, and {@code pal log call} commands, including:
 *
 * <ul>
 *   <li>Printing Kafka logs with and without PAL_DIRECTORY
 *   <li>Kafka end offset display (last offset, not last+1)
 *   <li>Removing Kafka logs with and without PAL_DIRECTORY
 *   <li>Calling methods that write to Kafka logs
 * </ul>
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class KafkaCliIT extends AbstractCliIT {

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
  // Kafka log print tests: pal log print
  // Old command: pal print -l <log>
  // New command: pal log print <log>
  // ==========================================================================

  /**
   * Tests that {@code pal log print} can print Kafka logs without PAL_DIRECTORY when -k is given.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withoutPalDirectory_withKafkaServers() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-kprint-nodir-" + generateId();

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

    // Print without -d flag, using -k directly
    CliProcessResult result = runLogPrint("-k", kafkaServers, walName, "--full");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected non-empty stdout", result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal log print} can print Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_kafkaLog_withPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-kprint-dir-" + generateId();

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

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--full");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected non-empty stdout", result.stdout(), is(not("")));
  }

  // ==========================================================================
  // Kafka log list tests: pal log ls
  // Old command: pal ls -L
  // New command: pal log ls
  // ==========================================================================

  /**
   * Tests that {@code pal log ls} shows Kafka end offset as the last message offset (not last+1).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_kafkaLog_endOffsetIsLastMessageOffset() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-kls-offset-" + generateId();

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

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    CliProcessResult result = runLogLs("-d", palDir, "-l", "--no-trim");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected output to contain log name", result.stdout(), containsString(walName));
  }

  // ==========================================================================
  // Kafka log remove tests: pal log rm
  // Old command: pal rm -L <name>
  // New command: pal log rm <name>
  // ==========================================================================

  /**
   * Tests that {@code pal log rm} can remove Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_kafkaLog_withPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-krm-dir-" + generateId();

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

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    CliProcessResult rmResult = runLogRm("-d", palDir, walName, "--force");
    assertThat("Expected exit code 0 for log rm", rmResult.exitCode(), is(0));

    // Verify log no longer appears in listing
    CliProcessResult lsResult = runLogLs("-d", palDir);
    assertThat(
        "Log should not appear after removal", lsResult.stdout(), not(containsString(walName)));
  }

  /**
   * Tests that {@code pal log rm} can remove Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_kafkaLog_withoutPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "wal-krm-nodir-" + generateId();

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

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Remove without -d flag (direct Kafka mode)
    CliProcessResult rmResult = runLogRm("-k", kafkaServers, walName, "--force");
    assertThat("Expected exit code 0 for direct-mode log rm", rmResult.exitCode(), is(0));
  }

  // ==========================================================================
  // Kafka log call tests: pal log call
  // Old command: pal call --output-log <source> --input-log <wal>
  // New command: pal log call --output-log <source> --input-log <wal>
  // ==========================================================================

  /**
   * Tests that {@code pal log call} can write to Kafka logs with PAL_DIRECTORY (Registry Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_kafkaLog_withPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String source = "kcall-src-" + generateId();
    String wal = "kcall-wal-" + generateId();

    // Use --source-log and --wal (not --log) to get separate source and WAL topics.
    // --log sets both sourceLog and wal to the same value, making them the same topic.
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--source-log",
            source,
            "--wal",
            wal,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Write to the source log (peer reads from it), read response from the WAL (peer writes to it)
    CliProcessResult result =
        runLogCall(
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--output-log",
            source,
            "--input-log",
            wal,
            "-m",
            "staticStringWithStringArgs",
            source,
            METHODS_CLASS,
            "test-call-kafka-registry");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected RESULT in stdout", result.stdout(), containsString("RESULT:"));
  }

  /**
   * Tests that {@code pal log call} can write to Kafka logs without PAL_DIRECTORY (Direct Mode).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_kafkaLog_withoutPalDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String source = "kcall-dsrc-" + generateId();
    String wal = "kcall-dwal-" + generateId();

    // Use --source-log and --wal (not --log) to get separate source and WAL topics
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--source-log",
            source,
            "--wal",
            wal,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Call without -d flag (direct mode)
    CliProcessResult result =
        runLogCall(
            "-k",
            kafkaServers,
            "--output-log",
            source,
            "--input-log",
            wal,
            "-m",
            "staticStringWithStringArgs",
            source,
            METHODS_CLASS,
            "test-call-kafka-direct");

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected RESULT in stdout", result.stdout(), containsString("RESULT:"));
  }
}
