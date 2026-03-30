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
import java.io.File;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests verifying that CLI commands resolve relative Chronicle WAL paths against the
 * process working directory.
 *
 * <p>Each test records a WAL to an absolute path in {@code /tmp}, then invokes a CLI command using
 * a relative {@code file:} path with the WAL's parent directory as the process CWD.
 *
 * <p>Covers: {@code pal log print}, {@code pal wal-index}, {@code pal log index}, and {@code pal
 * log rm}.
 */
public class RelativePathChronicleCliIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(RelativePathChronicleCliIT.class);

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

  /**
   * Records a WAL to an absolute path under {@code /tmp} and returns the directory name (relative
   * component).
   *
   * @param walDirName the WAL directory name (without {@code /tmp/} prefix)
   * @return the absolute path of the created WAL
   * @throws Exception if peer launch or join fails
   */
  private String recordWal(String walDirName) throws Exception {
    String palDir = getPalDirectoryUrl();
    String absPath = "/tmp/" + walDirName;
    trackChronicleLog(absPath);

    peerProcess =
        launchPeer(
            UUID.randomUUID(),
            "-d",
            palDir,
            "--wal",
            "file:" + absPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    joinPeer(peerProcess, PROCESS_TIMEOUT_SECONDS);
    peerProcess = null;
    logger.info("Recorded WAL to: {}", absPath);
    return absPath;
  }

  // ==========================================================================
  // pal log print
  // ==========================================================================

  /**
   * Tests that {@code pal log print} resolves a relative Chronicle path against the process CWD.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void logPrint_withRelativeChronicleWalPath() throws Exception {
    String walDirName = "pal-relpath-print-" + generateId();
    recordWal(walDirName);

    File parentDir = new File("/tmp");
    CliProcessResult result = runLogPrintFromDir(parentDir, "file:" + walDirName, "--full");

    logger.info("log print exit code: {}", result.exitCode());
    logger.info("log print stdout length: {}", result.stdout().length());

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat("Expected non-empty stdout", result.stdout(), is(not("")));
  }

  // ==========================================================================
  // pal wal-index
  // ==========================================================================

  /**
   * Tests that {@code pal wal-index} resolves a relative Chronicle path against the process CWD.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndex_withRelativeChronicleWalPath() throws Exception {
    String walDirName = "pal-relpath-walidx-" + generateId();
    recordWal(walDirName);

    File parentDir = new File("/tmp");
    CliProcessResult result = runWalIndexFromDir(parentDir, "file:" + walDirName);

    logger.info("wal-index exit code: {}", result.exitCode());
    logger.info("wal-index stdout:\n{}", result.stdout());

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat(
        "Expected WAL Index Summary in output",
        result.stdout(),
        containsString("WAL Index Summary"));
    assertThat(
        "Expected non-zero entry count", result.stdout(), not(containsString("Entries:       0")));
  }

  // ==========================================================================
  // pal log index
  // ==========================================================================

  /**
   * Tests that {@code pal log index} resolves a relative Chronicle path against the process CWD.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void logIndex_withRelativeChronicleWalPath() throws Exception {
    String walDirName = "pal-relpath-logidx-" + generateId();
    recordWal(walDirName);

    File parentDir = new File("/tmp");
    CliProcessResult result = runLogIndexFromDir(parentDir, "file:" + walDirName);

    logger.info("log index exit code: {}", result.exitCode());
    logger.info("log index stdout:\n{}", result.stdout());

    assertThat("Expected exit code 0", result.exitCode(), is(0));
    assertThat(
        "Expected WAL Index Summary in output",
        result.stdout(),
        containsString("WAL Index Summary"));
    assertThat(
        "Expected non-zero entry count", result.stdout(), not(containsString("Entries:       0")));
  }

  // ==========================================================================
  // pal log rm
  // ==========================================================================

  /**
   * Tests that {@code pal log rm} resolves a relative Chronicle path against the process CWD.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void logRm_withRelativeChronicleWalPath() throws Exception {
    String walDirName = "pal-relpath-rm-" + generateId();
    String absPath = recordWal(walDirName);

    File parentDir = new File("/tmp");
    CliProcessResult rmResult = runLogRmFromDir(parentDir, "file:" + walDirName, "--force");

    logger.info("log rm exit code: {}", rmResult.exitCode());

    assertThat("Expected exit code 0", rmResult.exitCode(), is(0));

    // Verify the Chronicle directory was actually deleted
    assertThat("Chronicle directory should be removed", new File(absPath).exists(), is(false));
  }
}
