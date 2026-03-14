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

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for {@code STUB_FROM_WAL} replay with shield-IO and stub patterns.
 *
 * <p>Validates that replay with {@code --shield-io} correctly stubs non-deterministic operations
 * (e.g., {@code System.currentTimeMillis()}, {@code Math.random()}) using WAL-recorded values, and
 * that {@code --stub} patterns selectively stub matching operations while re-executing non-matching
 * ones.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class StubFromWalReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(StubFromWalReplayIT.class);

  /** Package prefix shared by all replay test applications. */
  private static final String APP_PACKAGE = "io.quasient.foobar.apps.quantized.replay.";

  /** The number of threads for this parameterized test run. */
  private final int threadCount;

  /** Peer process launched for multi-threaded tests. */
  private PeerProcess peerProcess;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public StubFromWalReplayIT(int threadCount) {
    this.threadCount = threadCount;
  }

  /**
   * Returns the parameterized thread counts.
   *
   * @return collection of parameters: 1-thread and 2-thread scenarios
   */
  @Parameters(name = "threads={0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {1}, new Object[] {2});
  }

  /** Initializes per-test state. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Cleans up peer process after each test.
   *
   * @throws InterruptedException if interrupted while stopping the peer
   */
  @After
  public void tearDown() throws InterruptedException {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Verifies that replaying with {@code --shield-io} produces zero divergences by stubbing
   * non-deterministic I/O operations from the WAL.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithShieldIoProducesZeroDivergences() throws Exception {
    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("stub-shield-io");

      // Record WAL with TimeDependentApp (single-thread)
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "TimeDependentApp", "run");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());
      assertThat(
          "Recording output should contain TimeDep marker",
          recordResult.stdout(),
          containsString("TimeDep:"));

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay with --shield-io to stub non-deterministic operations
      CliProcessResult replayResult =
          doReplay(walSpec, List.of("--shield-io"), APP_PACKAGE + "TimeDependentApp", "run");

      logReplayResult("shield-io", replayResult);

      assertEquals("Replay with --shield-io should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    } else {
      // Multi-threaded: launch peer with RPC, issue compute calls, then replay
      String walSpec =
          recordMultiThreadWal("stub-shield-io-mt", APP_PACKAGE + "TimeDependentApp", "compute", 2);

      CliProcessResult replayResult =
          doReplay(walSpec, List.of("--shield-io"), APP_PACKAGE + "TimeDependentApp", "service");

      logReplayResult("shield-io-mt", replayResult);

      assertEquals(
          "Multi-thread replay with --shield-io should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    }
  }

  /**
   * Verifies that {@code --stub} with an Ant-style pattern stubs matching operations while
   * re-executing non-matching ones.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithStubPatternStubsMatchingOperations() throws Exception {
    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("stub-pattern");

      // Record WAL with StubPatternApp (single-thread)
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "StubPatternApp", "5", "3");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());
      assertThat(
          "Recording output should contain StubPattern marker",
          recordResult.stdout(),
          containsString("StubPattern:"));

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay: stub ExternalService, re-execute Calculator
      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--stub", APP_PACKAGE + "StubPatternApp$ExternalService.**"),
              APP_PACKAGE + "StubPatternApp",
              "5",
              "3");

      logReplayResult("stub-pattern", replayResult);

      assertEquals("Replay with --stub should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    } else {
      // Multi-threaded variant
      String walSpec =
          recordMultiThreadWal(
              "stub-pattern-mt", APP_PACKAGE + "StubPatternApp", "computeViaRpc", 2);

      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--stub", APP_PACKAGE + "StubPatternApp$ExternalService.**"),
              APP_PACKAGE + "StubPatternApp",
              "service");

      logReplayResult("stub-pattern-mt", replayResult);

      assertEquals("Multi-thread replay with --stub should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  /**
   * Creates a Chronicle WAL spec and registers it for cleanup.
   *
   * @param prefix descriptive prefix for the WAL name
   * @return the WAL spec string
   */
  private String createChronicleWalSpec(String prefix) {
    String id = generateId();
    String path = "/tmp/pal-" + prefix + "-" + id;
    trackChronicleLog(path);
    return "file:" + path;
  }

  /**
   * Records a WAL by running the peer with the given application and arguments.
   *
   * @param walSpec the WAL spec
   * @param mainClass the fully qualified main class name
   * @param appArgs application arguments
   * @return the process result
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, String mainClass, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("--wal");
    args.add(walSpec);
    args.add("--no-wal-incoming-cli");
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(mainClass);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs a replay against the given WAL spec with options.
   *
   * @param walSpec the WAL spec
   * @param replayOptions additional replay options
   * @param mainClass the fully qualified main class name
   * @param appArgs application arguments
   * @return the CLI process result
   * @throws Exception if replay fails
   */
  private CliProcessResult doReplay(
      String walSpec, List<String> replayOptions, String mainClass, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("--wal");
    args.add(walSpec);
    args.addAll(replayOptions);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(mainClass);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Records a multi-threaded WAL by starting a peer with RPC and issuing calls.
   *
   * @param prefix descriptive prefix for the WAL name
   * @param mainClass the main class
   * @param rpcMethod the RPC method to call
   * @param callCount number of RPC calls to issue
   * @return the WAL spec string
   * @throws Exception if recording fails
   */
  private String recordMultiThreadWal(
      String prefix, String mainClass, String rpcMethod, int callCount) throws Exception {
    String walSpec = createChronicleWalSpec(prefix);
    String peerName = "test-stub-" + generateId();
    UUID peerId = UUID.randomUUID();

    List<String> launchArgs = new ArrayList<>();
    launchArgs.add("-d");
    launchArgs.add(getPalDirectoryUrl());
    launchArgs.add("-n");
    launchArgs.add(peerName);
    launchArgs.add("--wal");
    launchArgs.add(walSpec);
    launchArgs.add("--zmq-rpc");
    launchArgs.add("auto");
    launchArgs.add("--rpc-threads");
    launchArgs.add("2");
    launchArgs.add("--wal-incoming-rpc");
    launchArgs.add("--no-wal-incoming-cli");
    launchArgs.add("--as-service");
    launchArgs.add("-cp");
    launchArgs.add(getIttAppsClasspath());
    launchArgs.add(mainClass);
    launchArgs.add("service");

    peerProcess = launchPeer(peerId, launchArgs.toArray(new String[0]));

    for (int i = 0; i < callCount; i++) {
      CliProcessResult result =
          runCall(
              "-d",
              getPalDirectoryUrl(),
              "-p",
              peerName,
              "--rpc-type",
              "ZMQ_RPC",
              "-m",
              rpcMethod,
              mainClass,
              String.valueOf(i + 1),
              String.valueOf(i + 2));
      logger.info("RPC call {} exit code: {}", i, result.exitCode());
    }

    runCall(
        "-d",
        getPalDirectoryUrl(),
        "-p",
        peerName,
        "--rpc-type",
        "ZMQ_RPC",
        "-m",
        "shutdown",
        mainClass);

    stopPeer(peerProcess);
    peerProcess = null;
    Thread.sleep(500);

    return walSpec;
  }

  /**
   * Logs the replay result for debugging.
   *
   * @param label a descriptive label
   * @param result the replay result
   */
  private void logReplayResult(String label, CliProcessResult result) {
    logger.info("[{}] Replay exit code: {}", label, result.exitCode());
    logger.info("[{}] Replay stdout: {}", label, result.stdout());
    logger.info("[{}] Replay stderr: {}", label, result.stderr());
  }
}
