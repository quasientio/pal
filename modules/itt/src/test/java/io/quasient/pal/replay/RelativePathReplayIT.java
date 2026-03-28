/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test verifying that {@code pal replay} resolves relative Chronicle WAL paths against
 * the current working directory.
 *
 * <p>Records a WAL into a temporary directory using an absolute path, then replays it using a
 * relative {@code file:} path with the WAL's parent directory as the process working directory.
 */
public class RelativePathReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(RelativePathReplayIT.class);

  /** Fully qualified main class for the test application. */
  private static final String MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";

  /** Arguments for the test application. */
  private static final String[] APP_ARGS = {"milk:2,bread:1,apple:5"};

  /** Expected output marker in stdout when the application runs successfully. */
  private static final String EXPECTED_OUTPUT = "Run 1:";

  /**
   * Records a WAL to an absolute Chronicle path, then replays using a relative {@code file:} path
   * with the WAL's parent directory as the process CWD. Verifies zero divergences.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithRelativeChronicleWalPath() throws Exception {
    // Record WAL to an absolute path inside a temp directory
    String walDirName = "pal-relative-replay-" + generateId();
    String absoluteWalPath = "/tmp/" + walDirName;
    trackChronicleLog(absoluteWalPath);

    // Record the WAL using the absolute path
    List<String> recordArgs = new ArrayList<>();
    recordArgs.add("-d");
    recordArgs.add(getPalDirectoryUrl());
    recordArgs.add("--wal");
    recordArgs.add("file:" + absoluteWalPath);
    recordArgs.add("--no-wal-incoming-cli");
    recordArgs.add("-cp");
    recordArgs.add(getIttAppsClasspath());
    recordArgs.add(MAIN_CLASS);
    Collections.addAll(recordArgs, APP_ARGS);
    ProcessResult recordResult = runPeer(recordArgs.toArray(new String[0]));
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output",
        recordResult.stdout(),
        containsString(EXPECTED_OUTPUT));

    logger.info("Recorded WAL to: {}", absoluteWalPath);

    // Replay using a RELATIVE path — the WAL directory name only, from /tmp as CWD
    File parentDir = new File("/tmp");
    List<String> replayArgs = new ArrayList<>();
    replayArgs.add("--wal");
    replayArgs.add("file:" + walDirName); // relative path!
    replayArgs.add("-cp");
    replayArgs.add(getIttAppsClasspath());
    replayArgs.add(MAIN_CLASS);
    Collections.addAll(replayArgs, APP_ARGS);

    CliProcessResult replayResult = runReplayFromDir(parentDir, replayArgs.toArray(new String[0]));

    logger.info("Relative-path replay exit code: {}", replayResult.exitCode());
    logger.info("Relative-path replay stdout: {}", replayResult.stdout());
    logger.info("Relative-path replay stderr: {}", replayResult.stderr());

    assertEquals(
        "Replay with relative path should succeed with zero divergences",
        0,
        replayResult.exitCode());
    assertThat(
        "Replay should contain expected output marker",
        replayResult.stdout(),
        containsString(EXPECTED_OUTPUT));
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
  }
}
