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
package io.quasient.pal.recording;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests verifying that replay with the same recording scope flags as the original
 * recording produces zero divergences, and that scope mismatch is handled gracefully.
 *
 * <p>Records a WAL using {@code MinimalReceiptCalculator} with various {@code --scope} flags, then
 * replays the WAL with the same flags and verifies deterministic replay correctness. Parameterized
 * over Chronicle Queue and Kafka backends following the {@code ReplayIT} pattern.
 *
 * <p>These are end-to-end acceptance tests for the recording scope + replay compatibility, using
 * real PAL infrastructure (WAL, weaving, dispatch, replay).
 *
 * @see io.quasient.pal.core.recording.RecordingScope
 */
@RunWith(Parameterized.class)
public class RecordingScopeReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(RecordingScopeReplayIT.class);

  /** Fully qualified name of the test application used for recording and replay. */
  private static final String MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /** WAL spec created fresh for each test method. */
  private String walSpec;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public RecordingScopeReplayIT(String backend) {
    this.backend = backend;
  }

  /**
   * Returns the parameterized backend types.
   *
   * @return collection of parameters: "chronicle" and "kafka"
   */
  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {"chronicle"}, new Object[] {"kafka"});
  }

  /**
   * Creates a fresh WAL spec for each test method.
   *
   * <p>For Chronicle, returns a {@code file:}-prefixed path and registers it for cleanup. For
   * Kafka, returns a unique topic name.
   */
  @Before
  public void setUp() {
    walSpec = createWalSpec("scope-replay-");
  }

  /**
   * Creates a WAL spec appropriate for the current backend.
   *
   * <p>For Chronicle, returns a {@code file:}-prefixed path and registers it for cleanup. For
   * Kafka, returns a unique topic name (no cleanup needed; topics are ephemeral).
   *
   * @param prefix a descriptive prefix for the WAL name
   * @return the WAL spec string
   */
  private String createWalSpec(String prefix) {
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
   * Records a WAL by running the peer with scope flags and application arguments.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param scopeFlags recording scope flags to pass to {@code pal run}
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, List<String> scopeFlags, String... appArgs)
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
    args.add("--no-wal-incoming-cli");
    args.addAll(scopeFlags);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs a replay against the given WAL spec with scope flags and optional replay options.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param scopeFlags recording scope flags to pass to {@code pal replay}
   * @param extraReplayOptions additional replay options (e.g., divergence policy)
   * @param appArgs application arguments passed to the main class
   * @return the CLI process result containing exit code, stdout, and stderr
   * @throws Exception if replay fails
   */
  private CliProcessResult doReplay(
      String walSpec, List<String> scopeFlags, List<String> extraReplayOptions, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("--wal");
    args.add(walSpec);
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.addAll(scopeFlags);
    args.addAll(extraReplayOptions);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip}, then replaying with the same scope flags, produces zero divergences.
   *
   * <p>This is the fundamental correctness test: same scope on record and replay must yield a clean
   * replay with exit code 0 and no DIVERGENCE output.
   */
  @Test
  public void recordAndReplayWithSameScope() throws Exception {
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output",
        recordResult.stdout(),
        containsString("Run 1:"));

    logger.info("Recorded output: {}", recordResult.stdout().trim());

    CliProcessResult replayResult =
        doReplay(walSpec, scopeFlags, List.of(), "milk:2,bread:1,apple:5");

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Verifies that recording with {@code --scope-exclude java.lang.String.**} and replaying with the
   * same {@code --scope-exclude} flag produces zero divergences.
   *
   * <p>Exclude-based scoping must also produce a WAL that replays cleanly when the same exclude
   * rules are applied during replay.
   */
  @Test
  public void recordAndReplayWithScopeExclude() throws Exception {
    List<String> scopeFlags = List.of("--scope-exclude", "java.lang.String.**");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    logger.info("Recorded output: {}", recordResult.stdout().trim());

    CliProcessResult replayResult =
        doReplay(walSpec, scopeFlags, List.of(), "milk:2,bread:1,apple:5");

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**}, {@code --scope-io}, and
   * {@code --scope-default skip}, then replaying with the same scope flags, produces a clean
   * replay.
   *
   * <p>The {@code --scope-io} flag adds I/O boundary operations (e.g., {@code
   * System.currentTimeMillis}) to the recording scope alongside the application code scope. Replay
   * must use the same scope flags so the WAL cursor matches the expected operations.
   */
  @Test
  public void recordAndReplayWithScopeIo() throws Exception {
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-io", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    logger.info("Recorded output: {}", recordResult.stdout().trim());

    CliProcessResult replayResult =
        doReplay(walSpec, scopeFlags, List.of(), "milk:2,bread:1,apple:5");

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Verifies that replaying a scoped WAL with different application input detects a value mismatch
   * for in-scope operations.
   *
   * <p>Records with input "milk:1" and replays with input "bread:2". Since the in-scope operations
   * produce different values, replay must detect VALUE_MISMATCH and exit with code 2.
   */
  @Test
  public void replayWithDifferentInputDetectsValueMismatch() throws Exception {
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    logger.info("Recorded output: {}", recordResult.stdout().trim());

    CliProcessResult replayResult = doReplay(walSpec, scopeFlags, List.of(), "bread:2");

    logger.info("Cross-divergence replay exit code: {}", replayResult.exitCode());
    logger.info("Cross-divergence replay stderr: {}", replayResult.stderr());

    assertEquals("Replay with different args should exit with code 2", 2, replayResult.exitCode());
    assertThat(
        "Replay stderr should contain VALUE_MISMATCH",
        replayResult.stderr(),
        containsString("VALUE_MISMATCH"));
  }
}
