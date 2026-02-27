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
import static org.junit.Assert.assertNotEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for deterministic WAL replay.
 *
 * <p>Tests validate the complete replay pipeline: record WAL, replay the application from the
 * recorded WAL, verify execution against the WAL oracle, and report divergences.
 *
 * <p>Parameterized over backend type: "chronicle" uses {@code file:} prefix WAL paths, "kafka" uses
 * Kafka topic names with {@code -k} bootstrap servers.
 */
@RunWith(Parameterized.class)
public class ReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(ReplayIT.class);

  /** Fully qualified name of the test application used for recording and replaying. */
  private static final String MAIN_CLASS =
      "io.quasient.pal.apps.quantized.replay.MinimalReceiptCalculator";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public ReplayIT(String backend) {
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
   * Records a WAL by running the peer with the given application arguments.
   *
   * <p>Launches a peer with {@code pal run} using a WAL at the specified location. For Chronicle,
   * the WAL spec is a {@code file:}-prefixed path. For Kafka, a topic name is used with {@code -k}
   * bootstrap servers.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, String... appArgs) throws Exception {
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
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs a replay against the given WAL spec with optional replay options and application
   * arguments.
   *
   * <p>Builds the argument list with {@code --wal} and, for Kafka backend, {@code -k} bootstrap
   * servers. Replay options (such as {@code --divergence-policy HALT}) are inserted before {@code
   * -cp}.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param replayOptions additional replay options before {@code -cp} (e.g., divergence policy)
   * @param appArgs application arguments passed to the main class
   * @return the CLI process result containing exit code, stdout, and stderr
   * @throws Exception if replay fails
   */
  private CliProcessResult doReplay(String walSpec, List<String> replayOptions, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("--wal");
    args.add(walSpec);
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.addAll(replayOptions);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Records and replays MinimalReceiptCalculator with identical arguments. Verifies that replay
   * produces zero divergences, exit code 0, and the same stdout output as the recording.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayZeroDivergence() throws Exception {
    String walSpec = createWalSpec("replay-zero-div");

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordWal(walSpec, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce Run output", recordResult.stdout(), containsString("Run 1:"));

    String expectedRunLine = extractRunLine(recordResult.stdout());
    logger.info("Recorded output: {}", expectedRunLine);

    // Replay from the same WAL with the same arguments
    CliProcessResult replayResult = doReplay(walSpec, List.of(), "milk:2,bread:1,apple:5");

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay should contain same Run output",
        replayResult.stdout(),
        containsString(expectedRunLine));
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
   * Records with one set of arguments and replays with different arguments. Verifies that
   * divergences are detected with VALUE_MISMATCH and the exit code is 2.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayCrossDivergence() throws Exception {
    String walSpec = createWalSpec("replay-cross-div");

    // Record with "milk:1"
    ProcessResult recordResult = recordWal(walSpec, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with different arguments "bread:2"
    CliProcessResult replayResult = doReplay(walSpec, List.of(), "bread:2");

    logger.info("Cross-divergence replay exit code: {}", replayResult.exitCode());
    logger.info("Cross-divergence replay stderr: {}", replayResult.stderr());

    assertEquals("Replay with different args should exit with code 2", 2, replayResult.exitCode());
    assertThat(
        "Replay stderr should contain VALUE_MISMATCH",
        replayResult.stderr(),
        containsString("VALUE_MISMATCH"));
  }

  /**
   * Records with one set of arguments and replays with HALT divergence policy and different
   * arguments. Verifies that the application halts on first divergence with a non-zero exit code.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithHaltPolicy() throws Exception {
    String walSpec = createWalSpec("replay-halt");

    // Record with "milk:1"
    ProcessResult recordResult = recordWal(walSpec, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with HALT policy and different arguments
    CliProcessResult replayResult =
        doReplay(walSpec, List.of("--divergence-policy", "HALT"), "bread:2");

    logger.info("HALT policy replay exit code: {}", replayResult.exitCode());
    logger.info("HALT policy replay stderr: {}", replayResult.stderr());

    assertNotEquals(
        "HALT policy should cause non-zero exit on divergence", 0, replayResult.exitCode());
  }

  /**
   * Extracts the "Run 1: ..." line from stdout output.
   *
   * @param output the stdout output to search
   * @return the trimmed line starting with "Run 1:"
   * @throws IllegalStateException if no matching line is found
   */
  private String extractRunLine(String output) {
    return output
        .lines()
        .map(String::trim)
        .filter(line -> line.startsWith("Run 1:"))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("No 'Run 1:' line found in output:\n" + output));
  }
}
