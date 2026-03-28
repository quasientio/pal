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
import static org.hamcrest.Matchers.emptyString;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for error handling in CLI commands using the new entity-operation command
 * structure.
 *
 * <p>Tests error scenarios across all CLI subcommands ({@code pal peer call}, {@code pal peer ls},
 * {@code pal peer rm}, {@code pal log print}, {@code pal log stats}) to ensure graceful failure
 * handling, appropriate error messages, and correct exit codes.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class CliErrorHandlingIT extends AbstractCliIT {

  /** The fully-qualified class name used for peer call tests. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** An unreachable etcd address used for directory-unreachable tests. */
  private static final String UNREACHABLE_DIRECTORY = "localhost:19999";

  /** A peer process handle for tests that launch peers, or null if none launched. */
  private PeerProcess peerProcess;

  /** Initializes test state before each test method. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Tears down test state after each test method, stopping any launched peer.
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

  // ==================== pal peer call Error Tests ====================

  /**
   * Tests that {@code pal peer call} fails gracefully with invalid method signature.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_invalidMethodSignature() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result =
        runPeerCall(
            "-d",
            palDir,
            "some-peer",
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "nonExistent",
            "invalid.Type");

    assertThat(
        "Expected non-zero exit code for invalid method signature", result.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal peer call} fails gracefully when peer UUID does not exist.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_nonExistentPeer() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result =
        runPeerCall("-d", palDir, UUID.randomUUID().toString(), "java.lang.System", "exit");

    assertThat("Expected non-zero exit code for non-existent peer", result.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal peer call} handles connection failures when etcd is unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_unreachableDirectory() throws Exception {
    CliProcessResult result =
        runPeerCall(
            "-d",
            UNREACHABLE_DIRECTORY,
            UUID.randomUUID().toString(),
            "java.lang.System",
            "currentTimeMillis");

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== pal peer ls Error Tests ====================

  /**
   * Tests that {@code pal peer ls} handles empty directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerLs_emptyDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runPeerLs("-d", palDir);

    assertThat("Expected exit code 0 for empty peer listing", result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal peer ls} fails gracefully when directory is unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerLs_unreachableDirectory() throws Exception {
    CliProcessResult result = runPeerLs("-d", UNREACHABLE_DIRECTORY);

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== pal log ls Error Tests ====================

  /**
   * Tests that {@code pal log ls} handles empty directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_emptyDirectory() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runLogLs("-d", palDir);

    assertThat("Expected exit code 0 for empty log listing", result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log ls} fails gracefully when directory is unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogLs_unreachableDirectory() throws Exception {
    CliProcessResult result = runLogLs("-d", UNREACHABLE_DIRECTORY);

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== pal peer rm Error Tests ====================

  /**
   * Tests that {@code pal peer rm} fails gracefully for non-existent peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerRm_nonExistentPeer() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runPeerRm("-d", palDir, UUID.randomUUID().toString());

    assertThat(
        "Expected non-zero exit code for removing non-existent peer",
        result.exitCode(),
        is(not(0)));
  }

  /**
   * Tests that {@code pal peer rm} handles unreachable directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerRm_unreachableDirectory() throws Exception {
    CliProcessResult result = runPeerRm("-d", UNREACHABLE_DIRECTORY, UUID.randomUUID().toString());

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== pal log rm Error Tests ====================

  /**
   * Tests that {@code pal log rm} fails gracefully for non-existent log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_nonExistentLog() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runLogRm("-d", palDir, "nonexistent-log-" + generateId(), "--force");

    assertThat(
        "Expected non-zero exit code for removing non-existent log", result.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal log rm} handles unreachable directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRm_unreachableDirectory() throws Exception {
    CliProcessResult result = runLogRm("-d", UNREACHABLE_DIRECTORY, "some-log", "--force");

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== pal log print Error Tests ====================

  /**
   * Tests that {@code pal log print} handles logs with minimal messages gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_minimalMessages() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "test-err-min-" + generateId();

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

    CliProcessResult result = runLogPrint("-d", palDir, walName);

    assertThat(
        "Expected exit code 0 for log print with minimal messages", result.exitCode(), is(0));
    assertThat("Expected non-empty output from log print", result.stdout(), is(not(emptyString())));
  }

  /**
   * Tests that {@code pal log print} handles offset beyond log end gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_offsetBeyondEnd() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "test-err-off-" + generateId();

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

    CliProcessResult result = runLogPrint("-d", palDir, walName, "-o", "999999");

    assertThat("Expected exit code 0 when offset is beyond end of log", result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal log print} fails gracefully for non-existent log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_nonExistentLog() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runLogPrint("-d", palDir, "nonexistent-log-" + generateId());

    assertThat("Expected non-zero exit code for non-existent log", result.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal log print} handles unreachable directory gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrint_unreachableDirectory() throws Exception {
    CliProcessResult result = runLogPrint("-d", UNREACHABLE_DIRECTORY, "some-log");

    assertThat(
        "Expected non-zero exit code for unreachable directory", result.exitCode(), is(not(0)));
  }

  // ==================== General CLI Error Tests ====================

  /**
   * Tests that CLI commands handle missing required arguments gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCli_missingRequiredArguments() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runPeerCall("-d", palDir);

    assertThat(
        "Expected non-zero exit code for missing required arguments",
        result.exitCode(),
        is(not(0)));
  }
}
