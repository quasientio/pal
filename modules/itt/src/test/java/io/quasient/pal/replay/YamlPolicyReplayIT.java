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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
 * Integration tests for YAML-based replay policy file support.
 *
 * <p>Validates that a YAML replay policy file passed via {@code --replay-policy <path>} is parsed
 * and applied correctly during replay. The policy file specifies per-class/method replay actions
 * (RE_EXECUTE, STUB_FROM_WAL, STUB_WITH_SIDE_EFFECTS) using Ant-style patterns.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class YamlPolicyReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(YamlPolicyReplayIT.class);

  /** Package prefix shared by all replay test applications. */
  private static final String APP_PACKAGE = "io.quasient.foobar.apps.quantized.replay.";

  /** The number of threads for this parameterized test run. */
  private final int threadCount;

  /** Peer process launched for multi-threaded tests. */
  private PeerProcess peerProcess;

  /** Temporary directory for YAML policy files. */
  private Path tempDir;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public YamlPolicyReplayIT(int threadCount) {
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
  public void setUp() throws IOException {
    peerProcess = null;
    tempDir = Files.createTempDirectory("pal-yaml-policy-test");
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    logger.warn("Failed to delete temp file: {}", p, e);
                  }
                });
      }
    }
  }

  /**
   * Verifies that a YAML replay policy file is parsed and applied correctly during replay.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void yamlPolicyFileAppliedCorrectly() throws Exception {
    // Create YAML policy: stub ExternalService from WAL, re-execute Calculator
    String policyContent =
        "defaultAction: RE_EXECUTE\n"
            + "rules:\n"
            + "  - class: \""
            + APP_PACKAGE
            + "StubPatternApp$ExternalService\"\n"
            + "    method: \"**\"\n"
            + "    action: STUB_FROM_WAL\n";
    Path policyFile = tempDir.resolve("yaml-policy.yaml");
    Files.writeString(policyFile, policyContent);

    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("yaml-policy");

      // Record WAL with StubPatternApp
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "StubPatternApp", "5", "3");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());
      assertThat(
          "Recording output should contain StubPattern marker",
          recordResult.stdout(),
          containsString("StubPattern:"));

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay with YAML policy file
      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString()),
              APP_PACKAGE + "StubPatternApp",
              "5",
              "3");

      logReplayResult("yaml-policy", replayResult);

      assertEquals("Replay with YAML policy should succeed", 0, replayResult.exitCode());
      assertThat(
          "Replay stdout should contain StubPattern marker",
          replayResult.stdout(),
          containsString("StubPattern:"));
      assertThat(
          "Replay stderr should not contain DIVERGENCE",
          replayResult.stderr(),
          not(containsString("DIVERGENCE")));
    } else {
      // Multi-threaded variant
      String walSpec =
          recordMultiThreadWal(
              "yaml-policy-mt", APP_PACKAGE + "StubPatternApp", "computeViaRpc", 2);

      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString()),
              APP_PACKAGE + "StubPatternApp",
              "service");

      logReplayResult("yaml-policy-mt", replayResult);

      assertEquals("Multi-thread YAML policy replay should succeed", 0, replayResult.exitCode());
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
    String peerName = "test-yaml-" + generateId();
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
