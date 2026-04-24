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
package io.quasient.pal.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.cli.AbstractCliIT;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that a {@code pal replay} subprocess killed with SIGTERM still emits the replay
 * divergence report on stderr via its JVM shutdown hook.
 *
 * <p>Guards the shutdown-hook path of {@code Main#printReplayDivergenceReportOnce}, which is
 * otherwise only observable when an application's main thread parks indefinitely and does not give
 * the peer a chance to print the report from the post-callJar code path.
 *
 * <p>Scenario:
 *
 * <ol>
 *   <li>Record {@link io.quasient.foobar.apps.quantized.replay.BlockingReplayApp} with arg {@code
 *       "3"} so the WAL contains {@code compute(3) == 21}. Recording exits cleanly.
 *   <li>Replay the same WAL with args {@code "5 block"}. Live {@code compute(5) == 35} diverges
 *       from the recorded value, and the app then parks forever on a latch, so the peer can only
 *       exit via SIGTERM. The sentinel argument is {@code block} rather than {@code --block} so
 *       {@code pal replay} does not consume it as one of its own options.
 *   <li>Once stdout shows the {@code "value:"} marker, the test sends SIGTERM to the JVM (bin/pal
 *       uses {@code exec java}, so the spawned {@link Process} is the JVM itself).
 *   <li>Assert the exit code is {@link AbstractCliIT#EXIT_CODE_SIGTERM} and stderr contains the
 *       divergence report with a {@code VALUE_MISMATCH} entry.
 * </ol>
 *
 * <p>Subprocess stdout and stderr are redirected to files via {@link
 * ProcessBuilder#redirectOutput(File)} / {@link ProcessBuilder#redirectError(File)} rather than
 * captured through pipes. The shutdown hook's stderr writes race the parent JVM's {@code
 * ProcessPipeInputStream} drain/close on SIGTERM, so any data the hook writes after the parent has
 * closed its read end is silently EPIPE'd. File redirection bypasses pipes entirely: the
 * subprocess's FD 2 is dup'd to the stderr file at {@code fork/exec} time, so writes land in the
 * file regardless of parent-side state.
 */
public class ReplaySigtermIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(ReplaySigtermIT.class);

  private static final String APP_CLASS =
      "io.quasient.foobar.apps.quantized.replay.BlockingReplayApp";

  /** Substring the test waits for on stdout before delivering SIGTERM. */
  private static final String VALUE_MARKER = "value:";

  /** How long to wait for the replay process to print the post-compute marker. */
  private static final int MARKER_TIMEOUT_SECONDS = 60;

  /** How long to wait for the replay process to exit after SIGTERM. */
  private static final int EXIT_TIMEOUT_SECONDS = 30;

  /** Polling interval used while waiting for {@link #VALUE_MARKER} to land in the stdout file. */
  private static final long MARKER_POLL_MILLIS = 100L;

  /**
   * Records a WAL with {@code compute(3)} then kills a divergent replay via SIGTERM and asserts the
   * shutdown hook flushed the divergence report.
   *
   * @throws Exception if subprocess execution fails
   */
  @Test
  public void sigtermDuringReplayPrintsDivergenceReport() throws Exception {
    String walPath = "/tmp/pal-replay-sigterm-" + generateId();
    String walSpec = "file:" + walPath;
    trackChronicleLog(walPath);

    ProcessResult recordResult =
        runPeer(
            "-d",
            getPalDirectoryUrl(),
            "--wal",
            walSpec,
            "--no-wal-incoming-cli",
            "-cp",
            getIttAppsClasspath(),
            APP_CLASS,
            "3");
    assertEquals(
        "Recording should exit cleanly (stderr=" + recordResult.stderr() + ")",
        0,
        recordResult.exitCode());
    assertThat(
        "Recording should print compute(3) = 21",
        recordResult.stdout(),
        containsString("value: 21"));

    ReplayProcess replay =
        launchReplay("--wal", walSpec, "-cp", getIttAppsClasspath(), APP_CLASS, "5", "block");
    int exitCode;
    String stderr;
    String stdout;
    try {
      assertTrue(
          "Replay should print the value marker before SIGTERM",
          awaitMarkerInFile(replay.stdoutFile, VALUE_MARKER, MARKER_TIMEOUT_SECONDS));

      logger.info("Sending SIGTERM to replay JVM pid={}", replay.process.pid());
      replay.process.destroy();

      assertTrue(
          "Replay should exit within " + EXIT_TIMEOUT_SECONDS + "s of SIGTERM",
          replay.process.waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    } finally {
      if (replay.process.isAlive()) {
        replay.process.destroyForcibly();
        replay.process.waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
      exitCode = replay.process.exitValue();
      stdout = readFileQuietly(replay.stdoutFile);
      stderr = readFileQuietly(replay.stderrFile);
      Files.deleteIfExists(replay.stdoutFile);
      Files.deleteIfExists(replay.stderrFile);
    }

    logger.info("Replay exit code: {}", exitCode);
    logger.info("Replay stdout:\n{}", stdout);
    logger.info("Replay stderr:\n{}", stderr);

    assertEquals("SIGTERM should yield exit code 143", EXIT_CODE_SIGTERM, exitCode);
    assertThat(
        "Shutdown hook must print the divergence report on stderr",
        stderr,
        containsString("Replay Divergence Report"));
    assertThat(
        "Divergence report should cite the compute() VALUE_MISMATCH",
        stderr,
        containsString("VALUE_MISMATCH"));
  }

  /**
   * Launches {@code pal replay} as a subprocess without waiting for it to exit, redirecting stdout
   * and stderr to per-test files so that the shutdown hook's writes survive any parent-side pipe
   * teardown triggered by {@link Process#destroy()}.
   *
   * @param args the arguments to pass after {@code pal replay}
   * @return a bundle with the live process and the paths of its stdout/stderr capture files
   * @throws IOException if the subprocess cannot be started or the capture files cannot be created
   */
  private ReplayProcess launchReplay(String... args) throws IOException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null || palHome.isEmpty()) {
      throw new IllegalStateException("PAL_HOME environment variable not set");
    }
    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal").toAbsolutePath().toString());
    command.add("replay");
    command.addAll(Arrays.asList(args));

    logger.info("Launching replay: {}", String.join(" ", command));

    Path stdoutFile = Files.createTempFile("pal-replay-sigterm-stdout-", ".log");
    Path stderrFile = Files.createTempFile("pal-replay-sigterm-stderr-", ".log");

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(palHome));
    pb.redirectOutput(stdoutFile.toFile());
    pb.redirectError(stderrFile.toFile());
    pb.environment()
        .put("PAL_CLI_LOGGING_CONFIG", Paths.get(palHome, "config", "cli-logging.xml").toString());
    pb.environment().remove("PAL_DIRECTORY");
    pb.environment().remove("PAL_KAFKA_SERVERS");
    pb.environment().remove("PAL_CHRONICLE_BASE_DIR");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    Process process = pb.start();

    return new ReplayProcess(process, stdoutFile, stderrFile);
  }

  /**
   * Polls {@code file} until it contains {@code marker} or until {@code timeoutSeconds} elapses.
   *
   * @param file the file to scan
   * @param marker the substring being sought
   * @param timeoutSeconds the maximum number of seconds to wait
   * @return {@code true} if the marker appeared in time, {@code false} otherwise
   * @throws InterruptedException if the calling thread is interrupted while sleeping
   */
  private boolean awaitMarkerInFile(Path file, String marker, int timeoutSeconds)
      throws InterruptedException {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    while (System.nanoTime() < deadlineNanos) {
      if (Files.exists(file) && readFileQuietly(file).contains(marker)) {
        return true;
      }
      Thread.sleep(MARKER_POLL_MILLIS);
    }
    return false;
  }

  /**
   * Reads {@code file} as UTF-8 and returns its contents, or the empty string if the file is
   * missing or cannot be read.
   *
   * @param file the file to read
   * @return the file contents, or {@code ""} if unreadable
   */
  private String readFileQuietly(Path file) {
    try {
      if (!Files.exists(file)) {
        return "";
      }
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      logger.warn("Could not read {}", file, e);
      return "";
    }
  }

  /** Bundle of state tied to a single {@code pal replay} subprocess. */
  private static final class ReplayProcess {
    final Process process;
    final Path stdoutFile;
    final Path stderrFile;

    ReplayProcess(Process process, Path stdoutFile, Path stderrFile) {
      this.process = process;
      this.stdoutFile = stdoutFile;
      this.stderrFile = stderrFile;
    }
  }
}
