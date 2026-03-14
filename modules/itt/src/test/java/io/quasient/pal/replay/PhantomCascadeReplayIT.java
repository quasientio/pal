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
 * Integration tests for phantom object cascade during WAL replay.
 *
 * <p>Validates that when a constructor is stubbed during replay, the resulting object is registered
 * as a "phantom" and all subsequent method calls and field accesses on that phantom object are
 * automatically stubbed from the WAL, cascading through the entire dependency tree.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class PhantomCascadeReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(PhantomCascadeReplayIT.class);

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
  public PhantomCascadeReplayIT(int threadCount) {
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
   * Verifies that stubbing a constructor causes all subsequent operations on the phantom object to
   * be auto-stubbed from the WAL.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void phantomCascadeStubsAllOperationsOnPhantomObject() throws Exception {
    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("phantom-cascade");

      // Record WAL with ObjectCreatorApp (single-thread)
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "ObjectCreatorApp", "hello");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());
      assertThat(
          "Recording output should contain ObjectCreator marker",
          recordResult.stdout(),
          containsString("ObjectCreator:"));

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay: stub the DataService constructor — all operations on the phantom cascade
      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--stub", APP_PACKAGE + "ObjectCreatorApp$DataService.**"),
              APP_PACKAGE + "ObjectCreatorApp",
              "hello");

      logReplayResult("phantom-cascade", replayResult);

      assertEquals("Replay with phantom cascade should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    } else {
      // Multi-threaded variant
      String walSpec =
          recordMultiThreadWal(
              "phantom-cascade-mt", APP_PACKAGE + "ObjectCreatorApp", "queryViaService", 2);

      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--stub", APP_PACKAGE + "ObjectCreatorApp$DataService.**"),
              APP_PACKAGE + "ObjectCreatorApp",
              "service");

      logReplayResult("phantom-cascade-mt", replayResult);

      assertEquals(
          "Multi-thread phantom cascade replay should succeed", 0, replayResult.exitCode());
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
   * @param prefix descriptive prefix
   * @return the WAL spec string
   */
  private String createChronicleWalSpec(String prefix) {
    String id = generateId();
    String path = "/tmp/pal-" + prefix + "-" + id;
    trackChronicleLog(path);
    return "file:" + path;
  }

  /**
   * Records a WAL by running the peer.
   *
   * @param walSpec the WAL spec
   * @param mainClass the main class
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
   * Runs a replay against the given WAL.
   *
   * @param walSpec the WAL spec
   * @param replayOptions additional replay options
   * @param mainClass the main class
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
   * Records a multi-threaded WAL.
   *
   * @param prefix descriptive prefix
   * @param mainClass the main class
   * @param rpcMethod the RPC method
   * @param callCount number of calls
   * @return the WAL spec
   * @throws Exception if recording fails
   */
  private String recordMultiThreadWal(
      String prefix, String mainClass, String rpcMethod, int callCount) throws Exception {
    String walSpec = createChronicleWalSpec(prefix);
    String peerName = "test-phantom-" + generateId();
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
              "input" + i);
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
