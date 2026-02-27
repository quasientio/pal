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
 * <p>Parameterized over two dimensions: backend type ("chronicle" or "kafka") and test application
 * class. Chronicle uses {@code file:} prefix WAL paths; Kafka uses topic names with {@code -k}
 * bootstrap servers. Each test application is a deterministic, single-threaded program from the
 * {@code io.quasient.pal.apps.quantized.replay} package.
 */
@RunWith(Parameterized.class)
public class ReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(ReplayIT.class);

  /** Package prefix shared by all replay test applications. */
  private static final String APP_PACKAGE = "io.quasient.pal.apps.quantized.replay.";

  /** Configurations for all test application classes. */
  private static final TestAppConfig[] TEST_APPS = {
    new TestAppConfig(
        "MinimalReceiptCalculator",
        APP_PACKAGE + "MinimalReceiptCalculator",
        new String[] {"milk:2,bread:1,apple:5"},
        "Run 1:",
        new String[] {"milk:1"},
        new String[] {"bread:2"}),
    new TestAppConfig(
        "DataAnalyzer",
        APP_PACKAGE + "DataAnalyzer",
        new String[] {"the cat in the hat"},
        "Unique words:",
        new String[] {"hello"},
        new String[] {"goodbye world"}),
    new TestAppConfig(
        "WordStats",
        APP_PACKAGE + "WordStats",
        new String[] {"hi there"},
        "Max:",
        new String[] {"a"},
        new String[] {"zzzzz"}),
    new TestAppConfig(
        "OrderTotal",
        APP_PACKAGE + "OrderTotal",
        new String[] {"TX|A|gum=1.00|0|"},
        "total",
        new String[] {"TX|A|gum=1.00|0|"},
        new String[] {"NY|B|tea=2.10|5|50"})
  };

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /** The test application configuration for this test run. */
  private final TestAppConfig testApp;

  /**
   * Creates a parameterized test instance for the given backend and test application.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   * @param testApp the test application configuration
   */
  public ReplayIT(String backend, TestAppConfig testApp) {
    this.backend = backend;
    this.testApp = testApp;
  }

  /**
   * Returns the parameterized combinations of backend types and test application classes.
   *
   * @return collection of parameters: all combinations of {"chronicle", "kafka"} and test apps
   */
  @Parameters(name = "{0}-{1}")
  public static Collection<Object[]> data() {
    List<Object[]> params = new ArrayList<>();
    for (String backend : new String[] {"chronicle", "kafka"}) {
      for (TestAppConfig config : TEST_APPS) {
        params.add(new Object[] {backend, config});
      }
    }
    return params;
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
    args.add(testApp.mainClass);
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
    args.add(testApp.mainClass);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Records and replays the test application with identical arguments. Verifies that replay
   * produces zero divergences, exit code 0, and the expected output marker in stdout.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayZeroDivergence() throws Exception {
    String walSpec = createWalSpec("replay-zero-div");

    // Record WAL with the test application
    ProcessResult recordResult = recordWal(walSpec, testApp.recordArgs);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output",
        recordResult.stdout(),
        containsString(testApp.expectedOutputMarker));

    logger.info("Recorded output: {}", recordResult.stdout().trim());

    // Replay from the same WAL with the same arguments
    CliProcessResult replayResult = doReplay(walSpec, List.of(), testApp.recordArgs);

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay should contain expected output marker",
        replayResult.stdout(),
        containsString(testApp.expectedOutputMarker));
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

    // Record with the cross-record arguments
    ProcessResult recordResult = recordWal(walSpec, testApp.crossRecordArgs);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with different arguments
    CliProcessResult replayResult = doReplay(walSpec, List.of(), testApp.crossReplayArgs);

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

    // Record with the cross-record arguments
    ProcessResult recordResult = recordWal(walSpec, testApp.crossRecordArgs);
    assertEquals("Recording should succeed", 0, recordResult.exitCode());

    // Replay with HALT policy and different arguments
    CliProcessResult replayResult =
        doReplay(walSpec, List.of("--divergence-policy", "HALT"), testApp.crossReplayArgs);

    logger.info("HALT policy replay exit code: {}", replayResult.exitCode());
    logger.info("HALT policy replay stderr: {}", replayResult.stderr());

    assertNotEquals(
        "HALT policy should cause non-zero exit on divergence", 0, replayResult.exitCode());
  }

  /**
   * Configuration for a test application class used in replay integration tests.
   *
   * <p>Encapsulates the fully qualified main class name, arguments for recording and replaying, an
   * expected output marker for verifying correct execution, and separate record/replay arguments
   * for cross-divergence tests.
   */
  static final class TestAppConfig {

    /** Short display name for parameterized test output. */
    final String displayName;

    /** Fully qualified main class name. */
    final String mainClass;

    /** Arguments for the zero-divergence recording and replay. */
    final String[] recordArgs;

    /** Substring expected in stdout when the application runs successfully. */
    final String expectedOutputMarker;

    /** Arguments for recording in cross-divergence tests. */
    final String[] crossRecordArgs;

    /** Arguments for replaying in cross-divergence tests (different from crossRecordArgs). */
    final String[] crossReplayArgs;

    /**
     * Creates a test application configuration.
     *
     * @param displayName short name for test display
     * @param mainClass fully qualified main class name
     * @param recordArgs arguments for recording
     * @param expectedOutputMarker substring expected in stdout
     * @param crossRecordArgs arguments for cross-divergence recording
     * @param crossReplayArgs arguments for cross-divergence replaying
     */
    TestAppConfig(
        String displayName,
        String mainClass,
        String[] recordArgs,
        String expectedOutputMarker,
        String[] crossRecordArgs,
        String[] crossReplayArgs) {
      this.displayName = displayName;
      this.mainClass = mainClass;
      this.recordArgs = recordArgs;
      this.expectedOutputMarker = expectedOutputMarker;
      this.crossRecordArgs = crossRecordArgs;
      this.crossReplayArgs = crossReplayArgs;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
