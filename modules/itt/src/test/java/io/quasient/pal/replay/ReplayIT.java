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
import static org.junit.Assert.fail;

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
   * Records a WAL by running the peer with the given application arguments.
   *
   * <p>Launches a peer with {@code pal run} using a Chronicle WAL at the specified path. The peer
   * runs the {@link #MAIN_CLASS} application, writes all operations to the WAL, then exits.
   *
   * @param walPath absolute path for the Chronicle WAL directory
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walPath, String... appArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("--wal");
    args.add("file:" + walPath);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Records and replays MinimalReceiptCalculator with identical arguments. Verifies that replay
   * produces zero divergences, exit code 0, and the same stdout output as the recording.
   *
   * <p>For the "chronicle" backend, the full test runs end-to-end. For the "kafka" backend, the
   * test is a specification stub awaiting implementation in #854.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayZeroDivergence() throws Exception {
    if ("kafka".equals(backend)) {
      // Given: WAL recorded with Kafka backend
      //        (pal run -d ... -k kafkaServers --wal topicName -cp ... MainClass
      //        "milk:2,bread:1,apple:5")
      // When: Replay with same args
      //        (pal replay -k kafkaServers --wal topicName -cp ... MainClass
      //        "milk:2,bread:1,apple:5")
      // Then: Exit code 0, stdout contains expected receipt output,
      //       stderr does not contain DIVERGENCE or MISMATCH

      // TODO(#854): Implement Kafka backend test logic
      fail("Not yet implemented");
    }

    String walPath = "/tmp/pal-replay-zero-div-" + generateId();
    trackChronicleLog(walPath);

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordWal(walPath, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce Run output", recordResult.stdout(), containsString("Run 1:"));

    String expectedRunLine = extractRunLine(recordResult.stdout());
    logger.info("Recorded output: {}", expectedRunLine);

    // Replay from the same WAL with the same arguments
    CliProcessResult replayResult =
        runReplay(
            "--wal",
            "file:" + walPath,
            "-cp",
            getIttAppsClasspath(),
            MAIN_CLASS,
            "milk:2,bread:1,apple:5");

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
   * <p>For the "chronicle" backend, the full test runs end-to-end. For the "kafka" backend, the
   * test is a specification stub awaiting implementation in #854.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayCrossDivergence() throws Exception {
    if ("kafka".equals(backend)) {
      // Given: WAL recorded with Kafka backend with args "milk:2,bread:1,apple:5"
      // When: Replay with different args "milk:3,bread:2,apple:1"
      //        (pal replay -k kafkaServers --wal topicName -cp ... MainClass
      //        "milk:3,bread:2,apple:1")
      // Then: Exit code 2 (divergences detected), stderr contains VALUE_MISMATCH

      // TODO(#854): Implement Kafka backend test logic
      fail("Not yet implemented");
    }

    String walPath = "/tmp/pal-replay-cross-div-" + generateId();
    trackChronicleLog(walPath);

    // Record with "milk:1"
    ProcessResult recordResult = recordWal(walPath, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with different arguments "bread:2"
    CliProcessResult replayResult =
        runReplay("--wal", "file:" + walPath, "-cp", getIttAppsClasspath(), MAIN_CLASS, "bread:2");

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
   * <p>For the "chronicle" backend, the full test runs end-to-end. For the "kafka" backend, the
   * test is a specification stub awaiting implementation in #854.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithHaltPolicy() throws Exception {
    if ("kafka".equals(backend)) {
      // Given: WAL recorded with Kafka backend with args "milk:1"
      // When: Replay with HALT divergence policy and different args "bread:2"
      //        (pal replay -k kafkaServers --wal topicName --divergence-policy HALT
      //        -cp ... MainClass "bread:2")
      // Then: Exit code != 0 (application halts on first divergence)

      // TODO(#854): Implement Kafka backend test logic
      fail("Not yet implemented");
    }

    String walPath = "/tmp/pal-replay-halt-" + generateId();
    trackChronicleLog(walPath);

    // Record with "milk:1"
    ProcessResult recordResult = recordWal(walPath, "milk:1");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with HALT policy and different arguments
    CliProcessResult replayResult =
        runReplay(
            "--wal",
            "file:" + walPath,
            "--divergence-policy",
            "HALT",
            "-cp",
            getIttAppsClasspath(),
            MAIN_CLASS,
            "bread:2");

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
